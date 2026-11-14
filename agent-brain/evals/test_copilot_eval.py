"""Resume Builder Co-Pilot evals (follows the Coach eval layout).

Deterministic checks (fake LLM — no Ollama, no Java, no Postgres):
  * options count is 3-5;
  * placeholders present (no invented numbers) when metrics are missing;
  * no invented companies/titles;
  * ATS keywords come from the target JD;
  * output is valid JSON matching the schema;
  * a selection returns a populated rendered_patch;
  * invalid LLM output falls back to REQUEST_METRICS (retry once first);
  * Ollama down falls back to REQUEST_METRICS (never crashes).

Judge (2 cases, mark ``judge``): Ollama ``llama3.1:8b`` scores the
real Co-Pilot reply 1-5 on grounding / no-hallucination / ATS fit;
pass = score >= 4. When Ollama is unreachable, judge tests are SKIPPED
(not failed), same as the Coach evals.
"""

from __future__ import annotations

import json
import re

import httpx
import pytest

from app.config import settings
from app.graph.copilot import run_copilot

JD = (
    "Senior Java Developer — Spring Boot, Kafka, PostgreSQL, Azure. "
    "Lead a team of engineers, ship banking APIs, mentor junior developers."
)
RESUME_STATE = {
    "name": "Balachandar Jeldi",
    "summary": "Java developer with 17 years of experience.",
    "experience": [{"title": "Senior Java Developer", "company": "Navy Federal"}],
}

# Famous employers the user has never worked for (none appear in any
# input below) — a Co-Pilot reply naming one is inventing a company.
INVENTED_COMPANIES = ["Google", "Microsoft", "Amazon", "Meta", "Apple", "Netflix", "Goldman Sachs"]


def _option(option_id: int, tag: str, bullets: list[str], keywords: list[str]) -> dict:
    return {
        "option_id": option_id,
        "angle_tag": tag,
        "content": bullets,
        "missing_placeholders": ["[X%]", "[N+ users]"],
        "ats_keywords_used": keywords,
    }


def _reply(section: str, options: list[dict]) -> str:
    return json.dumps(
        {
            "ui_message": "Pick the angle that fits this role best.",
            "active_section": section,
            "action_type": "PRESENT_OPTIONS",
            "options": options,
            "rendered_patch": {"target_block_id": section, "applied_content": None},
            "suggested_next_step": "Fill in the [X%] placeholder with your real number.",
        }
    )


EXPERIENCE_REPLY = _reply(
    "experience_item_2",
    [
        _option(1, "Metrics & Impact",
                ["Improved API response times by [X%] by redesigning the Spring Boot service layer for [N+ users]."],
                ["Spring Boot"]),
        _option(2, "Technical Depth",
                ["Architected Kafka event pipelines and PostgreSQL data models serving [N+ users] across banking APIs."],
                ["Kafka", "PostgreSQL"]),
        _option(3, "Leadership",
                ["Led a team of [N] engineers delivering banking APIs, mentoring junior developers on Spring Boot."],
                ["Spring Boot"]),
        _option(4, "ATS Keywords",
                ["Senior Java Developer with Spring Boot, Kafka, PostgreSQL, and Azure experience in banking APIs."],
                ["Spring Boot", "Kafka", "PostgreSQL", "Azure"]),
    ],
)

SUMMARY_REPLY = _reply(
    "summary",
    [
        _option(1, "Metrics & Impact",
                ["Java developer with 17 years of experience delivering banking systems used by [N+ users]."],
                ["Java"]),
        _option(2, "Technical Depth",
                ["Spring Boot, Kafka, and PostgreSQL engineer building high-throughput banking APIs on Azure."],
                ["Spring Boot", "Kafka", "PostgreSQL", "Azure"]),
        _option(3, "Leadership",
                ["Engineering lead who mentors teams and ships reliable banking APIs on time."],
                []),
    ],
)

SKILLS_REPLY = _reply(
    "skills",
    [
        _option(1, "Metrics & Impact", ["Java, Spring Boot, and Kafka — [N] years building banking APIs."], []),
        _option(2, "Technical Depth", ["Spring Boot microservices, Kafka pipelines, PostgreSQL, Azure."], []),
        _option(3, "Leadership", ["Mentoring, code review, and delivery ownership across [N] engineers."], []),
        _option(4, "ATS Keywords", ["Java, Spring Boot, Kafka, PostgreSQL, Azure."], []),
        _option(5, "Executive Punch", ["17y Java / Spring Boot / Kafka banking engineer."], []),
    ],
)

# Deterministic cases: (id, section, user_context, target_jd, fake_reply)
VALID_CASES = [
    ("exp-metrics", "experience_item_2",
     "Senior Java Developer at Navy Federal. Led Spring Boot migration; no metrics recorded yet.",
     JD, EXPERIENCE_REPLY),
    ("summary", "summary",
     "Java developer, 17 years, banking systems, led a team of 10.",
     JD, SUMMARY_REPLY),
    ("skills-no-jd", "skills",
     "Java, Spring Boot, Kafka, PostgreSQL, Azure.",
     None, SKILLS_REPLY),
    ("project", "project_1",
     "Built a Kafka pipeline for banking events; team of 4.",
     JD, EXPERIENCE_REPLY),
]

