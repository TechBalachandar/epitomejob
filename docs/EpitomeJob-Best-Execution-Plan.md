# EpitomeJob — Best Execution Plan
## End-to-end, zero to production | CTO plan, corrected version
**Date:** 2026-11-08 | **Product:** EpitomeJob — AI Career Assistant | **Platforms:** Android (native, Kotlin) + Web (React + TS) | **Mobile v2:** React Native *replaces* native Android OR defers to post-launch — never maintain all three at once
**Rule for every phase (the cake rule, from SampAgent):** build like layers of cake, not rings of onion. At the end of every phase the app is fully working. A phase is NOT done until its gate passes. If a slice breaks the gate, fix it before adding more.

### The gate — every phase, no exceptions
- Builds clean from a fresh checkout
- App launches (Android emulator + Web)
- All previous-phase features still work
- Data survives a restart
- Approval flow works (where it exists)
- Version bumped (v0.1, v0.2, …)
- Exit demo recorded / shown, with a pass/fail checklist — copy the SampAgent Slice 1 discipline (15/15 Postman tests)

### Timeline — honest, solo developer, part-time
Phases 0–2: ~3–4 weeks | Phases 3–4: ~2–3 weeks | Phases 5–6: ~4–5 weeks | Phase 7: ~2 weeks
**Total: roughly 10–12 weeks to production on AKS.** Do not compress it by skipping gates.

---

## Phase 0 — Foundation + Base Cake (Weeks 1–2)
**Goal:** login, profile, resume upload — working end to end, with safety built in from Day 1.
**Why this order:** without a resume, nothing matches. Without approval, nothing is trusted.

Steps:
1. **Day 1 — versions first.** `libs.versions.toml` (Android: AGP, Kotlin, KSP, Hilt, Retrofit, OkHttp, Room, Navigation — all pinned, none hardcoded) + Spring Boot BOM + Python LangGraph pinned versions. *Lesson from SampAgent: KSP 2.2.10-1.0.31 did not exist, Hilt 2.52 did not support AGP 9 — versions assembled, never compiled, cost days.*
2. **Backend — Spring Boot modular monolith.** PostgreSQL + Flyway, MinIO (S3-compatible, local — real S3/Blob only in cloud), JWT auth, roles USER / COACH / ADMIN, PII redaction in logs, data-retention + delete-my-data from the start.
3. **Observability Day 1.** OpenTelemetry traces, structured logs with trace id, one Grafana dashboard. Not Phase 6.
4. **Core domain:** Profile CRUD, resume upload + metadata, parsed skills (basic, no AI yet).
5. **Approval gate — core, not polish.** Draft → approval card → Approve/Reject → runs → audit log. Signed approval token + idempotency key, so Approve twice cannot apply twice. 24h expiry.
6. **Android first client:** Jetpack Compose, MVVM, Hilt, Retrofit — login, view profile, upload resume PDF.
7. **Web companion:** React + TS dashboard mirroring profile + upload, same REST endpoints.

**Exit demo + gate:** `docker compose up` → log in on Android *and* Web → upload a resume → profile persists after restart → create a draft → approval card appears → Approve → audit log shows it. Job Discovery is NOT in Phase 0 — it is Phase 0.5, it can wait.

## Phase 1 — Polish & Architecture (Week 3)
**Goal:** same features, product-grade polish, offline resilience. No new features.
1. Clean architecture: Repositories behind ViewModels + UseCases on Android; same separation on Web. UI behaviour unchanged.
2. Room cache on Android (offline-first profile/chat history) + localStorage on Web.
3. Design system: Material 3 tokens on Android, Tailwind/MUI equivalent on Web (avatars, timestamps, dark theme).
4. Approval inbox screen + SSE streaming for live updates.
5. **NOT in this phase:** R8/ProGuard — that moves to Phase 6, pre-launch.

**Exit demo + gate:** identical feature set, works offline, zero UI-to-network coupling, all Phase 0 gates still pass.

## Phase 2 — The Brain: Multi-Agent Orchestration (Weeks 4–5)
**Goal:** one goal in plain English → Supervisor plans → specialists execute → human approves.
1. **Python LangGraph service skeleton** (Python 3.12+), internal authenticated REST/SSE to the Spring Boot backend. One contract, one source of truth — Java is the system of record, Python is the agent brain only.
2. **Single-task agent first:** resume-parsing *or* JD-normalisation through one isolated LangGraph node, core fallback path still active.
3. **Supervisor Graph:** Planner → Router → specialists (Resume, Job Discovery, Matching, Interview, Learning, Application) → check → final answer. Patterns: sequential (resume before matching), parallel (resume parse + job search), handoff with clean context, loop + retry once, then escalate to the user — never guess.
4. **Temporal:** durable workflows — a multi-step job, a pending approval, and a scheduled alert survive a restart. State lives in Postgres/Temporal, never in memory.
5. **Human-in-the-loop interrupts:** graph pauses on Job Application and Learning Plan commits until signed approval.

