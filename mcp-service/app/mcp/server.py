# Phase 5B (MCP split): MOVED verbatim from agent-brain's app/mcp/server.py.
# Only the "Run from" docstring line changed (agent-brain -> mcp-service).
# Same FastMCP SSE server on :8001, path /sse, same Bearer-auth middleware
# with the same 15-minute token cache (TOKEN_CACHE_TTL_S = 900.0).

"""FastMCP SSE server (contract §4): exposes the 7 tools on :8001, path /sse.

Auth: the server validates the ``Authorization: Bearer`` header once per
connection via Java ``GET /profiles/me`` (validated tokens are cached for
15 minutes, so a reconnect re-validates). Without a valid token the
connection is rejected with 401 — Java-backed tools never run unauthenticated
and never crash on a bad token. The validated token/userId are carried to
the tools via contextvars; the tools themselves live in ``app/mcp/tools.py``
and are shared with the in-process /chat path.

Run from ``mcp-service/``:
    python -m app.mcp.server
Point the MCP Inspector at http://localhost:8001/sse with an
``Authorization: Bearer <token>`` header (Inspector -> Configuration ->
Request Headers).
"""

from __future__ import annotations

import contextvars
import logging
import time

import httpx
import uvicorn
from fastmcp import FastMCP

from app.auth import InvalidTokenError, JavaUnavailableError, validate_token
from app.config import settings
from app.java_client import JavaClient
from app.llm import OllamaClient
from app.mcp import tools as mcp_tools
from app.mcp.tools import ToolContext
from app.rag.store import RagStore

logger = logging.getLogger(__name__)

TOKEN_CACHE_TTL_S = 900.0

_token_var: contextvars.ContextVar[str] = contextvars.ContextVar("mcp_token", default="")
_user_var: contextvars.ContextVar[str] = contextvars.ContextVar("mcp_user_id", default="")

# token -> (user_id, validated_at)
_TOKEN_CACHE: dict[str, tuple[str, float]] = {}


async def _validate_cached(token: str) -> str:
    cached = _TOKEN_CACHE.get(token)
    if cached and time.monotonic() - cached[1] < TOKEN_CACHE_TTL_S:
        return cached[0]
    profile = await validate_token(token, settings.java_base_url)
    user_id = str(profile.get("userId", ""))
    if not user_id:
        raise InvalidTokenError("profile has no userId")
    _TOKEN_CACHE[token] = (user_id, time.monotonic())
    return user_id


class BearerAuthMiddleware:
    """Pure ASGI middleware: reject /sse requests without a valid Bearer token.

    Pure ASGI (not BaseHTTPMiddleware) so SSE streaming passes through
    untouched. Only paths under /sse are checked.
    """

    def __init__(self, app: object) -> None:
        self.app = app

    async def __call__(self, scope: dict, receive: object, send: object) -> None:  # type: ignore[no-untyped-def]
        if scope.get("type") != "http" or not str(scope.get("path", "")).startswith("/sse"):
            await self.app(scope, receive, send)  # type: ignore[operator]
            return
        headers = {k.lower(): v for k, v in scope.get("headers", [])}
        auth = headers.get(b"authorization", b"").decode("utf-8", "ignore")
        if not auth.startswith("Bearer ") or not auth[len("Bearer ") :].strip():
            await _send_json(send, 401, {"error": "missing Bearer token"})  # type: ignore[arg-type]
            return
        token = auth[len("Bearer ") :].strip()
        try:
            user_id = await _validate_cached(token)
        except InvalidTokenError:
            await _send_json(send, 401, {"error": "invalid token"})  # type: ignore[arg-type]
            return
        except JavaUnavailableError:
            await _send_json(send, 502, {"error": "Java unavailable for token validation"})  # type: ignore[arg-type]
            return
        reset_token = _token_var.set(token)
        reset_user = _user_var.set(user_id)
        try:
            await self.app(scope, receive, send)  # type: ignore[operator]
        finally:
            _token_var.reset(reset_token)
            _user_var.reset(reset_user)


async def _send_json(send: object, status: int, payload: dict[str, str]) -> None:
    import json

    body = json.dumps(payload).encode()
    await send(  # type: ignore[operator]
        {"type": "http.response.start", "status": status, "headers": [(b"content-type", b"application/json")]}
    )
    await send({"type": "http.response.body", "body": body})  # type: ignore[operator]


def _tool_context() -> ToolContext:
    token = _token_var.get()
    user_id = _user_var.get()
    if not token or not user_id:
        raise RuntimeError("no validated Bearer token on this MCP connection")
    return ToolContext(
        java=JavaClient(settings.java_base_url, token),
        store=RagStore(settings.database_url),
        llm=OllamaClient(
            base_url=settings.ollama_base_url,
            chat_model=settings.ollama_chat_model,
            embed_model=settings.ollama_embed_model,
            timeout_s=settings.ollama_timeout_s,
        ),
        token=token,
        user_id=user_id,
    )


mcp = FastMCP("epitomejob-coach")


@mcp.tool
async def search_jobs(
    query: str | None = None,
    location: str | None = None,
    workType: str | None = None,
    sponsorsH1b: bool | None = None,
    limit: int = 10,
) -> list[dict[str, object]]:
    """Search jobs via the Java backend (GET /jobs). limit default 10, max 20."""
    return await mcp_tools.search_jobs(
        _tool_context(), query=query, location=location, work_type=workType,
        sponsors_h1b=sponsorsH1b, limit=limit,
    )


@mcp.tool
async def parse_resume(resumeId: str) -> dict[str, object]:
    """Read a resume's parsed text (first 4000 chars) via Java GET /resumes/{id}/text."""
    return await mcp_tools.parse_resume(_tool_context(), resumeId)


@mcp.tool
async def match_score(jobId: str) -> dict[str, object]:
    """Get the match score, reasons and gaps for a job via Java GET /matches/{jobId}."""
    return await mcp_tools.match_score(_tool_context(), jobId)


@mcp.tool
async def ask_docs(query: str, topK: int = 5) -> dict[str, object]:
    """Ask the RAG index (hybrid vector + lexical, RRF). Returns matching chunks."""
    return await mcp_tools.ask_docs(_tool_context(), query, top_k=topK)


@mcp.tool
async def update_profile(fields: dict[str, object]) -> dict[str, object]:
    """Propose a profile update as a Java approval (UPDATE_PROFILE). Never writes directly."""
    return await mcp_tools.update_profile(_tool_context(), fields)


@mcp.tool
async def draft_application(jobId: str, resumeId: str | None = None) -> dict[str, object]:
    """Draft an application grounded in resume + job + profile, proposed as a Java approval (DRAFT_APPLICATION)."""
    return await mcp_tools.draft_application(_tool_context(), jobId, resumeId)


@mcp.tool
async def apply_to_job(jobId: str, resumeId: str | None = None) -> dict[str, object]:
    """Propose a job application as a Java approval (APPLY_JOB). Never applies directly."""
    return await mcp_tools.apply_to_job(_tool_context(), jobId, resumeId)


def create_app():  # type: ignore[no-untyped-def]
    """Starlette app serving FastMCP over SSE at /sse, behind Bearer auth."""
    inner = mcp.http_app(path="/sse", transport="sse")
    return BearerAuthMiddleware(inner)


app = create_app()


def main() -> None:
    logger.info("FastMCP SSE server on :%s/sse", settings.mcp_port)
    uvicorn.run(app, host="0.0.0.0", port=settings.mcp_port)


if __name__ == "__main__":
    main()
