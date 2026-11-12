"""Temporal worker entrypoint (contract §7).

Run from ``agent-brain/``:
    python -m app.temporal.worker
Connects to the Temporal dev server (``TEMPORAL_ADDRESS``, default
``localhost:7233``) and serves ``CoachWorkflow`` on task queue ``coach-queue``.
"""

from __future__ import annotations

import asyncio
import logging

from temporalio.client import Client
from temporalio.worker import Worker

from app.config import settings
from app.temporal.activities import index_resume_if_needed, run_supervisor_graph
from app.temporal.workflows import CoachWorkflow

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)


async def main() -> None:
    client = await Client.connect(settings.temporal_address)
    worker = Worker(
        client,
        task_queue="coach-queue",
        workflows=[CoachWorkflow],
        activities=[run_supervisor_graph, index_resume_if_needed],
    )
    logger.info("Temporal worker listening on task queue 'coach-queue' (%s)", settings.temporal_address)
    await worker.run()


if __name__ == "__main__":
    asyncio.run(main())
