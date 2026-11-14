"""Quester service: test generation and failure triage.

Both functions take an ``llm`` object with an async
``chat(messages, temperature=...)`` method (``OllamaClient`` in prod,
a fake in tests). The LLM is only ever asked to WRITE text at
temperature 0; it never decides pass/fail and never overrides a
deterministic triage verdict.

Errors:
  * ``OllamaDownError`` — the LLM returned nothing (Ollama down); the
    router maps this to 503.
  * ``GenerationError`` — the LLM replied but not with usable JSON; the
    router maps this to 502.
"""

from __future__ import annotations

import json
import logging
import re
from typing import Protocol

from app.quester import validate as v
from app.quester.schemas import (
    AnalyzeFailureRequest,
    GenerateTestsRequest,
    GenerateTestsResponse,
    GeneratedTest,
    TriageResponse,
)
from app.quester.signatures import extract_failing_step, match_signature

logger = logging.getLogger(__name__)

MAX_LLM_FAILURE_CHARS = 8_000


class LlmClient(Protocol):
    async def chat(
        self, messages: list[dict[str, str]], temperature: float | None = None
    ) -> str | None: ...


class OllamaDownError(RuntimeError):
    """The local LLM returned nothing (Ollama down or timed out)."""


class GenerationError(RuntimeError):
    """The LLM replied but the reply was not usable (bad JSON, no tests)."""


# --------------------------------------------------------------------------
# test generation
# --------------------------------------------------------------------------

def _generation_prompt(req: GenerateTestsRequest) -> list[dict[str, str]]:
    endpoints = (
        "\n".join(f"- {ep}" for ep in req.known_endpoints)
        if req.known_endpoints
        else "(no endpoint list supplied — use only endpoints named in the context)"
    )
    return [
        {
            "role": "system",
            "content": (
                "You generate test files for the EpitomeJob codebase. "
                "Reply with ONLY a JSON object, no prose, no markdown fences: "
                '{"tests": [{"path": "...", "content": "..."}, ...]}. '
                "Rules, in order: (1) every test MUST call only endpoints from "
                "the known-endpoint list below — never invent an endpoint, never "
                "guess a path; if the context names no endpoint for an assertion, "
                "assert on the response shape instead; (2) choose the path by "
                "stack — Python pytest files under tests/, Playwright specs "
                "under web/e2e/ ending .spec.ts, JUnit tests under "
                "backend/src/test/; (3) every file must be complete and parseable "
                "on its own; (4) each file starts with a comment naming the "
                "endpoint(s) it covers."
            ),
        },
        {
            "role": "user",
            "content": (
                f"Target: {req.target}\n"
                f"Known endpoints (use ONLY these):\n{endpoints}\n"
                f"Context:\n{req.context}\n"
                "Generate the tests as JSON."
            ),
        },
    ]


def _extract_json(text: str) -> dict[str, object] | None:
    """Parse the LLM's JSON, tolerating a ```json fenced block."""
    fenced = re.search(r"```(?:json)?\s*(\{.*?\})\s*```", text, re.DOTALL)
    candidate = fenced.group(1) if fenced else text
    start, end = candidate.find("{"), candidate.rfind("}")
    if start < 0 or end <= start:
        return None
    try:
        parsed = json.loads(candidate[start : end + 1])
    except json.JSONDecodeError:
        return None
    return parsed if isinstance(parsed, dict) else None


