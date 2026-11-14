"""Quester HTTP endpoints: POST /quester/generate-tests, /analyze-failure.

Auth mirrors ``app.main`` (Bearer token validated via Java, 401 on a bad
token, 502 when Java is down). The auth helper is duplicated here rather
than imported from ``app.main`` on purpose: ``app.main`` imports this
router, so importing back would be circular. If the auth logic in
``app.main._authenticate`` changes, change this one too.

Error mapping: Ollama down -> 503 (retryable), LLM replied unusable
JSON -> 502. Generated tests are returned for human review only — this
module never writes to the repo, never commits, never merges.
"""

from __future__ import annotations

from fastapi import APIRouter, Header, HTTPException

from app.auth import InvalidTokenError, JavaUnavailableError, validate_token
from app.config import settings
from app.llm import OllamaClient
from app.quester.generator import (
    GenerationError,
    OllamaDownError,
    analyze_failure,
    generate_tests,
)
from app.quester.schemas import (
    AnalyzeFailureRequest,
    GenerateTestsRequest,
    GenerateTestsResponse,
    TriageResponse,
)

router = APIRouter(prefix="/quester", tags=["quester"])

_llm: OllamaClient | None = None


def get_llm() -> OllamaClient:
    """Lazy singleton, same construction as ``app.main.get_llm``.

    Tests monkeypatch this name (``app.quester.router.get_llm``) with a
    fake — every test that patches it exercises it on its code path.
    """
    global _llm
    if _llm is None:
        _llm = OllamaClient(
            base_url=settings.ollama_base_url,
            chat_model=settings.ollama_chat_model,
            embed_model=settings.ollama_embed_model,
            timeout_s=settings.ollama_timeout_s,
        )
    return _llm


async def _authenticate(authorization: str | None) -> str:
    """Return the validated userId. 401 on a bad token, 502 when Java is down."""
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
    return user_id


@router.post("/generate-tests", response_model=GenerateTestsResponse)
async def generate_tests_endpoint(
    req: GenerateTestsRequest, authorization: str | None = Header(default=None)
) -> GenerateTestsResponse:
    await _authenticate(authorization)
    try:
        return await generate_tests(get_llm(), req)
    except OllamaDownError as exc:
        raise HTTPException(status_code=503, detail=str(exc)) from exc
    except GenerationError as exc:
        raise HTTPException(status_code=502, detail=str(exc)) from exc


@router.post("/analyze-failure", response_model=TriageResponse)
async def analyze_failure_endpoint(
    req: AnalyzeFailureRequest, authorization: str | None = Header(default=None)
) -> TriageResponse:
    await _authenticate(authorization)
    return await analyze_failure(get_llm(), req)
