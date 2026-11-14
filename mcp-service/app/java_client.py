# Phase 5B (MCP split): DELIBERATE DUPLICATE of agent-brain's app/java_client.py.
# agent-brain still needs the full client (/chat, specialists, RAG indexing); the tools need list/get/propose_approval.
# Kept verbatim so behaviour is identical on both sides of the split.
# There is intentionally no shared package: fix a bug here AND in
# agent-brain's copy until a shared-helper decision is revisited.

"""Typed async HTTP client for the Java backend REST API (contract §0/§3).

The agent-brain only ever READS from Java, except for one write path:
``POST /approvals`` (propose an approval — never approve, never apply,
never write a profile/tracker row directly). Every other write the agent
wants goes through that approval proposal; only the user approves.
"""

from __future__ import annotations

import json
import logging
from typing import TypedDict

import httpx

from app.auth import InvalidTokenError, JavaUnavailableError

logger = logging.getLogger(__name__)


class JavaClientError(Exception):
    """Java answered with a non-2xx status that is not an auth failure."""

    def __init__(self, status: int, message: str) -> None:
        super().__init__(message)
        self.status = status
        self.message = message


class Profile(TypedDict, total=False):
    userId: str
    headline: str | None
    summary: str | None
    location: str | None
    workType: str | None
    salaryMin: int | None
    salaryMax: int | None


class JobSummary(TypedDict, total=False):
    id: str
    title: str
    company: str
    location: str
    workType: str
    salaryMin: int | None
    salaryMax: int | None
    currency: str | None
    sponsorsH1b: bool
    skills: list[str]
    summary: str
    role: str
    postedAt: str | None


class MatchResult(TypedDict, total=False):
    jobId: str
    score: float
    reasons: list[str]
    gaps: list[str]


class JavaClient:
    """Async client bound to one user's Bearer token."""

    def __init__(
        self,
        base_url: str,
        token: str,
        client: httpx.AsyncClient | None = None,
    ) -> None:
        self.base_url = base_url.rstrip("/")
        self.token = token
        self._client = client

    def _get_client(self) -> httpx.AsyncClient:
        if self._client is None:
            # trust_env=False: Java is localhost; never route it via a proxy.
            self._client = httpx.AsyncClient(
                base_url=self.base_url, timeout=30.0, trust_env=False
            )
        return self._client

    def _headers(self) -> dict[str, str]:
        return {"Authorization": f"Bearer {self.token}"}

    async def _request(
        self, method: str, path: str, **kwargs: object
    ) -> httpx.Response:
        try:
            resp = await self._get_client().request(
                method, path, headers=self._headers(), **kwargs  # type: ignore[arg-type]
            )
        except (httpx.ConnectError, httpx.TimeoutException) as exc:
            raise JavaUnavailableError(f"could not reach Java: {exc}") from exc
        if resp.status_code == 401:
            raise InvalidTokenError("Java rejected the token (401)")
        return resp

    async def _get_json(self, path: str, **kwargs: object) -> dict[str, object] | list[object]:
        resp = await self._request("GET", path, **kwargs)
        if resp.status_code == 404:
            raise JavaClientError(404, f"Java resource not found: {path}")
        if resp.status_code >= 400:
            raise JavaClientError(resp.status_code, f"Java GET {path} -> {resp.status_code}")
        data = resp.json()
        if not isinstance(data, (dict, list)):
            raise JavaClientError(resp.status_code, f"Java GET {path} returned unexpected JSON")
        return data

    # -- profile ---------------------------------------------------------
    async def get_profile(self) -> Profile:
        data = await self._get_json("/profiles/me")
        assert isinstance(data, dict)
        return data  # type: ignore[return-value]

    # -- resumes ---------------------------------------------------------
    async def list_resumes(self) -> list[dict[str, object]]:
        data = await self._get_json("/resumes/me")
        if isinstance(data, dict):
            content = data.get("content", [])
            return content if isinstance(content, list) else []  # type: ignore[return-value]
        return data  # type: ignore[return-value]

    async def get_resume_text(self, resume_id: str) -> dict[str, object]:
        """W3 endpoint (contract §3): GET /resumes/{id}/text."""
        data = await self._get_json(f"/resumes/{resume_id}/text")
        assert isinstance(data, dict)
        return data

    # -- jobs ------------------------------------------------------------
    async def list_jobs(
        self,
        query: str | None = None,
        location: str | None = None,
        work_type: str | None = None,
        sponsors_h1b: bool | None = None,
        page: int = 0,
        size: int = 50,
    ) -> list[JobSummary]:
        params: dict[str, object] = {"page": page, "size": size}
        if query:
            params["query"] = query
        if location:
            params["location"] = location
        if work_type:
            params["workType"] = work_type
        if sponsors_h1b is not None:
            params["sponsorsH1b"] = str(sponsors_h1b).lower()
        data = await self._get_json("/jobs", params=params)
        if isinstance(data, dict):
            content = data.get("content", [])
            return content if isinstance(content, list) else []  # type: ignore[return-value]
        return data  # type: ignore[return-value]

    async def get_job(self, job_id: str) -> JobSummary:
        data = await self._get_json(f"/jobs/{job_id}")
        assert isinstance(data, dict)
        return data  # type: ignore[return-value]

    # -- matches / tracker / interviews ----------------------------------
    async def get_match(self, job_id: str) -> MatchResult:
        data = await self._get_json(f"/matches/{job_id}")
        assert isinstance(data, dict)
        return data  # type: ignore[return-value]

    async def list_matches(self) -> list[dict[str, object]]:
        data = await self._get_json("/matches/me")
        if isinstance(data, dict):
            content = data.get("content", [])
            return content if isinstance(content, list) else []  # type: ignore[return-value]
        return data  # type: ignore[return-value]

    async def get_tracker(self) -> list[dict[str, object]]:
        data = await self._get_json("/tracker/me")
        if isinstance(data, dict):
            content = data.get("content", [])
            return content if isinstance(content, list) else []  # type: ignore[return-value]
        return data  # type: ignore[return-value]

    async def get_interview_questions(self, job_id: str | None = None) -> list[dict[str, object]]:
        params = {"jobId": job_id} if job_id else None
        data = await self._get_json("/interviews/questions", params=params)
        if isinstance(data, dict):
            content = data.get("content", [])
            return content if isinstance(content, list) else []  # type: ignore[return-value]
        return data  # type: ignore[return-value]

    async def list_interview_sessions(self) -> list[dict[str, object]]:
        data = await self._get_json("/interviews/sessions/me")
        if isinstance(data, dict):
            content = data.get("content", [])
            return content if isinstance(content, list) else []  # type: ignore[return-value]
        return data  # type: ignore[return-value]

    # -- approvals: the ONLY write path (propose only, never approve) -----
    async def propose_approval(self, action: str, payload: dict[str, object]) -> dict[str, object]:
        """POST /approvals with the payload as a JSON string (contract §0)."""
        resp = await self._request(
            "POST",
            "/approvals",
            json={"action": action, "payload": json.dumps(payload)},
        )
        if resp.status_code not in (200, 201):
            raise JavaClientError(
                resp.status_code, f"Java POST /approvals -> {resp.status_code}: {resp.text[:300]}"
            )
        data = resp.json()
        assert isinstance(data, dict)
        return data

    async def aclose(self) -> None:
        if self._client is not None:
            await self._client.aclose()
            self._client = None