async def generate_tests(
    llm: LlmClient, req: GenerateTestsRequest
) -> GenerateTestsResponse:
    """Generate tests for ``req.target``; only validated tests are returned.

    Rejected tests are listed in ``warnings`` with the reason — they are
    never silently dropped and never returned as if they were good.
    """
    reply = await llm.chat(_generation_prompt(req), temperature=0)
    if reply is None:
        raise OllamaDownError("Ollama returned nothing (is it running on :11434?)")
    parsed = _extract_json(reply)
    if parsed is None or not isinstance(parsed.get("tests"), list):
        raise GenerationError("LLM reply was not JSON with a 'tests' list")
    warnings: list[str] = []
    if not req.known_endpoints:
        warnings.append(
            "no known-endpoint list supplied — the only-known-endpoints guard did not run"
        )
    tests: list[GeneratedTest] = []
    for item in parsed["tests"]:
        if not isinstance(item, dict):
            warnings.append("Rejected a generated test: not an object with path/content")
            continue
        path, content = str(item.get("path", "")), str(item.get("content", ""))
        problems: list[str] = []
        path_problem = v.validate_path(path)
        if path_problem:
            problems.append(path_problem)
        else:
            content_problems, notes = v.validate_generated(path, content)
            problems.extend(content_problems)
            warnings.extend(f"{path}: {note}" for note in notes)
            unknown = v.unknown_endpoints(content, req.known_endpoints)
            if unknown:
                problems.append(
                    "calls unknown endpoint(s): " + ", ".join(sorted(unknown))
                )
        if problems:
            warnings.append(f"Rejected {path or '(no path)'}: " + "; ".join(problems))
            continue
        tests.append(GeneratedTest(path=path, content=content))
    if not tests and not warnings:
        warnings.append("LLM returned no tests")
    return GenerateTestsResponse(tests=tests, warnings=warnings)


# --------------------------------------------------------------------------
# failure triage
# --------------------------------------------------------------------------

_GENERIC_CAUSE = (
    "No deterministic signature matched this failure, so the cause could "
    "not be pinned down from the excerpt alone."
)
_GENERIC_DO_FIRST = (
    "Re-run the failing test in isolation with the full log and paste the "
    "complete failure output — the excerpt does not show enough to act on."
)


def _triage_prompt(verdict: str, failure_text: str) -> list[dict[str, str]]:
    return [
        {
            "role": "system",
            "content": (
                "You explain a test failure for a developer. Reply with ONLY "
                "a JSON object, no prose: "
                '{"likely_cause": "...", "what_to_do_first": "..."}. '
                "One or two sentences each. Be concrete: name the file, test, "
                "or service involved when the failure text shows it. Never "
                "claim a verdict or a root cause the failure text does not "
                "support — say what is unknown instead."
            ),
        },
        {
            "role": "user",
            "content": (
                f"Triaged verdict (already decided, do not change it): {verdict}\n"
                f"Failure text:\n{failure_text[:MAX_LLM_FAILURE_CHARS]}"
            ),
        },
    ]


async def analyze_failure(
    llm: LlmClient, req: AnalyzeFailureRequest
) -> TriageResponse:
    """Triage a failure: deterministic signature first, LLM explains.

    When a signature matches, its verdict, likely cause and first action
    are authoritative. The LLM is still asked for a short explanation,
    which is appended to the cause as a note — it can add context but can
    never change the verdict or replace the runbook action.
    """
    signature = match_signature(req.failure_text)
    failing_step = extract_failing_step(req.failure_text)
    if signature is not None:
        verdict = signature.verdict
        likely_cause = signature.likely_cause
        what_to_do_first = signature.what_to_do_first
        deterministic = True
        matched = signature.name
    else:
        # No signature: a failing test with no env/flaky evidence is most
        # likely a regression. The LLM then writes the explanation.
        verdict = "regression"
        likely_cause = _GENERIC_CAUSE
        what_to_do_first = _GENERIC_DO_FIRST
        deterministic = False
        matched = None

    explanation = await llm.chat(
        _triage_prompt(verdict, req.failure_text), temperature=0
    )
    if explanation is not None:
        parsed = _extract_json(explanation)
        if parsed is not None:
            llm_cause = parsed.get("likely_cause")
            llm_action = parsed.get("what_to_do_first")
            if signature is not None:
                # Signature is authoritative; the LLM text is a note only.
                if isinstance(llm_cause, str) and llm_cause.strip():
                    likely_cause = f"{likely_cause} Note: {llm_cause.strip()[:300]}"
            else:
                if isinstance(llm_cause, str) and llm_cause.strip():
                    likely_cause = llm_cause.strip()[:600]
                if isinstance(llm_action, str) and llm_action.strip():
                    what_to_do_first = llm_action.strip()[:600]
        # An unparseable LLM explanation is ignored: the deterministic
        # fields (or the generic fallback) already answer the question.

    return TriageResponse(
        verdict=verdict,  # type: ignore[arg-type]
        failing_step=failing_step,
        likely_cause=likely_cause,
        what_to_do_first=what_to_do_first,
        deterministic=deterministic,
        matched_signature=matched,
    )
