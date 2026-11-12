"""RagStore tests with a fake psycopg connection (no Postgres)."""

import pytest

from app.rag.store import RagStore, format_embedding
from tests.fakes import JOB_ID, RESUME_ID, FakeConnection


def test_format_embedding_requires_768_dims() -> None:
    assert format_embedding([0.0] * 768) == "[" + ",".join(["0.0"] * 768) + "]"
    with pytest.raises(ValueError):
        format_embedding([0.0] * 3)


def test_replace_document_deletes_then_inserts_in_one_transaction() -> None:
    conn = FakeConnection()
    store = RagStore("postgresql://unused", connection_factory=lambda: conn)
    count = store.replace_document(
        "job", JOB_ID, [(0, "chunk A"), (1, "chunk B")], [[0.01] * 768, [0.02] * 768], {}
    )
    assert count == 2
    assert conn.committed is True
    assert conn.closed is True
    sqls = [s for s, _ in conn.cursor_obj.executed]
    assert sqls[0].startswith("DELETE FROM rag.documents")
    assert sqls[1].startswith("INSERT INTO rag.documents")
    assert sqls[2].startswith("INSERT INTO rag.documents")


def test_replace_document_rejects_mismatched_embeddings() -> None:
    store = RagStore("postgresql://unused", connection_factory=FakeConnection)
    with pytest.raises(ValueError):
        store.replace_document("job", JOB_ID, [(0, "a")], [], {})


def test_query_hybrid_fuses_vector_and_lexical_with_rrf() -> None:
    conn = FakeConnection()
    store = RagStore("postgresql://unused", connection_factory=lambda: conn)
    chunks = store.query_hybrid("java developer", [0.01] * 768, top_k=5)
    # Vector list: [job#0, resume#0]; lexical list: [resume#0].
    # resume#0 fused = 1/62 + 1/61 (highest); job#0 = 1/61.
    assert [c.doc_id for c in chunks] == [f"resume:{RESUME_ID}", f"job:{JOB_ID}"]
    assert chunks[0].score == round(1 / 62 + 1 / 61, 4)
    assert chunks[1].score == round(1 / 61, 4)
    assert chunks[0].source == "resume"
    sqls = [s for s, _ in conn.cursor_obj.executed]
    assert any("embedding <=>" in s for s in sqls)
    assert any("plainto_tsquery" in s for s in sqls)
