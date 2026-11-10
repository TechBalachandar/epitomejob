# EpitomeJob — System Design
## AI Career Assistant | CTO / Solution Architect View
**Version:** 1.0 — High-Level Design (HLD) | **Date:** 2026-11-07 | **Stack:** Spring Boot microservices, Spring AI + LangGraph, MCP, RAG, Temporal, Azure
**Status:** Design only — no code in this document. LLD (classes, API contracts, table schemas, sequences) is the next step, not this document.

---

## 1. What EpitomeJob is — in one paragraph
EpitomeJob is an AI career assistant for job seekers. A user uploads a resume, tells it their goals, and it does the work a good career coach does: it finds matching jobs, explains *why* each job matches, rewrites the resume for that job, prepares the user for interviews, tracks every application, and builds a learning plan to close skill gaps. It never applies, messages a recruiter, or changes a resume without the user approving it first.

## 2. Scope — what this design covers, and what it leaves to LLD

| This HLD covers | Left to LLD (next document) |
|---|---|
| Product scope, users, and core journeys | Exact REST request/response JSON per endpoint |
| 14 microservices, their responsibilities and modules | Class diagrams, package structure per service |
| How services talk (sync vs async), and why | Exact Kafka topics, event schemas, retry policies |
| Data per service, and the RAG / vector strategy | Exact Postgres table schemas, indexes, migrations |
| Security, approval gate, and audit model | Exact JWT claims, RBAC matrix per endpoint |
| Scale, availability, reliability, observability | SLO numbers per service, alert thresholds |
| Deployment: local → Kubernetes → Azure | Helm charts, Terraform, CI/CD pipeline YAML |
| Build order (what to build first, and why) | Sprint tickets |

**Non-goals for v1:** real recruiter payments, live job-board scraping at scale (start with 2–3 aggregator APIs + manual import), video interviews, mobile offline mode, multi-language UI.

## 3. Users and core journeys

| User | Goal |
|---|---|
| Job seeker (primary) | Find the right jobs, get hired faster, know what to learn |
| Coach / mentor (secondary) | Review a seeker's plan, give feedback |
| Recruiter (portal, v2) | Post jobs, see matched candidates (with consent) |
| Admin | Manage plans, content, abuse, and audits |

**Journey A — Get matched (the 5-minute wow):**
Upload resume → parse + embed → Matching Engine ranks jobs + explains each → Coach builds a 30-day plan → user saves 3 jobs.

**Journey B — Apply with approval:**
Pick a job → Coach rewrites resume bullets for that JD → user reviews diff → approves → Tracker creates the application, Notification schedules follow-up → audit log records who approved what, when.

**Journey C — Interview prep:**
Pick a saved job → Interview Prep runs a mock (voice/text) → scores answers against a rubric → RAG pulls the company's interview questions → feedback report + 3 things to practise.

**Journey D — Close the gap:**
Learning Service compares resume skills vs target JDs → skill-gap report → 30-60-90 roadmap with courses + quizzes → progress tracked weekly.

## 4. Architecture — the figure
See the two images delivered with this design:
- `epitomejob-services-diagram.png` — clients → edge → 12 core services → AI/orchestration → data → platform.
- `epitomejob-modules-diagram.png` — the 5 modules inside each of the 14 services.

**Reading the figure top to bottom (one request flows down):**
Android (Kotlin/Compose, MVVM) + Web + Recruiter Portal → Spring Cloud Gateway (routing, rate limit) + Identity Service (OAuth2/OIDC, JWT, Entra ID, MFA) → core Spring Boot services (one Postgres DB per service) → Spring AI + LangGraph (agent brain) + Temporal (durable workflows) + Kafka/Redis (events, cache) → Postgres + pgvector + Blob + Azure AI Search → Docker/kind locally, AKS in Azure, observed with OTel/Grafana/LangSmith.

**The safety control, in the middle of the flow:** no AI write — no application, no message, no resume change — runs without a human approval. The Coach Agent proposes, the user taps Approve/Reject on their phone, the Approval record is idempotent and expires in 24h, and the action is audit-logged on Kafka.

