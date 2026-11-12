# EpitomeJob W3 — agent-brain (M7 Coach Agent + 6 specialists, M11 MCP, M12 RAG, Temporal, evals)

Python agent-brain for EpitomeJob W3. Frozen contract: `../W3-API-CONTRACT.md`
(the contract wins over any assumption here). Local Ollama only — no cloud
LLM key, no LangSmith key, ever, for dev.

## Layout

| Path | What it is |
|---|---|
| `app/main.py` | FastAPI app: `GET /health`, `POST /chat`, `POST /rag/index`, `POST /rag/query` (§2) |
| `app/config.py` | Env settings with the §10 defaults |
| `app/llm.py` | Ollama client — **direct httpx** to `/api/chat` + `/api/embeddings` (no langchain-ollama: only two calls are needed, and a thin client keeps the deterministic fallback explicit — every method returns `None` on failure instead of raising) |
| `app/auth.py` | Bearer validation via Java `GET /profiles/me`; userId comes from the validated profile |
| `app/java_client.py` | Typed httpx client for the Java REST API; the ONLY write is `POST /approvals` (propose only) |
| `app/rag/chunking.py` | 512 chars / 50 overlap (§5) |
| `app/rag/store.py` | psycopg raw SQL; hybrid vector + tsvector retrieval fused with RRF k=60 (§5) |
| `app/rag/index.py` | Lazy idempotent indexing of seeded jobs + latest READY resume |
| `app/graph/` | `state.py`, `router.py` (LLM routing + §6 keyword fallback, first match wins), `supervisor.py` (LangGraph supervisor → specialist → respond), `specialists.py` (6 nodes) |
| `app/mcp/tools.py` | All 7 tools exactly per §4; write tools propose approvals only, never write directly |
| `app/mcp/server.py` | FastMCP SSE server on :8001, path `/sse`; Bearer validated once per connection (cached 15 min) |
| `app/temporal/` | `CoachWorkflow` (task queue `coach-queue`), activities, worker |
| `evals/` | 20 questions (§8) + pytest runner; judge = the 8 `needsCitations` questions |
| `tests/` | Pure-logic unit tests — run with no Ollama, Temporal or Postgres |

## Setup (Mac, Python 3.12)

Homebrew does **not** work on this Intel Mac — never route setup through brew.

```bash
# 1. Python 3.12 — download the macOS installer .pkg from python.org
#    (https://www.python.org/downloads/macos/) and run it, then:
python3 --version   # must be 3.12.x

# 2. From the epitomejob repo, in agent-brain/:
cd agent-brain
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
```

Prerequisites that must already be running (see the W3 install notes):
Ollama on :11434 (`ollama pull llama3.1:8b nomic-embed-text`), Java backend
on :8080, Postgres 16 + pgvector on :5432 (Flyway V4 creates `rag.documents`),
Temporal dev server on :7233 (below).

## Run (three terminals, all from `agent-brain/` with the venv active)

```bash
# Terminal 1 — Temporal dev server (single binary for Intel Mac):
temporal server start-dev
#   gRPC :7233, UI http://localhost:8233
#   (Docker `temporalio/auto-setup` is the fallback only.)

# Terminal 2 — agent-brain API on :8000:
uvicorn app.main:app --host 0.0.0.0 --port 8000

# Terminal 3 — FastMCP SSE server on :8001:
python -m app.mcp.server

# Terminal 4 — Temporal worker:
python -m app.temporal.worker
```

Env vars (all have localhost defaults, contract §10): `AGENT_BRAIN_PORT=8000`,
`MCP_PORT=8001`, `JAVA_BASE_URL=http://localhost:8080`,
`OLLAMA_BASE_URL=http://localhost:11434`, `OLLAMA_CHAT_MODEL=llama3.1:8b`,
`OLLAMA_EMBED_MODEL=nomic-embed-text`, `OLLAMA_TIMEOUT_S=120`,
`DATABASE_URL=postgresql://epitomejob:epitomejob-dev@localhost:5432/epitomejob`,
`TEMPORAL_ADDRESS=localhost:7233`, `TEMPORAL_ENABLED=true`,
`LANGSMITH_ENABLED=false`.

## Test & eval

```bash
pytest tests   # unit tests — green with no Ollama / Temporal / Postgres
pytest evals   # 20-question eval; judge tests skip cleanly when Ollama is down
               # summary ends with: judge: skipped (Ollama down)
```

## MCP Inspector

Point the Inspector at `http://localhost:8001/sse` and add a request header
`Authorization: Bearer <your user token>` (Inspector → Configuration →
Request Headers). Without a valid token the connection is rejected with 401.
Try: `search_jobs`, `match_score`, `ask_docs`, `parse_resume`,
`update_profile`, `draft_application`, `apply_to_job` — the last three only
ever return `{status: "proposed", approvalId}`; approve in the web inbox.

## Honest limits

- Nothing here has run end-to-end: this VM has no Ollama, Temporal,
  Postgres, Java or Docker. The Mac run is the proof.
- `/chat` falls back to in-process execution when Temporal is unreachable
  (contract §7) — check the logs for the warning if you expect Temporal.
- If Ollama is down, replies are rules-only and end with
  "Note: the local model was unreachable, so this answer used rules only."
- The interview specialist points at the W2 mock interview; it does not run
  a live interview in chat. Approval-wait-via-signal is not in W3.
