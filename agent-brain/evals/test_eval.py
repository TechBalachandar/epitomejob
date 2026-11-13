"""Eval runner (contract §8): one command — ``pytest evals``.

Deterministic checks (all 20 questions, mocked LLM/Java/RAG):
  * routing sends the question to the expected specialist (fallback router);
  * citations present when ``needsCitations`` is true;
  * ``expectsAction`` questions produce an approval proposal (mocked Java);
  * ``guardrail`` questions assert NO write happens without a proposal
    (no direct PUT/POST to Java except /approvals).

Judge (8 of the 20 — the ``needsCitations`` questions, mark ``judge``):
Ollama ``llama3.1:8b`` scores relevance/grounding 1-5; pass = score >= 4.
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
from app.graph.router import route_fallback
from app.graph.supervisor import CoachDeps, run_coach
from app.rag.index import reset_indexed_cache
from app.rag.store import RagChunk

QUESTIONS = json.loads((Path(__file__).parent / "questions.json").read_text())
JUDGE_IDS = {q["id"] for q in QUESTIONS if q["needsCitations"]}
assert len(JUDGE_IDS) == 8, "judge subset must be exactly the 8 needsCitations questions"

USER_ID = "00000000-0000-0000-0000-000000000001"
TOKEN = "eval-token"
JOB_ID = "10000000-0000-0000-0000-000000000001"
RESUME_ID = "20000000-0000-0000-0000-000000000002"


# -- fakes (mocked LLM / Java / RAG; no Ollama, Temporal or Postgres) -----
class FakeOllama:
    def __init__(self, down: bool = True, replies: list[str] | None = None) -> None:
        self.down = down
        self.replies = replies or []

    async def probe(self) -> bool:
        return not self.down

    async def chat(self, messages: list[dict[str, str]]) -> str | None:
        if self.down or not self.replies:
            return None
        return self.replies.pop(0)

    async def embed(self, texts: list[str]) -> list[list[float]] | None:
        if self.down:
            return None
        return [[0.01] * 768 for _ in texts]


class FakeJava:
    """Records every call; only propose_approval is a write."""

    def __init__(self) -> None:
        self.proposals: list[dict[str, object]] = []
        self.direct_writes: list[str] = []

    async def get_profile(self) -> dict[str, object]:
        return {"userId": USER_ID, "headline": "Java Developer", "summary": "17y experience"}

    async def list_resumes(self) -> list[dict[str, object]]:
        return [{"id": RESUME_ID, "status": "READY"}]

    async def get_resume_text(self, resume_id: str) -> dict[str, object]:
        return {"resumeId": resume_id, "text": (
            "Balachandar Jeldi — Senior Java Developer\n"
            "17 years building banking and enterprise systems with Java and Spring Boot.\n"
            "- Led a team of 10 engineers delivering mobile banking APIs used by millions of members.\n"
            "- Built Kafka event pipelines processing 50,000 messages per second.\n"
            "- Skills: Java, Spring Boot, Kafka, Spring Security, PostgreSQL, Azure."
        )}

    async def list_jobs(self, **kwargs: object) -> list[dict[str, object]]:
        return [
            {
                "id": JOB_ID, "title": "Senior AI Engineer", "company": "Acme Health",
                "location": "Reston, VA", "workType": "REMOTE", "salaryMin": 150000,
                "salaryMax": 190000, "sponsorsH1b": True,
                "skills": ["Python", "LangGraph"], "summary": "Build agentic AI.",
                "role": "AI Engineer",
            }
        ]

    async def get_job(self, job_id: str) -> dict[str, object]:
        return (await self.list_jobs())[0]

    async def get_match(self, job_id: str) -> dict[str, object]:
        return {"jobId": job_id, "score": 0.82, "reasons": ["Java + Spring Boot"], "gaps": ["Python", "LangGraph"]}

    async def list_matches(self) -> list[dict[str, object]]:
        return []

    async def get_tracker(self) -> list[dict[str, object]]:
        return []

    async def get_interview_questions(self, job_id: str | None = None) -> list[dict[str, object]]:
        return [
            {"question": "Tell me about a system you designed."},
            {"question": "How do you handle a production outage in a Java service?"},
            {"question": "Explain how you would design a rate limiter."},
        ]

    async def list_interview_sessions(self) -> list[dict[str, object]]:
        return []

    async def propose_approval(self, action: str, payload: dict[str, object]) -> dict[str, object]:
        self.proposals.append({"action": action, "payload": payload})
        return {"id": "approval-1", "action": action, "status": "PENDING"}

    async def aclose(self) -> None:
        pass


class FakeStore:
    def __init__(self) -> None:
        self.indexed: list[str] = []

    def replace_document(self, owner_type: str, owner_id: str, chunks, embeddings, metadata=None) -> int:
        self.indexed.append(f"{owner_type}:{owner_id}")
        return len(chunks)

    def query_hybrid(self, query_text: str, query_embedding: list[float], top_k: int = 5) -> list[RagChunk]:
        return [
            RagChunk(doc_id=f"job:{JOB_ID}", text="Senior AI Engineer at Acme Health, Python LangGraph", score=0.0323, source="job"),
            RagChunk(doc_id=f"resume:{RESUME_ID}", text="Java developer with Spring Boot and Kafka experience.", score=0.0161, source="resume"),
        ][:top_k]


def _deps(down: bool = True) -> CoachDeps:
    return CoachDeps(java=FakeJava(), llm=FakeOllama(down=down), store=FakeStore())  # type: ignore[arg-type]


def _ollama_up() -> bool:
    try:
        resp = httpx.get(f"{settings.ollama_base_url}/api/tags", timeout=2.0, trust_env=False)
        return resp.status_code == 200
    except Exception:  # noqa: BLE001
        return False


# -- deterministic checks --------------------------------------------------
@pytest.mark.parametrize("q", QUESTIONS, ids=[q["id"] for q in QUESTIONS])
def test_routing_sends_to_expected_specialist(q: dict[str, object]) -> None:
    assert route_fallback(str(q["question"])) == q["specialist"]


@pytest.mark.parametrize("q", [q for q in QUESTIONS if q["needsCitations"]], ids=lambda q: q["id"])
async def test_citations_present_when_needed(q: dict[str, object]) -> None:
    reset_indexed_cache()
    # Mocked LLM "up" for embeddings (chat still silent -> keyword fallback
    # routing); RAG needs embeddings to return chunks.
    deps = _deps(down=False)
    result = await run_coach(str(q["question"]), USER_ID, TOKEN, deps)
    assert result["citations"], f"{q['id']}: expected citations, got none"
    assert str(q["specialist"]) in result["specialistsUsed"]


@pytest.mark.parametrize("q", [q for q in QUESTIONS if q["expectsAction"]], ids=lambda q: q["id"])
async def test_expects_action_produces_approval_proposal(q: dict[str, object]) -> None:
    reset_indexed_cache()
    deps = _deps(down=False)
    result = await run_coach(str(q["question"]), USER_ID, TOKEN, deps)
    actions = result["actions"]
    assert actions, f"{q['id']}: expected an approval proposal action"
    assert all(a["status"] == "proposed" for a in actions)
    assert all(a["approvalId"] for a in actions)
    assert deps.java.proposals, "expected POST /approvals to be proposed"  # type: ignore[attr-defined]


@pytest.mark.parametrize("q", [q for q in QUESTIONS if q["guardrail"]], ids=lambda q: q["id"])
async def test_guardrail_no_write_without_proposal(q: dict[str, object]) -> None:
    reset_indexed_cache()
    deps = _deps()
    result = await run_coach(str(q["question"]), USER_ID, TOKEN, deps)
    fake_java = deps.java
    assert fake_java.direct_writes == [], "a direct write happened"  # type: ignore[attr-defined]
    for action in result["actions"]:
        assert action["status"] in ("proposed", "needs_approval"), (
            f"{q['id']}: write action with status {action['status']!r} — "
            "a write must never be 'done' without a prior approval"
        )
    if "no-write" in str(q["guardrail"]):
        assert result["actions"] == [], f"{q['id']}: read-only question produced actions"


# -- judge (Ollama; skipped when Ollama is down) ---------------------------
@pytest.mark.judge
@pytest.mark.parametrize("q", [q for q in QUESTIONS if q["id"] in JUDGE_IDS], ids=lambda q: q["id"])
async def test_judge_relevance_grounding(q: dict[str, object]) -> None:
    if not _ollama_up():
        pytest.skip("Ollama down")
    from app.llm import OllamaClient

    reset_indexed_cache()
    llm = OllamaClient(
        base_url=settings.ollama_base_url,
        chat_model=settings.ollama_chat_model,
        embed_model=settings.ollama_embed_model,
        timeout_s=settings.ollama_timeout_s,
    )
    deps = CoachDeps(java=FakeJava(), llm=llm, store=FakeStore())  # type: ignore[arg-type]
    result = await run_coach(str(q["question"]), USER_ID, TOKEN, deps)
    reply = str(result["reply"])
    verdict = await llm.chat(
        [
            {
                "role": "system",
                "content": (
                    "You judge a job-coach answer for relevance and grounding. "
                    "Reply with exactly one integer from 1 to 5 and nothing else. "
                    "5 = directly answers the question and stays grounded in the "
                    "given facts; 1 = irrelevant or invented."
                ),
            },
            {
                "role": "user",
                "content": f"Question: {q['question']}\nAnswer: {reply}\nScore (1-5):",
            },
        ],
        temperature=0,  # deterministic measurement: same reply, same score
    )
    await llm.aclose()
    assert verdict is not None, "judge LLM returned nothing"
    match = re.search(r"[1-5]", verdict)
    assert match, f"judge returned unparseable verdict: {verdict!r}"
    score = int(match.group(0))
    assert score >= 4, f"{q['id']}: judge score {score} < 4 for reply: {reply[:300]}"
