# EpitomeJob agent-brain — W4 (contract §2b).
# python:3.12-slim verified on Docker Hub 2026-11-12 (exists, 2026-11-08).
# curl is installed for the /health healthcheck.
# requirements.txt is copied from the W3 tree unchanged, including the
# cryptography==48.0.1 pin (newest version with a universal2 wheel for the
# user's Intel Mac; on linux/amd64 it resolves to a manylinux wheel).
# Default CMD runs the FastAPI app on :8000; compose overrides the command
# for mcp-server (python -m app.mcp.server, :8001) and temporal-worker
# (python -m app.temporal.worker, no HTTP port).

FROM python:3.12-slim
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY requirements.txt ./
RUN pip install --no-cache-dir -r requirements.txt
COPY app/ ./app/
EXPOSE 8000
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