## 5. Services — responsibility and owner of data

| # | Service | Owns | Must never own |
|---|---|---|---|
| 1 | API Gateway | Routing, rate limit, JWT check | Business logic |
| 2 | Identity Service | Users, credentials, roles, tokens | Resumes, jobs |
| 3 | Profile Service | Profile, skills, goals, preferences | Resume files |
| 4 | Resume Service | Resume files, versions, parsed skills | Job data |
| 5 | Job Discovery | Jobs, dedup, search index, alerts | User applications |
| 6 | Matching Engine | Embeddings, scores, rankings, explanations | Resume files |
| 7 | Coach Agent Service | Plans, memory, tool calls, approvals | Billing |
| 8 | Interview Prep | Mocks, rubrics, scores, feedback | Applications |
| 9 | Application Tracker | Applications, kanban, deadlines, outreach drafts | Payments |
| 10 | Learning Service | Skill gaps, roadmaps, courses, progress | Jobs |
| 11 | MCP Server | Tools, resources, prompts (STREAMABLE) | User data |
| 12 | RAG Service | Ingestion, chunking, embeddings, search, citations | Business rules |
| 13 | Notification Service | Email/push/SMS, templates, delivery log | Applications |
| 14 | Billing & Analytics | Plans, Stripe, usage, evals, audit, metrics | Resume content |

**Rule:** one service, one database. Services talk over REST (sync, user-facing) or Kafka events (async, side effects). No service reads another service's database directly.

## 6. Key APIs (HLD level — names only, contracts in LLD)
- `POST /resumes` — upload, parse, embed (async: returns job id, poll status)
- `GET /matches?userId=` — ranked jobs with score + explain-why
- `POST /coach/plan` — 30-day plan from profile + target roles
- `POST /coach/apply` — propose an application (creates a pending approval, does not send)
- `POST /approvals/{id}/approve | /reject` — human gate
- `POST /interviews/mock` — start a mock; `GET /interviews/{id}/feedback`
- `GET /learning/gap` — skill-gap report; `GET /learning/roadmap`
- `GET /tracker` — kanban; `POST /tracker/{id}/follow-up`
- MCP: `tools/list`, `tools/call` (resumeSearch, jobSearch, matchScore, askDocs, createApplication-draft) — all via the MCP Server, never directly from the client.

## 7. Data design
- **Postgres per service** — the source of truth for that service only.
- **pgvector** — resume + job embeddings (Matching Engine and RAG Service read it; they do not share tables).
- **Blob Storage** — resume PDFs/DOCX, interview audio, course docs.
- **Azure AI Search** — managed search option for jobs/docs; pgvector is the local-first default.
- **Redis** — cache for match results and session data; rate-limit counters.
- **Kafka** — `application.approved`, `resume.parsed`, `match.computed`, `audit.*` — with the outbox pattern so an event is never lost if a service crashes mid-write.
- **PII rule:** resume text and contact details are redacted in logs and audit trails; only the owning service stores raw PII.

## 8. The AI system — agents, MCP, RAG
- **Coach Agent (supervisor):** Spring AI + LangGraph. It plans, calls tools, checks results, and hands the user a final answer. It does not approve its own work.
- **Specialist agents:** Matching, Interview, Learning, and Notification agents — each with its own tools and its own RAG source, so one agent's mistake does not poison the others.
- **MCP:** the universal plug. Every tool the agents use is exposed through the MCP Server (`@McpTool`, STREAMABLE). Add a new tool once, every agent can use it. The Android app never calls a tool directly.
- **RAG:** answer from documents first, then generate. Resume/JD matching, interview questions, and course content are all retrieved, reranked, and cited — the user sees *where* an answer came from, not just the answer.
- **Models:** Ollama local by default (free, private, for development); Azure OpenAI as the opt-in cloud model. Swapping models is config, not code.
- **Evals:** LangSmith — a fixed set of 20–50 test resumes/JDs with expected matches, scores, and interview feedback. No model or prompt change ships without the eval suite passing.

