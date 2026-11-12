"""RAG chunking (contract §5): 512 chars per chunk, 50 chars overlap,
plain-text char windows, chunk_index starts at 0 per document, in order.
"""

from __future__ import annotations

from dataclasses import dataclass

CHUNK_SIZE = 512
CHUNK_OVERLAP = 50


@dataclass(frozen=True)
class Chunk:
    index: int
    text: str


def chunk_text(text: str, chunk_size: int = CHUNK_SIZE, overlap: int = CHUNK_OVERLAP) -> list[str]:
    """Split ``text`` into char windows of ``chunk_size`` with ``overlap``.

    The last window is kept even when short; a text shorter than
    ``chunk_size`` yields exactly one chunk. Empty/blank text yields [].
    """
    if chunk_size <= 0 or overlap < 0 or overlap >= chunk_size:
        raise ValueError("require 0 <= overlap < chunk_size and chunk_size > 0")
    if not text or not text.strip():
        return []
    chunks: list[str] = []
    step = chunk_size - overlap
    start = 0
    while start < len(text):
        chunks.append(text[start : start + chunk_size])
        if start + chunk_size >= len(text):
            break
        start += step
    return chunks


def chunk_document(text: str) -> list[Chunk]:
    """Chunk a document, attaching the per-document chunk index."""
    return [Chunk(index=i, text=c) for i, c in enumerate(chunk_text(text))]