**Exit demo + gate:** from Android/Web, run a multi-step resume-tailoring job → Supervisor runs → pauses for approval → Approve → resumes and completes → previous features still work.

## Phase 3 — Knowledge: MCP & RAG (Week 6)
**Goal:** answers from your documents, with citations — not guesses.
1. pgvector in PostgreSQL; tenant-isolated embeddings.
2. Ingestion + chunking pipeline for resumes, notes, JDs, learning material.
3. MCP Server: tools `search_jobs`, `parse_resume`, `update_profile`, `match_score`, `ask_docs` — STREAMABLE, OAuth/JWT-validated per tool. Android never calls a tool directly.
4. Hybrid search (lexical + vector) + reranking + citations.
5. **Eval suite:** start with 20 questions, grow to 50 before Phase 6. A prompt/model change that drops the eval score does not ship.

**Exit demo + gate:** upload a complex PDF resume → ask it a question in plain English → answer arrives with direct source citations → eval suite passes.

## Phase 4 — Containers (Week 7)
**Goal:** the whole stack runs with one command, and survives a restart mid-flight.
1. Multi-stage Dockerfiles: Spring Boot backend + Python LangGraph service (small, cached layers).
2. `docker-compose.yml`: Postgres (pgvector) + MinIO + Ollama (one small model in dev) + Kafka + Redis + Spring Boot + Python AI. CPU/memory limits set — kind/Ollama on a 2019 Intel Mac needs them.
3. State-recovery test is the gate: start a multi-step workflow, restart all containers mid-flight, verify it resumes from Postgres/Temporal with no data loss.

**Exit demo + gate:** `docker compose up --build` → full agentic workflow on Android/Web → restart mid-flight → state recovered → all previous gates pass.

## Phase 5 — The Split: Microservices (Weeks 8–9)
**Goal:** strangler split, one service at a time. Never big-bang.
**Split order (do not change it):** 1) MCP Server (stateless, easiest) → 2) Notification → 3) Job Discovery + Matching Engine → 4) Resume/Profile → 5) Coach Agent + Interview + Learning → 6) RAG → 7) Billing & Analytics (separate — Notification and Billing are different bounded contexts).
Each split needs, before it ships: table-ownership map, data-migration/backfill plan, dual-write or cutover note, rollback plan, and its own gate.
Gateway: Spring Cloud Gateway, dynamic routing, service discovery, Resilience4j circuit breaker *per service* (not only at the gateway).

**Exit demo + gate:** all services running in Compose → intentionally crash and restart Notification (non-critical) → frontend stays live and fully operational → previous gates pass.

## Phase 6 — Cloud: Kubernetes, CI/CD, Launch Readiness (Weeks 10–11)
**Goal:** production on Azure, with a deploy that cannot ship a worse AI.
1. Local kind cluster first (Helm charts / manifests), then AKS + ACR + APIM + Key Vault.
2. **CI/CD (GitHub Actions):** build → JUnit + pytest → container security scan → **LangSmith eval gate (deploy fails if eval score drops)** → deploy on push.
3. **Launch readiness in this phase:** R8/ProGuard release smoke test, LLM cost limits + per-user token budget + cost dashboard (Ollama is free locally, Azure OpenAI is not), rate limiting, backup/restore drill.
4. Telemetry: OTel traces end to end, Grafana dashboards, alerts on match latency, approval backlog age, eval drop, payment failures.

**Exit demo + gate:** push a feature branch → CI runs tests + evals + scan → deploys to AKS automatically → live telemetry shows the request flowing → release build installs and runs.

## Phase 7 — Senses, Mobile v2 & Capstone (Week 12)
**Goal:** the showcase — voice, camera, and one mobile strategy.
1. **Mobile decision (pick one):** React Native *replaces* native Android as the mobile client, OR React Native defers to post-launch. Do not maintain Android native + Web React + React Native.
2. Camera + vision: photograph a paper resume/certificate → vision extraction → into the approval workflow (never auto-applied).
3. Voice: STT for prompts, TTS for coach feedback.
4. **Multi-environment AI routing:** Dev/QA/PreProd run entirely free on local Ollama; PROD routes to low-cost cloud inference with token budgets.
5. Capstone demo: one scripted journey across Web + mobile, voice/camera in, LangGraph agents, approval gate, live on AKS.

**Exit demo + gate:** full showcase runs on production cloud → voice + camera work → approval gate holds → all previous gates pass → **v1.0 launch.**

---

## What this plan deliberately does NOT do
- No microservices on Day 1. No React Native alongside native Android. No R8 in Phase 1. No approval gate as "polish". No AI deploy without an eval gate. No state in memory that a restart can lose.

## First step — this week
Phase 0, Day 1 only: pin versions, `docker compose up` with Postgres + MinIO, JWT login working on Android. Send me the build result (pass, or the error tail only) and we take Phase 0, Step 2 next.
