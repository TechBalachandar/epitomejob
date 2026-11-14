# Phase 5B (MCP split): DELIBERATE DUPLICATE of agent-brain's app/rag/store.py.
# agent-brain still needs the store for /rag/index, /rag/query and /chat; ask_docs needs query_hybrid only (replace_document is unused here but kept verbatim to avoid divergence).
# Kept verbatim so behaviour is identical on both sides of the split.
# There is intentionally no shared package: fix a bug here AND in
# agent-brain's copy until a shared-helper decision is revisited.

"""RAG storage: hybrid vector + lexical retrieval fused with RRF (contract §5).

Table (owned by Java Flyway V4, Python owns reads/writes with raw SQL):
``rag.documents`` — one row per chunk, ``embedding VECTOR(768)``,
``content_tsv`` generated tsvector, unique (owner_type, owner_id, chunk_index).

Retrieval: two ranked lists, fused with reciprocal rank fusion, k=60:
  (a) vector: ORDER BY embedding <=> $1::vector LIMIT 2*topK (cosine distance)
  (b) lexical: WHERE content_tsv @@ plainto_tsquery('english', $1)
      ORDER BY ts_rank(...) DESC LIMIT 2*topK
fused score for a chunk = sum(1 / (60 + rank)) over the lists it appears in
(rank starts at 1). Scores are rounded to 4 decimals at the response layer.
"""

from __future__ import annotations

import logging
import uuid
from dataclasses import dataclass

import psycopg

logger = logging.getLogger(__name__)

RRF_K = 60
VECTOR_DIM = 768


@dataclass(frozen=True)
class RagChunk:
    doc_id: str  # "<owner_type>:<owner_id>"
    text: str
    score: float  # RRF fused score, rounded to 4 decimals
    source: str  # "job" or "resume"


def format_embedding(vector: list[float]) -> str:
    """Render a vector as a pgvector literal, e.g. "[0.1,0.2,...]"."""
    if len(vector) != VECTOR_DIM:
        raise ValueError(f"embedding must be {VECTOR_DIM} dims, got {len(vector)}")
    return "[" + ",".join(repr(float(v)) for v in vector) + "]"


def fuse_rrf(ranked_lists: list[list[str]], k: int = RRF_K) -> list[tuple[str, float]]:
    """Reciprocal rank fusion over ranked lists of chunk keys.

    Each list is ordered best-first; rank starts at 1. Returns
    ``(key, fused_score)`` sorted by score desc, then key asc for ties.
    Pure function — unit-tested without any database.
    """
    scores: dict[str, float] = {}
    for ranked in ranked_lists:
        for rank, key in enumerate(ranked, start=1):
            scores[key] = scores.get(key, 0.0) + 1.0 / (k + rank)
    return sorted(scores.items(), key=lambda kv: (-kv[1], kv[0]))


class RagStore:
    """Raw-SQL access to ``rag.documents`` via psycopg (sync).

    ``connection_factory`` is injectable for tests (a callable returning a
    connection-like object with ``cursor()`` / ``commit()``). By default it
    opens a real psycopg connection to ``database_url`` per operation.
    """

    def __init__(
        self,
        database_url: str,
        connection_factory: object | None = None,  # callable[[], connection] in practice
    ) -> None:
        self.database_url = database_url
        self._connection_factory = connection_factory

    def _connect(self) -> object:
        if self._connection_factory is not None:
            factory = self._connection_factory  # type: ignore[operator]
            return factory()
        return psycopg.connect(self.database_url)

    def replace_document(
        self,
        owner_type: str,
        owner_id: str,
        chunks: list[tuple[int, str]],
        embeddings: list[list[float]],
        metadata: dict[str, object] | None = None,
    ) -> int:
        """Delete-then-insert one document's chunks in a single transaction.

        Returns the number of chunks stored. Re-indexing the same docId
        REPLACES its chunks (contract §2, POST /rag/index).
        """
        if len(chunks) != len(embeddings):
            raise ValueError("chunks and embeddings must have the same length")
        conn = self._connect()
        try:
            with conn.cursor() as cur:
                cur.execute(
                    "DELETE FROM rag.documents WHERE owner_type = %s AND owner_id = %s",
                    (owner_type, owner_id),
                )
                for (chunk_index, text), embedding in zip(chunks, embeddings):
                    cur.execute(
                        """
                        INSERT INTO rag.documents
                            (id, owner_type, owner_id, source, chunk_index,
                             content, embedding, metadata)
                        VALUES (%s, %s, %s, %s, %s, %s, %s::vector, %s::jsonb)
                        """,
                        (
                            str(uuid.uuid4()),
                            owner_type,
                            owner_id,
                            owner_type,  # source = owner_type in W3
                            chunk_index,
                            text,
                            format_embedding(embedding),
                            _json(metadata or {}),
                        ),
                    )
            conn.commit()
        finally:
            _close(conn)
        return len(chunks)

    def query_hybrid(
        self, query_text: str, query_embedding: list[float], top_k: int = 5
    ) -> list[RagChunk]:
        """Hybrid retrieval: vector list + lexical list, fused with RRF (§5)."""
        limit = 2 * top_k
        conn = self._connect()
        try:
            with conn.cursor() as cur:
                cur.execute(
                    """
                    SELECT owner_type, owner_id, chunk_index, content
                    FROM rag.documents
                    ORDER BY embedding <=> %s::vector
                    LIMIT %s
                    """,
                    (format_embedding(query_embedding), limit),
                )
                vector_rows = cur.fetchall()
                cur.execute(
                    """
                    SELECT owner_type, owner_id, chunk_index, content
                    FROM rag.documents
                    WHERE content_tsv @@ plainto_tsquery('english', %s)
                    ORDER BY ts_rank(content_tsv, plainto_tsquery('english', %s)) DESC
                    LIMIT %s
                    """,
                    (query_text, query_text, limit),
                )
                lexical_rows = cur.fetchall()
        finally:
            _close(conn)

        def key(row: tuple[object, ...]) -> str:
            return f"{row[0]}:{row[1]}#{row[2]}"

        by_key: dict[str, str] = {}
        for row in vector_rows + lexical_rows:
            by_key[key(row)] = str(row[3])
        fused = fuse_rrf(
            [[key(r) for r in vector_rows], [key(r) for r in lexical_rows]], k=RRF_K
        )
        out: list[RagChunk] = []
        for chunk_key, score in fused[:top_k]:
            owner_type, rest = chunk_key.split(":", 1)
            out.append(
                RagChunk(
                    doc_id=f"{owner_type}:{rest.split('#', 1)[0]}",
                    text=by_key[chunk_key],
                    score=round(score, 4),
                    source=owner_type,
                )
            )
        return out


def _json(value: dict[str, object]) -> str:
    import json

    return json.dumps(value)


def _close(conn: object) -> None:
    close = getattr(conn, "close", None)
    if callable(close):
        try:
            close()
        except Exception:  # noqa: BLE001 - close must never mask the real result
            logger.debug("closing RAG connection failed", exc_info=True)
