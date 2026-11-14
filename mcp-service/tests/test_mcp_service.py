"""mcp-service smoke tests (Phase 5B).

No Java, Ollama or Postgres needed:
- the 7 tools are registered on the FastMCP object with exactly the
  contract names;
- /sse rejects requests without a Bearer token (401) and never reaches
  the tools unauthenticated.
A live check (valid token -> SSE stream, tool calls against Java) is a
Mac/compose job, not a unit test.
"""

from __future__ import annotations

import httpx
import pytest

from app.mcp import server as server_mod
from app.mcp import tools as mcp_tools

EXPECTED_TOOLS = {
    "search_jobs",
    "parse_resume",
    "match_score",
    "ask_docs",
    "update_profile",
    "draft_application",
    "apply_to_job",
}


async def test_all_seven_tools_registered() -> None:
    tools = await server_mod.mcp.list_tools()
    assert {t.name for t in tools} == EXPECTED_TOOLS


def test_tool_functions_importable_from_mcp_tools() -> None:
    for name in EXPECTED_TOOLS:
        assert callable(getattr(mcp_tools, name)), name


def test_token_cache_ttl_is_15_minutes() -> None:
    assert server_mod.TOKEN_CACHE_TTL_S == 900.0


async def test_sse_rejects_missing_bearer_token() -> None:
    app = server_mod.create_app()
    transport = httpx.ASGITransport(app=app)
    async with httpx.AsyncClient(transport=transport, base_url="http://test") as client:
        resp = await client.get("/sse")
        assert resp.status_code == 401
        assert resp.json() == {"error": "missing Bearer token"}
        resp = await client.get("/sse", headers={"Authorization": "Bearer "})
        assert resp.status_code == 401


async def test_sse_rejects_empty_bearer_and_non_sse_passes_through() -> None:
    # A request with no Authorization header at all, on a non-/sse path,
    # must NOT get the 401 (middleware only guards /sse).
    app = server_mod.create_app()
    transport = httpx.ASGITransport(app=app)
    async with httpx.AsyncClient(transport=transport, base_url="http://test") as client:
        resp = await client.get("/")
        assert resp.status_code != 401
