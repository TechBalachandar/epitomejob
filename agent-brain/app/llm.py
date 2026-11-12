"""Ollama client (chat + embeddings) via direct HTTP calls.

Design choice (documented): direct ``httpx`` calls to Ollama's
``/api/chat`` and ``/api/embeddings`` endpoints, NOT langchain-ollama.
Reason: the agent-brain only needs two Ollama calls, and a thin client
keeps the deterministic fallback explicit — every method returns ``None``
on any Ollama failure instead of raising, so callers can fall back to
rules. No cloud LLM is ever used; Ollama is local-only (:11434).
"""

from __future__ import annotations

import logging

import httpx

logger = logging.getLogger(__name__)

EMBED_BATCH_SIZE = 10  # contract §5: embeddings batched 10 chunks per request


class OllamaClient:
    """Minimal async client for a local Ollama server."""

    def __init__(
        self,
        base_url: str = "http://localhost:11434",
        chat_model: str = "llama3.1:8b",
        embed_model: str = "nomic-embed-text",
        timeout_s: float = 120.0,
        client: httpx.AsyncClient | None = None,
    ) -> None:
        self.base_url = base_url.rstrip("/")
        self.chat_model = chat_model
        self.embed_model = embed_model
        self.timeout_s = timeout_s
        self._client = client

    def _get_client(self) -> httpx.AsyncClient:
        if self._client is None:
            # trust_env=False: localhost services must never go through a
            # proxy, and a stray proxy env var must not break the client.
            self._client = httpx.AsyncClient(
                base_url=self.base_url, timeout=self.timeout_s, trust_env=False
            )
        return self._client

    async def probe(self) -> bool:
        """Cheap liveness probe: GET /api/tags with a 2s timeout."""
        try:
            client = self._get_client()
            resp = await client.get("/api/tags", timeout=2.0)
            return resp.status_code == 200
        except Exception as exc:  # noqa: BLE001 - any failure means "down"
            logger.debug("Ollama probe failed: %s", exc)
            return False

    async def chat(self, messages: list[dict[str, str]]) -> str | None:
        """Chat completion. Returns the reply text, or None if Ollama is down."""
        try:
            client = self._get_client()
            resp = await client.post(
                "/api/chat",
                json={"model": self.chat_model, "messages": messages, "stream": False},
                timeout=self.timeout_s,
            )
            if resp.status_code != 200:
                logger.warning("Ollama /api/chat returned %s", resp.status_code)
                return None
            content = resp.json().get("message", {}).get("content")
            return content.strip() if isinstance(content, str) and content.strip() else None
        except Exception as exc:  # noqa: BLE001 - any failure means "down"
            logger.warning("Ollama chat failed: %s", exc)
            return None

    async def embed(self, texts: list[str]) -> list[list[float]] | None:
        """Embed texts (768-dim, nomic-embed-text), 10 per request.

        Returns embeddings in input order, or None if Ollama is down.
        """
        if not texts:
            return []
        try:
            client = self._get_client()
            out: list[list[float]] = []
            for i in range(0, len(texts), EMBED_BATCH_SIZE):
                for text in texts[i : i + EMBED_BATCH_SIZE]:
                    resp = await client.post(
                        "/api/embeddings",
                        json={"model": self.embed_model, "prompt": text},
                        timeout=self.timeout_s,
                    )
                    if resp.status_code != 200:
                        logger.warning("Ollama /api/embeddings returned %s", resp.status_code)
                        return None
                    vec = resp.json().get("embedding")
                    if not isinstance(vec, list) or not vec:
                        return None
                    out.append([float(v) for v in vec])
            return out
        except Exception as exc:  # noqa: BLE001 - any failure means "down"
            logger.warning("Ollama embed failed: %s", exc)
            return None

    async def aclose(self) -> None:
        if self._client is not None:
            await self._client.aclose()
            self._client = None
