# EpitomeJob

**AI career assistant that finds matching jobs, explains *why* each matches, tailors your
resume for that job, preps you for the interview, tracks applications — and never applies
or writes anything without your approval.**

- **First wedge:** laid-off H-1B / visa-dependent tech workers (60-day clock, sponsorship
  filter, 60-day countdown plan). See `docs/EpitomeJob-Market-Research.md`.
- **Pricing:** $79–99 one-time 30-day sprint pass, $149 lifetime, $299–499 human-reviewed
  package. No generic $20–30/mo subscription.
- **Architecture:** Phase 5 strangler split is **done** on main. A Spring Cloud Gateway
  (`:8080`) fronts the services; the monolith is an Eureka client (internal `:8081`);
  MCP is split out (`:8001`); Resume Builder is its own service (`:8082`); the Python
  agent-brain (`:8000`, LangGraph + Temporal) does Coach/RAG. 14 modules (M1–M14),
  Supervisor + 6 specialist agents, approval gate (24h signed expiry, idempotent, audited).
- **Build order:** web UI first (Next.js 16 + React 19), then mobile (React Native / Expo).
  Web v1.0: Fri Jan 22, 2027. Mobile v1.0: Fri Feb 19, 2027.

## Repo layout

| Path | What it is |
|---|---|
| `backend/` | Spring Boot monolith (Java 21) — auth, profile, resume, jobs, matching, tracker, interviews, approvals, coach proxy, billing. Eureka client; internal `:8081` in compose, `:8080` standalone |
| `gateway-service/` | Spring Cloud Gateway — the only external entry point (`:8080`), JWT filter, Redis rate limit, Resilience4j |
| `eureka-server/` | Service registry (`:8761`) |
| `mcp-service/` | MCP server, the 7 coach tools (`:8001`) |
| `resume-builder-service/` | Resume Builder — templates, PDF/DOCX parse, OpenHTMLToPDF export (`:8082` internal) |
| `agent-brain/` | Python agent-brain — LangGraph Supervisor + 6 specialists, Temporal worker, RAG (pgvector) (`:8000`) |
| `web/` | Next.js 16 web app (`:3000`), BFF proxy to the gateway |
| `libs/` | Shared libs (`epitomejob-common`) |
| `docker-compose.yml` | Full 18-service stack, run from the repo root |
| `k8s/` + `kind/` | Kubernetes manifests + local kind deploy |
| `docs/` | Execution plan v3 (current), UI-first plan v2, best execution plan, system design, plan validation, LLD Bible v2 (HTML), market research report |
| `.github/workflows/` | `ci.yml` (PR/push gate), `nightly.yml` (full suite, daily), `quester.yml` (per-commit test runs + triage), `quester-perf.yml` (nightly perf) |
| `qa-reports/` | Daily Quester QA reports |

## Run it — Mac and Windows

Last verified 2026-11-14 against main. The full stack is 18 services; the
recommended path is Docker Compose from the repo root. Per-service dev runs
are below for when you are working on one piece.

### Prerequisites

| Tool | Mac | Windows |
|---|---|---|
| Java 21 | Temurin 21 in `~/tools`; run `source ~/.zshrc` first in a fresh terminal (machine default Java is 25 — a trap) | Temurin 21 MSI from Adoptium; set `JAVA_HOME` to it and check `java -version` shows 21 |
| Maven 3.9+ | In `~/tools` (via `.zshrc`) | Chocolatey `choco install maven`, or the Maven zip + `PATH` |
| Docker Desktop | Mac app; give it at least 6 CPU / 8 GB (Elasticsearch + the Java services need it) | Docker Desktop with the WSL 2 backend, Linux containers |
| Node.js | 26 (`pnpm` via npm) | 22 LTS — the version CI and the web image use |
| pnpm 10 | `npm install -g pnpm@10` (Node 25+ has no corepack) | Same: `npm install -g pnpm@10` |
| Python 3.12 | python.org 3.12.10 (system python3 is 3.9 — never use it for agent-brain) | python.org 3.12 installer, tick "Add python.exe to PATH" |
| Ollama | `ollama pull llama3.1:8b` and `ollama pull nomic-embed-text`; runs on the host, containers reach it at `host.docker.internal:11434` | Ollama for Windows; same two pulls; same host address |
| Git | — | Git for Windows (use **Git Bash** for the commands below). Before cloning: `git config --global core.autocrlf false` — it protects the `.sh` scripts and line endings |
| kind + kubectl | Optional, for the local k8s deploy (`k8s/deploy-kind.sh`) | Optional: `kind-windows-amd64.exe` + kubectl; the deploy script needs Git Bash |

