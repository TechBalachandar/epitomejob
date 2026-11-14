# EpitomeJob mcp-service — Phase 5B (MCP split)

Standalone FastMCP SSE server exposing the 7 Coach tools on `:8001/sse`,
split out of `agent-brain` (base: live repo `TechBalachandar/epitomejob`
@ main `b59dc45`). No code is shared with agent-brain: the small helpers
the tools need are deliberately duplicated verbatim — see `MOVES.md`
for the move / stay / duplicate decision on every module.

## Run (local, Mac)

```bash
cd mcp-service
python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
python -m app.mcp.server   # FastMCP SSE server on :8001/sse
```

Point the MCP Inspector at `http://localhost:8001/sse` with an
`Authorization: Bearer <token>` header (Inspector → Configuration →
Request Headers). The token is validated once per connection via Java
`GET /profiles/me` and cached for 15 minutes; without a valid token the
connection is rejected with 401.

## Run (Docker)

```bash
docker build -t epitomejob/mcp-service:5b .
docker run -p 8001:8001 \
  -e JAVA_BASE_URL=http://backend:8080 \
  -e DATABASE_URL=postgresql://epitomejob:<password>@postgres:5432/epitomejob \
  -e OLLAMA_BASE_URL=http://host.docker.internal:11434 \
  epitomejob/mcp-service:5b
```

Healthcheck (same as the old `mcp-server` compose service — there is no
unauthenticated HTTP health path, `/sse` needs a valid Bearer token):

```yaml
healthcheck:
  test: ["CMD", "python", "-c", "import socket; socket.create_connection(('localhost', 8001), timeout=5)"]
```

## The 7 tools

| Tool | What it does |
|---|---|
| `search_jobs` | Java `GET /jobs` (limit 1–20, default 10), trimmed to the contract shape |
| `parse_resume` | Java `GET /resumes/{id}/text`, first 4000 chars + counts |
| `match_score` | Java `GET /matches/{jobId}`, returned verbatim |
| `ask_docs` | RAG hybrid vector + lexical query (RRF), topK 1–10 |
| `update_profile` | **Proposes** Java approval `UPDATE_PROFILE` — never writes directly |
| `draft_application` | LLM draft grounded in resume + job + profile, **proposed** as approval `DRAFT_APPLICATION` |
| `apply_to_job` | **Proposes** Java approval `APPLY_JOB` — never applies directly |

Write rule: the three write tools only ever call `POST /approvals`
(propose). Only the user approves. No blind auto-apply, ever.

## Environment

Same names as agent-brain's settings (pydantic-settings, `.env`
supported, `extra="ignore"`). Localhost defaults, so it runs with no
env file on the Mac:

| Variable | Default |
|---|---|
| `MCP_PORT` | `8001` |
| `JAVA_BASE_URL` | `http://localhost:8080` |
| `DATABASE_URL` | `postgresql://epitomejob:epitomejob-dev@localhost:5432/epitomejob` |
| `OLLAMA_BASE_URL` | `http://localhost:11434` |
| `OLLAMA_CHAT_MODEL` | `llama3.1:8b` |
| `OLLAMA_EMBED_MODEL` | `nomic-embed-text` |
| `OLLAMA_TIMEOUT_S` | `120.0` |

## Tests

```bash
pytest tests/ -v   # smoke tests: 7 tools registered, /sse 401 without token
```

Live verification (valid token → SSE stream, tool calls against Java +
Ollama + Postgres) is a Mac/compose job — it cannot run on a bare VM
with no Java backend. See the Phase 5B report for what was verified
here vs what the Mac run must prove.