SELECTION_CASES = [
    ("select-option-2", "experience_item_2",
     json.dumps({"select_option": 2, "notes": "Senior Java Developer at Navy Federal."}),
     JD, EXPERIENCE_REPLY, 2),
    ("select-option-4", "experience_item_2",
     json.dumps({"select_option": 4}),
     JD, EXPERIENCE_REPLY, 4),
]


class FakeCopilotLLM:
    """Fake Ollama: replies popped in order; None means Ollama down."""

    def __init__(self, replies: list[str | None] | None = None) -> None:
        self.replies = list(replies) if replies is not None else []
        self.calls: list[list[dict[str, str]]] = []

    async def probe(self) -> bool:
        return True

    async def chat(self, messages: list[dict[str, str]], temperature: float | None = None) -> str | None:
        self.calls.append(messages)
        if not self.replies:
            return None
        return self.replies.pop(0)

    async def embed(self, texts: list[str]) -> list[list[float]] | None:
        return [[0.01] * 768 for _ in texts]


def _ollama_up() -> bool:
    try:
        resp = httpx.get(f"{settings.ollama_base_url}/api/tags", timeout=2.0, trust_env=False)
        return resp.status_code == 200
    except Exception:  # noqa: BLE001
        return False


async def _run(case: tuple) -> tuple[dict, FakeCopilotLLM]:
    _, section, ctx, jd, reply = case
    llm = FakeCopilotLLM([reply])
    result = await run_copilot(section, ctx, jd, RESUME_STATE, llm)  # type: ignore[arg-type]
    return result, llm


# -- deterministic checks --------------------------------------------------
@pytest.mark.parametrize("case", VALID_CASES, ids=[c[0] for c in VALID_CASES])
async def test_copilot_eval_options_count_3_to_5(case: tuple) -> None:
    result, _ = await _run(case)
    assert result["action_type"] == "PRESENT_OPTIONS"
    assert 3 <= len(result["options"]) <= 5, f"{case[0]}: {len(result['options'])} options"  # type: ignore[arg-type]
    assert len({o["option_id"] for o in result["options"]}) == len(result["options"])  # type: ignore[union-attr]


@pytest.mark.parametrize("case", VALID_CASES, ids=[c[0] for c in VALID_CASES])
async def test_copilot_eval_valid_json_schema(case: tuple) -> None:
    result, _ = await _run(case)
    assert set(result) == {
        "ui_message", "active_section", "action_type",
        "options", "rendered_patch", "suggested_next_step",
    }
    assert isinstance(result["ui_message"], str) and result["ui_message"].strip()
    assert result["active_section"] == case[1]
    assert result["action_type"] in ("PRESENT_OPTIONS", "APPLY_SELECTION", "REQUEST_METRICS")
    assert result["rendered_patch"]["applied_content"] is None  # type: ignore[index]
    assert isinstance(result["suggested_next_step"], str)
    for opt in result["options"]:  # type: ignore[union-attr]
        assert isinstance(opt["option_id"], int)
        assert isinstance(opt["angle_tag"], str) and opt["angle_tag"].strip()
        assert isinstance(opt["content"], list) and opt["content"]
        assert all(isinstance(b, str) and b.strip() for b in opt["content"])


@pytest.mark.parametrize("case", [c for c in VALID_CASES if c[0] in ("exp-metrics", "summary")],
                         ids=lambda c: c[0])
async def test_copilot_eval_placeholders_not_invented_numbers(case: tuple) -> None:
    """User context has no metrics: bullets must use editable placeholders,
    never a bare invented number like 'by 40%'."""
    result, _ = await _run(case)
    joined = "\n".join(b for o in result["options"] for b in o["content"])  # type: ignore[union-attr]
    assert "[" in joined, f"{case[0]}: no editable placeholder in options"
    assert not re.search(r"\d+\s*%", joined), f"{case[0]}: invented percentage in: {joined[:200]}"
    assert not re.search(r"\$\d", joined), f"{case[0]}: invented dollar figure in: {joined[:200]}"


@pytest.mark.parametrize("case", VALID_CASES, ids=[c[0] for c in VALID_CASES])
async def test_copilot_eval_no_invented_companies(case: tuple) -> None:
    result, _ = await _run(case)
    joined = "\n".join(b for o in result["options"] for b in o["content"])  # type: ignore[union-attr]
    for company in INVENTED_COMPANIES:
        assert company not in joined, f"{case[0]}: invented company {company!r}"


@pytest.mark.parametrize("case", [c for c in VALID_CASES if c[3] is not None],
                         ids=lambda c: c[0])
