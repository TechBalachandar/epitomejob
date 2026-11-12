"""Shared fakes for unit tests: mocked LLM / Java / RAG / DB.

No Ollama, Temporal or Postgres is ever touched by these fakes.
"""

from __future__ import annotations

from app.rag.store import RagChunk

USER_ID = "00000000-0000-0000-0000-000000000001"
TOKEN = "test-token"
JOB_ID = "10000000-0000-0000-0000-000000000001"
RESUME_ID = "20000000-0000-0000-0000-000000000002"


class FakeOllama:
    def __init__(self, down: bool = True, chat_replies: list[str | None] | None = None) -> None:
        self.down = down
        self.chat_replies = list(chat_replies or [])

    async def probe(self) -> bool:
        return not self.down

    async def chat(self, messages: list[dict[str, str]]) -> str | None:
        if self.down:
            return None
        if self.chat_replies:
            return self.chat_replies.pop(0)
        return None

    async def embed(self, texts: list[str]) -> list[list[float]] | None:
        if self.down:
            return None
        return [[0.01] * 768 for _ in texts]


class FakeJava:
    """Only propose_approval is a write; everything else is a read."""

    def __init__(self) -> None:
        self.proposals: list[dict[str, object]] = []
        self.calls: list[str] = []

    async def get_profile(self) -> dict[str, object]:
        self.calls.append("get_profile")
        return {"userId": USER_ID, "headline": "Java Developer", "summary": "17y experience"}

    async def list_resumes(self) -> list[dict[str, object]]:
        self.calls.append("list_resumes")
        return [{"id": RESUME_ID, "status": "READY"}]

    async def get_resume_text(self, resume_id: str) -> dict[str, object]:
        self.calls.append("get_resume_text")
        return {"resumeId": resume_id, "text": "Java developer with Spring Boot and Kafka experience."}

    async def list_jobs(self, **kwargs: object) -> list[dict[str, object]]:
        self.calls.append("list_jobs")
        return [
            {
                "id": JOB_ID, "title": "Senior AI Engineer", "company": "Acme Health",
                "location": "Reston, VA", "workType": "REMOTE", "salaryMin": 150000,
                "salaryMax": 190000, "currency": "USD", "sponsorsH1b": True,
                "skills": ["Python", "LangGraph"], "summary": "Build agentic AI.",
                "role": "AI Engineer", "postedAt": None,
            }
        ]

    async def get_job(self, job_id: str) -> dict[str, object]:
        self.calls.append("get_job")
        return (await self.list_jobs())[0]

    async def get_match(self, job_id: str) -> dict[str, object]:
        self.calls.append("get_match")
        return {"jobId": job_id, "score": 0.82, "reasons": ["Java + Spring Boot"], "gaps": ["Python"]}

    async def list_matches(self) -> list[dict[str, object]]:
        return []

    async def get_tracker(self) -> list[dict[str, object]]:
        return []

    async def get_interview_questions(self, job_id: str | None = None) -> list[dict[str, object]]:
        return [{"question": "Tell me about a system you designed."}]

    async def list_interview_sessions(self) -> list[dict[str, object]]:
        return []

    async def propose_approval(self, action: str, payload: dict[str, object]) -> dict[str, object]:
        self.calls.append(f"propose_approval:{action}")
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
            RagChunk(doc_id=f"job:{JOB_ID}", text="Senior AI Engineer at Acme Health", score=0.0323, source="job"),
            RagChunk(doc_id=f"resume:{RESUME_ID}", text="Java developer with Spring Boot", score=0.0161, source="resume"),
        ][:top_k]


class FakeCursor:
    """Records SQL; returns canned rows for the two hybrid queries."""

    def __init__(self) -> None:
        self.executed: list[tuple[str, tuple[object, ...] | None]] = []
        self._fetch: list[tuple[object, ...]] = []

    def __enter__(self) -> "FakeCursor":
        return self

    def __exit__(self, *args: object) -> None:
        pass

    def execute(self, sql: str, params: tuple[object, ...] | None = None) -> None:
        self.executed.append((" ".join(sql.split()), params))
        if "embedding <=>" in sql:
            self._fetch = [
                ("job", JOB_ID, 0, "chunk A"),
                ("resume", RESUME_ID, 0, "chunk B"),
            ]
        elif "content_tsv" in sql:
            self._fetch = [("resume", RESUME_ID, 0, "chunk B")]

    def fetchall(self) -> list[tuple[object, ...]]:
        return self._fetch


class FakeConnection:
    def __init__(self) -> None:
        self.cursor_obj = FakeCursor()
        self.committed = False
        self.closed = False

    def cursor(self) -> FakeCursor:
        return self.cursor_obj

    def commit(self) -> None:
        self.committed = True

    def close(self) -> None:
        self.closed = True
