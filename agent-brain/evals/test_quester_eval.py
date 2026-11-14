"""Quester triage evals (contract §8 style): one command — ``pytest evals``.

Deterministic checks (all 8 cases, LLM stubbed to return nothing):
  * the verdict is the expected one (deterministic signature);
  * the matched signature is the expected one;
  * ``what_to_do_first`` contains the expected runbook phrase.

Judge (all 8 cases, mark ``judge``): Ollama ``llama3.1:8b`` scores the
triage explanation (likely_cause + what_to_do_first) 1-5 for this failure;
pass = score >= 4 — the same bar as the Coach evals, never lowered.
When Ollama is unreachable, judge tests are SKIPPED (not failed) and the
terminal summary says ``judge: skipped (Ollama down)``.
"""

from __future__ import annotations

import json
import re
from pathlib import Path

import httpx
import pytest

from app.config import settings
from app.quester.generator import analyze_failure
from app.quester.schemas import AnalyzeFailureRequest

CASES = json.loads((Path(__file__).parent / "quester_cases.json").read_text())
assert len(CASES) == 8, "quester eval must have exactly 8 cases"
assert {c["verdict"] for c in CASES} == {"regression", "flaky", "environment"}, (
    "cases must cover all three verdicts"
)


class _DownLlm:
    """LLM that returns nothing: proves the deterministic path needs no LLM."""

    async def chat(self, messages: list[dict[str, str]], temperature: float | None = None) -> str | None:  # type: ignore[no-untyped-def]
        return None


def _ollama_up() -> bool:
    try:
        resp = httpx.get(f"{settings.ollama_base_url}/api/tags", timeout=2.0, trust_env=False)
        return resp.status_code == 200
    except Exception:  # noqa: BLE001
        return False


# -- deterministic checks --------------------------------------------------
@pytest.mark.parametrize("case", CASES, ids=[c["id"] for c in CASES])
async def test_triage_verdict_and_signature(case: dict[str, object]) -> None:
    result = await analyze_failure(
        _DownLlm(),  # type: ignore[arg-type]
        AnalyzeFailureRequest(failure_text=str(case["failure_text"])),
    )
    assert result.verdict == case["verdict"], (
        f"{case['id']}: verdict {result.verdict!r} != {case['verdict']!r}"
    )
    assert result.matched_signature == case["signature"], (
        f"{case['id']}: signature {result.matched_signature!r} != {case['signature']!r}"
    )
    assert result.deterministic is True
    assert str(case["expect_in_action"]) in result.what_to_do_first, (
        f"{case['id']}: expected {case['expect_in_action']!r} in first action, "
        f"got {result.what_to_do_first!r}"
    )
    assert result.failing_step, f"{case['id']}: failing_step is empty"


# -- judge (Ollama; skipped when Ollama is down) ---------------------------
@pytest.mark.judge
@pytest.mark.parametrize("case", CASES, ids=[c["id"] for c in CASES])
async def test_judge_triage_explanation(case: dict[str, object]) -> None:
    if not _ollama_up():
        pytest.skip("Ollama down")
    from app.llm import OllamaClient

    llm = OllamaClient(
        base_url=settings.ollama_base_url,
        chat_model=settings.ollama_chat_model,
        embed_model=settings.ollama_embed_model,
        timeout_s=settings.ollama_timeout_s,
    )
    result = await analyze_failure(
        llm, AnalyzeFailureRequest(failure_text=str(case["failure_text"]))
    )
    verdict = await llm.chat(
        [
            {
                "role": "system",
                "content": (
                    "You judge a failure-triage explanation for a developer. "
                    "Reply with exactly one integer from 1 to 5 and nothing else. "
                    "5 = the likely cause correctly explains the failure and the "
                    "first action is the right next step; 1 = wrong cause or "
                    "useless first action."
                ),
            },
            {
                "role": "user",
                "content": (
                    f"Failure:\n{case['failure_text']}\n"
                    f"Verdict: {result.verdict}\n"
                    f"Likely cause: {result.likely_cause}\n"
                    f"What to do first: {result.what_to_do_first}\n"
                    "Score (1-5):"
                ),
            },
        ],
        temperature=0,  # deterministic measurement: same reply, same score
    )
    await llm.aclose()
    assert verdict is not None, "judge LLM returned nothing"
    match = re.search(r"[1-5]", verdict)
    assert match, f"judge returned unparseable verdict: {verdict!r}"
    score = int(match.group(0))
    assert score >= 4, (
        f"{case['id']}: judge score {score} < 4 for triage: "
        f"{result.likely_cause[:200]} / {result.what_to_do_first[:200]}"
    )
