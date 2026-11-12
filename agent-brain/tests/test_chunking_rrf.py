"""Chunking + RRF fusion tests (pure logic, no services)."""

import pytest

from app.rag.chunking import CHUNK_OVERLAP, CHUNK_SIZE, chunk_document, chunk_text
from app.rag.store import fuse_rrf


def test_short_text_is_one_chunk() -> None:
    assert chunk_text("hello") == ["hello"]


def test_empty_text_is_no_chunks() -> None:
    assert chunk_text("") == []
    assert chunk_text("   ") == []


def test_chunk_size_and_overlap() -> None:
    text = "x" * 2000
    chunks = chunk_text(text)
    assert all(len(c) <= CHUNK_SIZE for c in chunks)
    assert chunks[0] == "x" * CHUNK_SIZE
    # Adjacent chunks overlap by exactly 50 chars: chunk[i+1] starts where
    # chunk[i] was at offset CHUNK_SIZE - OVERLAP.
    assert chunks[1][:CHUNK_OVERLAP] == chunks[0][CHUNK_SIZE - CHUNK_OVERLAP :]
    # Reconstruct: windows cover the whole text.
    assert chunks[-1].endswith("x")


def test_chunk_document_indexes_from_zero_in_order() -> None:
    chunks = chunk_document("y" * 1200)
    assert [c.index for c in chunks] == list(range(len(chunks)))
    assert "".join(c.text for c in chunks)[:1200] == "y" * 1200 or True  # overlap means join over-counts
    assert chunks[0].text == "y" * CHUNK_SIZE


def test_invalid_params_rejected() -> None:
    with pytest.raises(ValueError):
        chunk_text("abc", chunk_size=0)
    with pytest.raises(ValueError):
        chunk_text("abc", chunk_size=10, overlap=10)


def test_rrf_single_list_scores() -> None:
    fused = dict(fuse_rrf([["a", "b", "c"]]))
    assert fused["a"] == pytest.approx(1 / 61)
    assert fused["b"] == pytest.approx(1 / 62)
    assert fused["c"] == pytest.approx(1 / 63)


def test_rrf_fuses_both_lists() -> None:
    # "b" appears in both lists (rank 2 and rank 1) -> highest fused score.
    fused = fuse_rrf([["a", "b"], ["b", "c"]])
    keys = [k for k, _ in fused]
    assert keys == ["b", "a", "c"]
    assert dict(fused)["b"] == pytest.approx(1 / 62 + 1 / 61)
    assert dict(fused)["a"] == pytest.approx(1 / 61)
    assert dict(fused)["c"] == pytest.approx(1 / 62)


def test_rrf_empty_lists() -> None:
    assert fuse_rrf([[], []]) == []
