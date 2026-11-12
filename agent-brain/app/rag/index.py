"""Lazy, idempotent RAG indexing (contract §5).

On first use per document the agent-brain indexes:
  * the seeded jobs (via Java ``GET /jobs?size=50``), and
  * the user's latest READY resume (via ``GET /resumes/me`` +
    ``GET /resumes/{id}/text``).
A module-level ``_INDEXED`` set makes re-indexing within one process a
no-op per docId; ``POST /rag/index`` is the explicit endpoint for the same
operation and always re-indexes (replace semantics live in the store).
No other sources are indexed in W3.
"""

from __future__ import annotations

import logging

from app.java_client import JavaClient, JavaClientError
from app.llm import OllamaClient
from app.rag.chunking import chunk_document
from app.rag.store import RagStore

logger = logging.getLogger(__name__)

_INDEXED: set[str] = set()


def reset_indexed_cache() -> None:
    """Test helper: forget which documents were already indexed."""
    _INDEXED.clear()


def job_text(job: dict[str, object]) -> str:
    """Plain-text rendering of one job for indexing."""
    skills = job.get("skills") or []
    skill_text = ", ".join(str(s) for s in skills) if isinstance(skills, list) else str(skills)
    parts = [
        f"Title: {job.get('title', '')}",
        f"Company: {job.get('company', '')}",
        f"Location: {job.get('location', '')}",
        f"Work type: {job.get('workType', '')}",
        f"Sponsors H-1B: {job.get('sponsorsH1b', '')}",
        f"Skills: {skill_text}",
        f"Summary: {job.get('summary', '')}",
        f"Role: {job.get('role', '')}",
    ]
    return "\n".join(parts)


async def index_document(
    store: RagStore,
    llm: OllamaClient,
    owner_type: str,
    owner_id: str,
    text: str,
    metadata: dict[str, object] | None = None,
) -> int:
    """Chunk + embed + store one document. Returns the chunk count.

    Returns 0 (without raising) when Ollama embeddings are unavailable —
    indexing is best-effort; the chat path still answers from tools/rules.
    """
    chunks = chunk_document(text)
    if not chunks:
        return 0
    embeddings = await llm.embed([c.text for c in chunks])
    if embeddings is None:
        logger.warning("Skipping index of %s:%s — Ollama embeddings down", owner_type, owner_id)
        return 0
    count = store.replace_document(
        owner_type, owner_id, [(c.index, c.text) for c in chunks], embeddings, metadata
    )
    _INDEXED.add(f"{owner_type}:{owner_id}")
    return count


async def index_jobs_and_resume(java: JavaClient, store: RagStore, llm: OllamaClient) -> dict[str, int]:
    """Index seeded jobs + latest READY resume, skipping already-indexed docs.

    Returns ``{"jobs": <chunks>, "resume": <chunks>}``. Any single document
    failure is logged and skipped — indexing must never break a chat turn.
    """
    totals = {"jobs": 0, "resume": 0}
    try:
        jobs = await java.list_jobs(size=50)
    except Exception as exc:  # noqa: BLE001 - indexing is best-effort
        logger.warning("Lazy job indexing skipped: %s", exc)
        jobs = []
    for job in jobs:
        job_id = str(job.get("id", ""))
        if not job_id or f"job:{job_id}" in _INDEXED:
            continue
        try:
            totals["jobs"] += await index_document(
                store, llm, "job", job_id, job_text(job), {"title": str(job.get("title", ""))}
            )
        except Exception as exc:  # noqa: BLE001
            logger.warning("Indexing job %s failed: %s", job_id, exc)

    try:
        resumes = await java.list_resumes()
    except Exception as exc:  # noqa: BLE001
        logger.warning("Lazy resume indexing skipped: %s", exc)
        resumes = []
    ready = [r for r in resumes if str(r.get("status", "")).upper() == "READY"]
    candidates = ready or resumes
    if candidates:
        resume = candidates[0]
        resume_id = str(resume.get("id", ""))
        if resume_id and f"resume:{resume_id}" not in _INDEXED:
            try:
                text_doc = await java.get_resume_text(resume_id)
                text = str(text_doc.get("text", "") or "")
                if text.strip():
                    totals["resume"] += await index_document(
                        store, llm, "resume", resume_id, text, {}
                    )
            except JavaClientError as exc:
                # 400 RESUME_NOT_READY / 404: nothing to index yet.
                logger.info("Resume %s not indexable: %s", resume_id, exc)
            except Exception as exc:  # noqa: BLE001
                logger.warning("Indexing resume %s failed: %s", resume_id, exc)
    return totals