async def test_copilot_eval_ats_keywords_from_jd(case: tuple) -> None:
    result, _ = await _run(case)
    jd_lower = str(case[3]).lower()
    for opt in result["options"]:  # type: ignore[union-attr]
        for keyword in opt["ats_keywords_used"]:
            assert keyword.lower() in jd_lower, (
                f"{case[0]}: ATS keyword {keyword!r} is not in the target JD"
            )


@pytest.mark.parametrize("case", SELECTION_CASES, ids=[c[0] for c in SELECTION_CASES])
async def test_copilot_eval_selection_returns_populated_patch(case: tuple) -> None:
    _, section, ctx, jd, reply, selected = case
    llm = FakeCopilotLLM([reply])
    result = await run_copilot(section, ctx, jd, RESUME_STATE, llm)  # type: ignore[arg-type]
    assert result["action_type"] == "APPLY_SELECTION", f"{case[0]}: not APPLY_SELECTION"
    patch = result["rendered_patch"]  # type: ignore[assignment]
    assert patch["target_block_id"] == section
    assert isinstance(patch["applied_content"], str) and patch["applied_content"].strip()
    chosen = next(o for o in result["options"] if o["option_id"] == selected)  # type: ignore[union-attr]
    assert patch["applied_content"] == "\n".join(chosen["content"])


async def test_copilot_eval_invalid_output_falls_back_to_request_metrics() -> None:
    llm = FakeCopilotLLM(["this is not json", "{broken json"])
    result = await run_copilot("experience_item_2", "Led migration.", JD, RESUME_STATE, llm)  # type: ignore[arg-type]
    assert len(llm.calls) == 2, "expected exactly one retry"
    assert result["action_type"] == "REQUEST_METRICS"
    assert result["options"] == []  # type: ignore[comparison-overlap]
    assert result["rendered_patch"]["applied_content"] is None  # type: ignore[index]


async def test_copilot_eval_retry_recovers_on_valid_second_reply() -> None:
    llm = FakeCopilotLLM(["garbage", EXPERIENCE_REPLY])
    result = await run_copilot("experience_item_2", "Led migration.", JD, RESUME_STATE, llm)  # type: ignore[arg-type]
    assert len(llm.calls) == 2
    assert result["action_type"] == "PRESENT_OPTIONS"
    assert len(result["options"]) == 4  # type: ignore[arg-type]


async def test_copilot_eval_ollama_down_falls_back() -> None:
    llm = FakeCopilotLLM([None])
    result = await run_copilot("experience_item_2", "Led migration.", JD, RESUME_STATE, llm)  # type: ignore[arg-type]
    assert result["action_type"] == "REQUEST_METRICS"
    assert result["options"] == []  # type: ignore[comparison-overlap]
    assert result["rendered_patch"] == {  # type: ignore[comparison-overlap]
        "target_block_id": "experience_item_2", "applied_content": None,
    }


# -- judge (Ollama; skipped when Ollama is down) ---------------------------
JUDGE_CASES = [
    ("judge-exp", "experience_item_2",
     "Senior Java Developer at Navy Federal. Led Spring Boot migration for mobile banking APIs; no metrics recorded yet.",
     JD),
    ("judge-summary", "summary",
     "Java developer, 17 years, banking systems, led a team of 10 engineers.",
     JD),
]


@pytest.mark.judge
@pytest.mark.parametrize("case", JUDGE_CASES, ids=[c[0] for c in JUDGE_CASES])
async def test_copilot_judge_grounded_no_hallucination(case: tuple) -> None:
    if not _ollama_up():
        pytest.skip("Ollama down")
    from app.llm import OllamaClient

    _, section, ctx, jd = case
    llm = OllamaClient(
        base_url=settings.ollama_base_url,
        chat_model=settings.ollama_chat_model,
        embed_model=settings.ollama_embed_model,
        timeout_s=settings.ollama_timeout_s,
    )
    result = await run_copilot(section, ctx, jd, RESUME_STATE, llm)
    assert result["action_type"] in ("PRESENT_OPTIONS", "REQUEST_METRICS"), (
        f"{case[0]}: real LLM produced invalid action: {result}"
    )
    if result["action_type"] == "REQUEST_METRICS":
        pytest.skip("real LLM asked for metrics instead of options")
    reply_json = json.dumps(result)
    verdict = await llm.chat(
        [
            {
                "role": "system",
                "content": (
                    "You judge a resume co-pilot answer. Reply with exactly one "
                    "integer from 1 to 5 and nothing else. 5 = the options are "
                    "grounded in the given user context and resume, invent no "
                    "companies, titles, or numbers (editable placeholders like "
                    "[X%] are fine), use ATS keywords from the given job "
                    "description, and there are 3-5 options; 1 = invented facts "
                    "or off-topic."
                ),
            },
            {
                "role": "user",
                "content": (
                    f"User context: {ctx}\nTarget JD: {jd}\n"
                    f"Resume state: {json.dumps(RESUME_STATE)}\n"
                    f"Co-pilot reply: {reply_json}\nScore (1-5):"
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
    assert score >= 4, f"{case[0]}: judge score {score} < 4 for reply: {reply_json[:400]}"