### 1. Clone and env

```bash
git clone https://github.com/TechBalachandar/epitomejob.git
cd epitomejob
cp .env.example .env   # set APP_JWT_SECRET to a long random value before any shared use
```

### 2. Full stack (recommended)

```bash
# If the old backend-only stack is running, stop it FIRST — the two stacks
# both publish :5432 and :9000 and must never run at the same time.
cd backend && docker compose down
cd ..

docker compose up -d --build   # 18 services, from the repo root
docker compose ps              # wait until every service is healthy
```

First run only, when the data volumes already exist (the init script below
only runs on a fresh volume): create the Temporal databases while the
backend stack is up:

```bash
docker compose -f backend/docker-compose.yml exec postgres psql \
  -U epitomejob -d epitomejob \
  -c "CREATE DATABASE temporal;" -c "CREATE DATABASE temporal_visibility;"
```

(On a fresh clone the init script in `docker/postgres/init-temporal-dbs.sql`
does this for you. The "already exists" error on a second run is harmless.)

Never run `docker compose down -v` on the root stack — it deletes the
Kafka and Elasticsearch data. Plain `docker compose down` keeps everything.

### 3. Check it works

```bash
curl http://localhost:8080/actuator/health   # gateway → {"status":"UP"}
curl http://localhost:8000/health           # agent-brain → {"status":"ok", ...}
```

Then open http://localhost:3000/login and sign in with the dev seed account
`dev@example.com` / `DevPass123!` (dev-only — change it before any shared use).
Registering a new user works too; use a fresh unique email each run.

Browser gate (also in `INSTALL-W1.md`): register → dashboard; log out and back
in as the seed user; edit the profile headline → completeness score updates;
upload a real PDF resume → status READY (a non-PDF is rejected); Matches →
Apply → approve it in the Approvals inbox → the tracker entry moves to APPLIED;
log out → a protected page redirects to `/login`.

### Ports

| Service | URL |
|---|---|
| Web app | http://localhost:3000 |
| Gateway (only external API entry) | http://localhost:8080 |
| Backend | Internal `:8081` in compose; `:8080` when run standalone (gateway stopped) |
| Resume Builder | Internal `:8082` |
| Agent-brain | http://localhost:8000 |
| MCP server | `:8001` (TCP; `/sse` needs a Bearer token) |
| Eureka | http://localhost:8761 |
| Temporal UI | http://localhost:8233 |
| Jaeger UI | http://localhost:16686 |
| Kibana | http://localhost:5601 |
| Postgres | `:5432` · RustFS `:9000`, console `:9001` · Redis `:6379` · Kafka `:9092` (in-network only) · Elasticsearch `:9200` |

API docs (standalone backend only): http://localhost:8080/swagger-ui.html.

### Dev mode — one service at a time

```bash
# Backend alone (gateway stopped, or :8080 is taken):
docker compose stop gateway
cd backend && mvn spring-boot:run        # http://localhost:8080

# Web alone (stop the compose web service first, or :3000 is taken):
docker compose stop web
cd web && pnpm install && pnpm dev      # http://localhost:3000
```

The agent-brain, MCP server, and Temporal worker run in compose; Ollama
stays on the host. Bring a stopped service back with
`docker compose up -d <service>`.

### Troubleshooting

- **Port already in use** — the other stack is running. `cd backend &&
  docker compose down`, then start the root stack. Never run both.
- **Gateway unhealthy / backend not registering** — check
  `docker compose logs -f gateway eureka backend`, and Eureka at
  http://localhost:8761 (the backend appears under Applications).
- **Coach says the agent is unavailable** — Ollama is not running on the
  host, or the models are not pulled. `ollama list` must show
  `llama3.1:8b` and `nomic-embed-text`.
- **A `gh` / triage warning in a Quester run** — reporting only; it never
  fails the run. The suites are the verdict.
- **Full page reload returns to `/login`** — known limit: the access token
  lives in memory only and the refresh cookie does not restore the session yet.

## Rules

- **Cake rule:** every phase ends with a working app; a phase is Done only when its gate passes.
- **Agents never approve each other.** Only the user approves a write.
- **Every feature is plug-and-play:** own package, own tables/migration, feature flag,
  own tests, rollback = flag off.
- **No live-interview copilot, no blind auto-apply — ever.**
- **Local Ollama is free for Dev/QA/PreProd.** Cloud models only for PROD, metered.
- **Billing is flag-off** (no Stripe account yet — enable with test keys in
  `.env` + `BILLING_ENABLED=true`). **Live job feeds need an API key**
  (Adzuna is a stub; JobsPipe / JSearch) — no scraping, ever.