## 9. Security and trust
- OAuth2/OIDC + Entra ID, JWT (short-lived, refresh flow), MFA optional, roles USER / COACH / ADMIN.
- Approval gate on every consequential write; 24h expiry; idempotent approvals (tapping twice does not apply twice).
- PII redaction in logs, audit, and evals; encrypted token storage on Android; cleartext forbidden in release; `allowBackup=false`.
- Rate limiting at the gateway; circuit breaker + bulkhead (Resilience4j) so one slow AI call cannot take down the app.
- No work/sensitive data leaves the local machine in dev; cloud only when proven.

## 10. Scale, availability, reliability
- Stateless services → scale horizontally on Kubernetes (HPA on CPU + queue depth).
- Matching and RAG are the hot paths — cache match results in Redis, pre-compute embeddings on resume upload, not on search.
- Temporal makes long workflows durable: a 30-day plan, a scheduled job alert, or a pending approval survives a restart.
- Async work (parsing, embedding, notifications) is never in the user-facing request path — it runs on Kafka, with retries and a dead-letter queue.
- **v1 targets (honest, not marketing):** 1,000 users, 10k resumes, 100k jobs indexed, p95 match < 2s (cached) / < 8s (cold). Scale numbers are revisited after v1 traffic, not guessed now.

## 11. Observability
OpenTelemetry traces (one trace id from phone → gateway → agent → MCP → DB), Micrometer metrics + Grafana dashboards, structured logs with trace id, LangSmith for AI evals and prompt traces. Alert on: match latency, approval backlog age, eval score drop, and payment failures.

## 12. Deployment
**Local (free):** Docker Compose + kind — Postgres, pgvector, Redis, Kafka, Ollama, all services.
**CI/CD:** GitHub Actions — build → unit tests → agent evals → Docker image → deploy.
**Cloud:** AKS + ACR, Azure APIM in front of the gateway, Key Vault for secrets, Azure AI Search + Blob. AWS swap table (EKS/ECR, API Gateway, Cognito, Secrets Manager, OpenSearch, S3) is a documented alternative, not a second build.
**Strangler rule:** build one service at a time, keep the monolith-style v1 running until the new service proves itself. Never a big-bang migration.

## 13. Design decisions and trade-offs

| Decision | Why | Cost / risk |
|---|---|---|
| Microservices, not monolith | Teams can ship independently; AI services scale separately | More ops, more failure modes — mitigated by gateway + Temporal |
| Spring Boot for services, LangGraph for the agent brain | Your Java strength + the industry-standard agent graph | Two runtimes to operate; keep the Python surface small (agent graph only) |
| MCP for all tools | One plug, reusable by any agent/client | Newer standard — pin versions, test the handshake |
| RAG + citations, not fine-tuning | Cheaper, fresher, explainable | Retrieval quality is the bottleneck — invest in chunking + rerank |
| Approval gate on writes | Trust + safety; the product's differentiator | Extra tap for the user — keep it to consequential actions only |
| Local-first Ollama, cloud opt-in | Free, private, fast to iterate | Local models are weaker — evals must catch that gap |
| Postgres per service | Clean ownership, independent scaling | Cross-service queries need events/APIs, not joins |

## 14. HLD vs LLD — what comes next
HLD (this document) answers *what* the system is and *why* it is shaped this way. LLD answers *how* each service is built: classes, API JSON, table schemas, sequences, and test cases. Do not start LLD for all 14 services at once — do it per service, in build order, as that service is built.

## 15. Build order — CTO recommendation
1. **Identity + Profile + Resume** (the foundation — without a resume, nothing matches)
2. **Job Discovery + Matching Engine** (the wow — first ranked matches)
3. **Coach Agent + Approval gate** (the trust — plans + human control)
4. **Application Tracker + Notification** (the outcome — actually applying)
5. **Interview Prep + Learning** (the retention — get hired, close gaps)
6. **MCP Server + RAG hardening** (the platform — reusable tools, cited answers)
7. **Billing + Analytics + Azure deployment** (the business — pay, measure, scale)

**First milestone (end of Week 1):** a user can upload a resume, see 5 ranked jobs with explanations, and save one. Everything after that is depth, not a new system.
