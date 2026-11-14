"""Agent-brain FastAPI app (contract §2). Port 8000.

Endpoints: GET /health, POST /chat, POST /rag/index, POST /rag/query.
Every endpoint requires ``Authorization: Bearer <user token>``; the token
is validated via Java GET /profiles/me and the userId comes from that
validated profile (a body userId that differs returns 401).
/chat uses Temporal when reachable, in-process fallback otherwise (§7).
"""

from __future__ import annotations

import asyncio
import json
import logging
import uuid

import httpx
from fastapi import FastAPI, Header, HTTPException, Request
from fastapi.responses import JSONResponse
from pydantic import BaseModel, Field

from app.auth import InvalidTokenError, JavaUnavailableError, validate_token
from app.config import settings
from app.graph.supervisor import CoachDeps, run_coach
from app.java_client import JavaClient
from app.llm import OllamaClient
from app.rag.chunking import chunk_document
from app.rag.store import RagChunk, RagStore

logger = logging.getLogger(__name__)

MAX_INDEX_TEXT_CHARS = 200_000

app = FastAPI(title="EpitomeJob Agent Brain (W3)", version="3.0.0")

_llm: OllamaClient | None = None
_store: RagStore | None = None


def get_llm() -> OllamaClient:
    global _llm
    if _llm is None:
        _llm = OllamaClient(
            base_url=settings.ollama_base_url,
            chat_model=settings.ollama_chat_model,
            embed_model=settings.ollama_embed_model,
            timeout_s=settings.ollama_timeout_s,
        )
    return _llm


def get_store() -> RagStore:
    global _store
    if _store is None:
        _store = RagStore(settings.database_url)
    return _store


# -- request/response models (contract §2) -------------------------------
class ChatRequest(BaseModel):
    message: str
    userId: str


class CitationOut(BaseModel):
    docId: str
    chunk: str
    score: float
    source: str


class ActionOut(BaseModel):
    type: str
    status: str
    approvalId: str | None = None
    detail: str = ""


class ChatResponse(BaseModel):
    reply: str
    citations: list[CitationOut] = []
    actions: list[ActionOut] = []
    specialistsUsed: list[str] = []


class HealthResponse(BaseModel):
    status: str = "ok"
    ollama: str
    temporal: str


class RagIndexRequest(BaseModel):
    docId: str
    ownerType: str
    ownerId: str
    text: str
    metadata: dict[str, object] = Field(default_factory=dict)


class RagQueryRequest(BaseModel):
    query: str
    topK: int = 5


class RagChunkOut(BaseModel):
    docId: str
    text: str
    score: float
    source: str


# -- auth ----------------------------------------------------------------
async def _authenticate(authorization: str | None) -> tuple[str, str]:
    """Return (token, userId). 401 on a bad token, 502 when Java is down."""
    if not authorization or not authorization.startswith("Bearer "):
        raise HTTPException(status_code=401, detail="missing Bearer token")
    token = authorization[len("Bearer ") :].strip()
    if not token:
        raise HTTPException(status_code=401, detail="missing Bearer token")
    try:
        profile = await validate_token(token, settings.java_base_url)
    except InvalidTokenError as exc:
        raise HTTPException(status_code=401, detail=str(exc)) from exc
    except JavaUnavailableError as exc:
        raise HTTPException(status_code=502, detail=str(exc)) from exc
    user_id = str(profile.get("userId", ""))
    if not user_id:
        raise HTTPException(status_code=401, detail="profile has no userId")
    return token, user_id


async def _temporal_is_up() -> bool:
    if not settings.temporal_enabled:
        return False
    try:
        from temporalio.client import Client

        # NOTE: temporalio 1.36 Client has no close() — the channel is
        # released when the client is garbage-collected. Do NOT re-add a
        # close() call here: it raises AttributeError and the probe reports
        # Temporal as down even though the server is up.
        await asyncio.wait_for(
            Client.connect(settings.temporal_address), timeout=2.0
        )
        return True
    except Exception as exc:  # noqa: BLE001 - any failure means "down"
        logger.debug("Temporal probe failed: %s", exc)
        return False


async def _run_via_temporal(message: str, user_id: str, token: str) -> dict[str, object] | None:
    """Run the chat through Temporal. Returns None when Temporal is unusable
    (caller falls back to in-process)."""
    if not settings.temporal_enabled:
        return None
    try:
        from temporalio.client import Client

        from app.temporal.workflows import CoachChatInput, CoachWorkflow

        client = await asyncio.wait_for(
            Client.connect(settings.temporal_address), timeout=5.0
        )
        # NOTE: temporalio 1.36 Client has no close() — do NOT add one here.
        # The old finally: await client.close() raised AttributeError after
        # every run and discarded the workflow result, forcing a fallback.
        handle = await client.start_workflow(
            CoachWorkflow.run,
            CoachChatInput(message=message, user_id=user_id, token=token),
            id=f"coach-{user_id}-{uuid.uuid4()}",
            task_queue="coach-queue",
        )
        result = await asyncio.wait_for(handle.result(), timeout=settings.ollama_timeout_s)
        return result  # type: ignore[return-value]
    except Exception as exc:  # noqa: BLE001 - fall back in-process per §7
        logger.warning("Temporal /chat failed, falling back in-process: %s", exc)
        return None


