# EpitomeJob mcp-service — Phase 5B (MCP split).
# Mirrors agent-brain/Dockerfile @ b59dc45: python:3.12-slim, curl for
# debugging/health probes, same cryptography==48.0.1 pin (newest version
# with a universal2 wheel for the user's Intel Mac; on linux/amd64 it
# resolves to a manylinux wheel).
# The compose healthcheck is a TCP connect via python (no unauthenticated
# HTTP health path exists: /sse requires a valid Bearer token, 401 without
# one — same as the old mcp-server service in docker-compose.yml).
# Default CMD runs the FastMCP SSE server on :8001.

FROM python:3.12-slim
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY requirements.txt ./
RUN pip install --no-cache-dir -r requirements.txt
COPY app/ ./app/
EXPOSE 8001
CMD ["python", "-m", "app.mcp.server"]
