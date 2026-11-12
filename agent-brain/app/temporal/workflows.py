"""Temporal workflow (contract §7).

``CoachWorkflow`` takes ``{message, userId, token}`` and returns the
``POST /chat`` response object. Default retry policy (max 3 attempts).
Workflow id: ``coach-<userId>-<uuid4>``, task queue ``coach-queue``.
Approval-wait-via-signal is NOT in W3: the workflow completes after
proposing an approval.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import timedelta

from temporalio import workflow
from temporalio.common import RetryPolicy

with workflow.unsafe.imports_passed_through():
    from app.temporal.activities import index_resume_if_needed, run_supervisor_graph


@dataclass
class CoachChatInput:
    message: str
    user_id: str
    token: str


@workflow.defn
class CoachWorkflow:
    @workflow.run
    async def run(self, input: CoachChatInput) -> dict[str, object]:
        retry = RetryPolicy(maximum_attempts=3)
        await workflow.execute_activity(
            index_resume_if_needed,
            {"user_id": input.user_id, "token": input.token},
            start_to_close_timeout=timedelta(seconds=60),
            retry_policy=retry,
        )
        result = await workflow.execute_activity(
            run_supervisor_graph,
            {"message": input.message, "user_id": input.user_id, "token": input.token},
            start_to_close_timeout=timedelta(seconds=120),
            retry_policy=retry,
        )
        return result  # type: ignore[no-any-return]