def _deps_for(token: str) -> CoachDeps:
    return CoachDeps(
        java=JavaClient(settings.java_base_url, token),
        llm=get_llm(),
        store=get_store(),
    )


# -- endpoints -----------------------------------------------------------
@app.get("/health", response_model=HealthResponse)
async def health() -> HealthResponse:
    ollama_up = await get_llm().probe()
    temporal_up = await _temporal_is_up()
    return HealthResponse(
        status="ok",
        ollama="up" if ollama_up else "down",
        temporal="up" if temporal_up else "down",
    )


@app.post("/chat", response_model=ChatResponse)
async def chat(req: ChatRequest, authorization: str | None = Header(default=None)) -> ChatResponse:
    if not req.message or not req.message.strip():
        raise HTTPException(status_code=400, detail="message must not be empty")
    token, user_id = await _authenticate(authorization)
    if req.userId != user_id:
        raise HTTPException(status_code=401, detail="userId does not match the token")
    try:
        result = await _run_via_temporal(req.message, user_id, token)
        if result is None:
            result = await run_coach(req.message, user_id, token, _deps_for(token))
        return ChatResponse(**result)  # type: ignore[arg-type]
    except (InvalidTokenError, JavaUnavailableError) as exc:
        raise HTTPException(status_code=502, detail=str(exc)) from exc
    except HTTPException:
        raise
    except Exception as exc:  # noqa: BLE001
        logger.exception("chat failed")
        return JSONResponse(
            status_code=500,
            content={"code": "CHAT_FAILED", "message": str(exc)[:300]},
        )  # type: ignore[return-value]


@app.post("/rag/index", status_code=201)
async def rag_index(
    req: RagIndexRequest, authorization: str | None = Header(default=None)
) -> dict[str, object]:
    token, user_id = await _authenticate(authorization)
    if len(req.text) > MAX_INDEX_TEXT_CHARS:
        raise HTTPException(status_code=400, detail="text over 200,000 chars")
    if req.ownerType not in ("job", "resume"):
        raise HTTPException(status_code=400, detail="ownerType must be 'job' or 'resume'")
    if req.docId != f"{req.ownerType}:{req.ownerId}":
        raise HTTPException(status_code=400, detail="docId must be '<ownerType>:<ownerId>'")
    java = JavaClient(settings.java_base_url, token)
    if req.ownerType == "resume":
        # The token's user must own the document: resume must be in their list.
        try:
            resumes = await java.list_resumes()
        except JavaUnavailableError as exc:
            raise HTTPException(status_code=502, detail=str(exc)) from exc
        if req.ownerId not in {str(r.get("id")) for r in resumes}:
            raise HTTPException(status_code=404, detail="resume not found for this user")
    chunks = chunk_document(req.text)
    if not chunks:
        return {"docId": req.docId, "chunks": 0}
    llm = get_llm()
    embeddings = await llm.embed([c.text for c in chunks])
    if embeddings is None:
        raise HTTPException(status_code=503, detail="Ollama embeddings unavailable")
    try:
        count = get_store().replace_document(
            req.ownerType,
            req.ownerId,
            [(c.index, c.text) for c in chunks],
            embeddings,
            req.metadata,
        )
    except Exception as exc:  # noqa: BLE001
        logger.exception("rag index failed")
        return JSONResponse(
            status_code=500, content={"code": "RAG_INDEX_FAILED", "message": str(exc)[:300]}
        )  # type: ignore[return-value]
    return {"docId": req.docId, "chunks": count}


@app.post("/rag/query")
async def rag_query(
    req: RagQueryRequest, authorization: str | None = Header(default=None)
) -> dict[str, object]:
    token, user_id = await _authenticate(authorization)
    _ = (token, user_id)
    top_k = max(1, min(req.topK, 10))
    if not req.query or not req.query.strip():
        raise HTTPException(status_code=400, detail="query must not be empty")
    llm = get_llm()
    embeddings = await llm.embed([req.query])
    if embeddings is None:
        # Ollama down: zero hits, never an error for this endpoint.
        return {"chunks": []}
    try:
        chunks: list[RagChunk] = get_store().query_hybrid(req.query, embeddings[0], top_k=top_k)
    except Exception as exc:  # noqa: BLE001
        logger.exception("rag query failed")
        return JSONResponse(
            status_code=500, content={"code": "RAG_QUERY_FAILED", "message": str(exc)[:300]}
        )  # type: ignore[return-value]
    return {
        "chunks": [
            {"docId": c.doc_id, "text": c.text, "score": c.score, "source": c.source}
            for c in chunks
        ]
    }


@app.exception_handler(Exception)
async def _unhandled(request: Request, exc: Exception) -> JSONResponse:
    logger.exception("unhandled error on %s", request.url.path)
    return JSONResponse(
        status_code=500, content={"code": "INTERNAL_ERROR", "message": str(exc)[:300]}
    )


# -- Quester (chief QA tester agent) ---------------------------------------
# BEGIN QUESTER WIRING (Quester worker, 2026-11-14). Delimited block: the
# parallel Resume Builder work may also edit this file — keep this block
# intact and re-apply it after their change if it conflicts. Quester's
# only touchpoints here are this import and the include_router line below;
# all Quester code lives under app/quester/.
from app.quester.router import router as quester_router

app.include_router(quester_router)
# END QUESTER WIRING
