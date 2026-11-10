# EpitomeJob — Updated End-to-End Execution Plan v3

## UI-first, plug-and-play features, automated testing, daily QA agent | 2026-11-09

**Supersedes:** `EpitomeJob-Execution-Plan-UI-First.md` (v2, Nov 9, 2026) and the backend
`EpitomeJob-Best-Execution-Plan.md`. The backend architecture is unchanged — **modular monolith
first, microservices later via Phase 5 strangler split** — and the client order is unchanged
(web first, then mobile). What changes in v3: the **market wedge**, the **plug-and-play feature
contract**, the **testing + daily QA agent**, and the **project tracking tool**.

**The product in one line:** an AI career assistant that finds matching jobs, explains *why*
each matches, tailors your resume for that job, preps you for the interview, tracks applications,
and never applies or writes anything without your approval.

---

## 1. What changed in v3 (and why)

| # | Change | Why |
|---|---|---|
| 1 | **H-1B / visa-dependent tech workers are the first wedge**, not "US tech job seekers" in general | Market research, Nov 9, 2026: the job-seeker-paid slice is only ~$0.3–1.8B; freemium converts ~2–5% and success = churn in ~2 months. The H-1B wedge has a 60-day clock, six-figure stakes, and only one incumbent (Jobright) serving it — ~30% of its users are international workers |
| 2 | **Sprint pricing, not subscription-only** | $79–99 one-time 30-day sprint pass (hero product), $149 lifetime, $299–499 human-reviewed package. A $20–30/mo generic subscription competes head-on with Teal+ ($29), Careerflow ($24) and Simplify+ ($40) — the worst position in the market |
| 3 | **Every feature is plug-and-play** (see §4) | Each feature is a self-contained vertical slice with its own flag, routes, API, tables and tests. A feature can be switched on/off, added, or removed without touching other features |
| 4 | **Learning plans (M10) cut from v1** | Research verdict: learning content is a commodity and the weakest pillar. It returns only as the *second* wedge (career switchers), after the H-1B wedge is proven |
| 5 | **Automated testing + a daily QA agent** (see §7–8) | One agent runs the full test suite every morning and sends you a report. No more "does it still work?" — you get the answer daily, before you start coding |
| 6 | **GitHub Projects is the tracker** (see §9) | Free, lives next to the code, and automates board movement from PRs. Replaces the Excel tracker as the day-to-day tool (the Excel file stays as the forecast backup) |
| 7 | **No live-interview copilot, no blind auto-apply — ever** | Amazon bans GenAI in interviews; auto-apply is where trust dies (LazyApply ~2.4/5 on Trustpilot). Assisted bulk (draft + one-click approve each) is the most we ever do |

---

## 2. Ground rules (they do not change)

- **Cake rule:** every phase ends with a working app. A phase is not Done until its gate passes:
  builds clean from a fresh checkout, launches, previous features still work, data survives a
  restart, approvals work, version bumped.
- **Agents never approve each other.** Only you approve a write (apply, send, pay, publish).
  The approval gate is 24h signed expiry, idempotent (approve twice = one execution), audited.
- **Local Ollama is free for Dev/QA/PreProd** (llama3.1:8b). Cloud models only for PROD, and
  only where the cost is metered.
- **No feature lands without its tests.** A feature with no tests is not a feature — it is a
  demo. The daily QA agent enforces this (see §8).
- **One thing at a time.** Do not start the next phase until the current gate passes.

---

## 3. Tech stack (pinned — do not drift without a note in the tracker)

| Layer | Pick | Version |
|---|---|---|
| Web framework | Next.js (App Router) | 16.4.x |
| UI library | React | 19.3.1 |
| Language | TypeScript (strict mode) | 5.9.x |
| Styling | Tailwind CSS | 4.3.4 |
| Components | shadcn/ui (Radix + Tailwind) | latest |
| Data fetching | TanStack Query | 5.x |
| Client state | Zustand | 5.x |
| Forms | React Hook Form + Zod | latest |
| Web testing | Vitest + Playwright | latest |
| Package manager / runtime | pnpm + Node 22 LTS | Node 22 |
| Mock data (UI phase only) | MSW (Mock Service Worker) | latest |
| API contract | OpenAPI from the Spring Boot backend | — |
| Backend | Java 21, Spring Boot | 3.5.4 |
| Backend security | Spring Security 6, JJWT | 0.12.6 |
| Backend data | Postgres 16, Flyway, MinIO (S3-compatible) | 16 / 8.5.13 |
| Backend testing | JUnit 5, Testcontainers, RestAssured | — |
| Agent brain | Python, LangGraph, LangChain | 3.12+ |
| Agent orchestration | Temporal (pause/resume, retries) | latest |
| Vector search | Postgres pgvector (HNSW) | — |
| Async | Kafka + Redis | — |
| Observability | OpenTelemetry, Grafana, LangSmith (evals) | — |
| Containers / cloud | Docker Compose → kind → AKS, GitHub Actions CI | — |
| Mobile (Phase M) | React Native (Expo) | latest |
| Project tracking | GitHub Projects (free) | — |
| Feature flags | DB-backed flags table + env override | — |

**What NOT to use:** Create React App (dead), Tailwind v3, Redux (overkill), a separate Vite SPA,
KMP before verifying Xcode on your 2019 Intel Mac, Flutter (Dart is a third language).

---

## 4. Plug-and-play features — the contract every feature must follow

A feature is plug-and-play when it can be **added, switched off, or removed without breaking
anything else**. Every feature (M1–M14, plus the H-1B wedge features) ships as one vertical
slice that follows this contract:

| Contract rule | What it means in practice |
|---|---|
| **One package, one boundary** | Backend: one package per module (`com.epitomejob.<module>`), own schema/tables, own API. Web: one route group + one feature folder. No cross-module DB reads — modules talk through internal service interfaces and Kafka events only |
| **Feature flag** | Every feature has a flag (`features.<name>.enabled`, DB-backed, env override). Flag off = routes hidden, API returns 404, no data written. A feature can be killed in production without a redeploy |
| **Own migration** | Flyway migration(s) owned by the feature. Removing a feature = its migration is never run on a fresh install; existing data stays untouched |
| **Own tests** | Unit + API + at least one E2E journey test ship *with* the feature, in the same PR. No tests = the PR does not merge (CI enforces it) |
| **OpenAPI contract** | The web app only calls what the backend's OpenAPI says exists. The UI never invents a field the API does not return |
| **Approval boundary** | Any feature that writes on the user's behalf (apply, send, tailor-and-submit) goes through the Approval Gate (M7). No feature bypasses it |
| **Rollback = flag off** | If a feature breaks in production, the first response is flag-off, not a hotfix. The hotfix follows after |

**Plug-in order (the order features are added):** Identity → Profile → Resume → Jobs →
Matches → Tracker → Coach (Supervisor) → Interview Prep → Notifications → Billing (sprint pass)
→ MCP/RAG → Learning (only as second wedge) → Gateway hardening last (it fronts what exists).

**The H-1B wedge features (plug in during W2, before general matching):**

| Wedge feature | What it does | Plug-and-play note |
|---|---|---|
| Sponsorship filter | "Only show jobs from employers that sponsor H-1B" — built from H-1B disclosure data | A filter on the Jobs module; flag `features.sponsorship-filter.enabled` |
| 60-day countdown | Countdown plan from layoff date: day-by-day checklist, deadline warnings | A widget on the dashboard + tracker; own tables, flag-gated |
| Sponsor-aware matching | Match score includes sponsorship likelihood, not just skills | An extra signal inside the Matching module, not a new module |
| Referral / intro help | "Who do I know at this sponsor?" — LinkedIn-connection-aware prompts | Coach specialist prompt pack; no new data store in v1 |

---

## 5. End-to-end roadmap — every phase, every feature, every gate

| Phase | Dates | What gets built | Exit criteria (gate) |
|---|---|---|---|
| **W0 — Web UI foundation** | Nov 9–20, 2026 | App shell + design system; all 12 pages (`/login`, `/register`, `/`, `/profile`, `/resumes`, `/jobs`, `/matches`, `/coach`, `/approvals`, `/interviews`, `/tracker`, `/learning` — learning page is a placeholder linking out, per §1.4); auth UI (JWT in memory + refresh in httpOnly cookie); Coach chat + approval card UI; resume upload UI (PDF only); MSW mocks | Every page renders; no console errors; layout holds at 390px; mock login works; fake PDF upload shows READY/FAILED; approval card Approve removes it from the inbox; everything works with mocks only |
| **W1 — Connect UI to real backend** | Nov 23–27, 2026 | Run Phase 0 backend starter (Postgres + MinIO + JWT + Profile/Resume + approval + SSE); wire TanStack Query to `/auth/login`, `/profiles/me`, `/resumes`, `/approvals/pending`, `/approvals/stream`; delete MSW mocks endpoint by endpoint; error states (401 → login, 403 → "not allowed", 404 → empty state, 500 → "try again") | Phase 0 gate done through the browser: register a real user → upload a real PDF (appears in MinIO) → create an approval → approve it → audit trail shows it |
| **W2 — Core journeys + H-1B wedge** | Nov 30–Dec 11, 2026 | Job Discovery (M5) + Matching (M6) with "why this matches" + gaps; sponsorship filter + 60-day countdown; Tracker kanban (M9: SAVED → APPLIED → INTERVIEW → OFFER → REJECTED); Interview Prep mock (M8: 5 questions, rubric, feedback) | Journey A end to end in the browser: register → profile → upload resume → see 5 ranked jobs with explanations → sponsorship filter on → save one → mock interview for it |
| **W3 — Agent brain on web** | Dec 14, 2026–Jan 1, 2027 | Python LangGraph Supervisor + 6 specialists (Resume, Job Discovery, Matching, Interview, Learning*, Application); MCP Server (M11) + RAG (M12) with citations; Temporal pause/resume; eval suite 20 → 50 questions + LangSmith (a prompt change that drops the score does not ship) | Journey B in the browser: "Find me jobs and prepare me" → multi-step job pauses for approval → resumes after Approve → never executes a write without one |
| **W4 — Polish, containers, cloud** | Jan 4–22, 2027 | Full Docker Compose stack (backend + LangGraph + Postgres + MinIO + Kafka + Ollama); restart-mid-flight recovery test; kind → AKS; GitHub Actions CI with eval gate; billing: sprint pass checkout ($79–99) + lifetime ($149) | Push a branch → CI tests + evals pass → deploys to AKS. **Web v1.0 launches Fri Jan 22, 2027** |
| **Phase M — Mobile** | Jan 25–Feb 19, 2027 | React Native (Expo), one codebase Android + iOS: login, profile, resume upload + camera, Coach chat, approval inbox with push, matches, tracker. Mobile decision checkpoint Mon Jan 25 (KMP only if Xcode iOS build verified on your Mac) | Real Android + real iPhone → login → approval push arrives → Approve from phone → Tracker updates on web. **Mobile v1.0: Fri Feb 19, 2027** |
| **Phase 5 — Microservices split (later)** | After mobile v1.0 | Strangler split, one service at a time: MCP Server first → Notification → Job Discovery + Matching → rest, each with data-migration + rollback plan | Each split ships only when the monolith gate still passes with that service extracted |

\* Learning specialist exists in W3 only as a *curator* (links out to existing courses for a
job's missing skills). No course library is built.

---

## 6. Feature map — all 14 modules (M1–M14), plug-and-play status

| Module | What it does | Built in | Plug-and-play boundary | In v1? |
|---|---|---|---|---|
| M1 API Gateway | Single entry, routing, rate limits | W4 (hardening) | Fronts whatever modules are enabled; no business logic | Yes |
| M2 Identity & Auth | Register/login, JWT RS256, refresh rotation + reuse-revokes-all, lockout 5/15 min, Google OAuth2, forgot/reset | Phase 0 (done) + W1 | Own tables (`users`, `refresh_tokens`, `oauth_accounts`, `login_attempts`); BCrypt cost 12 | Yes |
| M3 Profile | CRUD + completeness score (headline/summary/location/work type/salary = 20 pts each) | Phase 0 (done) + W1 | Owner-only; own schema | Yes |
| M4 Resume | PDF-only upload to MinIO, PDFBox text parse, status READY/FAILED | Phase 0 (done) + W1 | Own bucket prefix + tables; DOCX deferred | Yes |
| M5 Job Discovery | Search + filters + saved searches; job data via JobsPipe/JSearch/Adzuna ($50–900/mo budget — see risks) | W2 | Job-data provider behind one interface, so the provider can be swapped | Yes |
| M6 Matching Engine | Ranked matches + "why this matches" + gaps, with citations to posting + resume lines | W2 | Scoring is a pure function of (resume, posting) — testable without the UI | Yes |
| M7 Coach Agent (Supervisor) | LangGraph Supervisor + 6 specialists; approval gate (24h signed expiry, idempotent, audited); SSE stream | W0 (UI) + W3 (brain) | The gate is the one choke point every write passes through | Yes |
| M8 Interview Prep | Mock interview (5 questions), rubric score, feedback report, grounded in the specific job + the user's actual resume | W2 (mock) + W3 (agent) | Question bank per role; no live-interview copilot | Yes |
| M9 Application Tracker | Kanban SAVED → APPLIED → INTERVIEW → OFFER → REJECTED, follow-up reminders, 60-day countdown widget | W2 | Own tables; countdown is a flag-gated widget on top | Yes |
| M10 Learning | Curated links for a job's missing skills (no course library) | Second wedge only | Links out; nothing to build in v1 except the curator prompt | **No (v1)** |
| M11 MCP Server | Tool catalog: `search_jobs`, `parse_resume`, `match_score`, `ask_docs`, `update_profile`, `draft_application`, `apply_to_job` (apply always needs approval) | W3 | Tools are versioned; a tool can be disabled without touching the agent | Yes |
| M12 RAG Service | Chunk 512/50 overlap → embed → pgvector HNSW → hybrid lexical+vector → rerank → citations | W3 | Retrieval is behind one interface; the embed model is swappable | Yes |
| M13 Notification | Email + push (approval ready, deadline warnings, interview reminders) | W3 (email) + M (push) | Event-driven (Kafka); a channel can be off without affecting the others | Yes |
| M14 Billing & Analytics | Sprint pass $79–99, lifetime $149, human-reviewed package $299–499 (manual fulfilment in v1); outcome analytics (interviews/offers — published, no competitor does this) | W4 (checkout) | Stripe (or equivalent) behind one interface; analytics reads events only, never writes | Yes (checkout in W4; package is manual) |


---

## 7. Testing — robust by design, not by hope

The test pyramid. Every layer runs in CI on every PR; the full suite runs nightly (see §8).

| Layer | What it tests | Tools | Where it runs | Target |
|---|---|---|---|---|
| **Unit** | One function/class: matching score math, JWT sign/parse, completeness score, approval expiry | Vitest (web), JUnit 5 (backend), pytest (agent) | Every PR | ≥80% coverage on business logic (matching, auth, approvals, billing); UI components: happy path + one error state each |
| **Integration** | API + real DB: register → profile → resume → approval → approve, with data surviving a restart | Testcontainers (Postgres + MinIO), Spring Boot Test | Every PR | All happy paths + 401/403/404/500 for every endpoint |
| **Contract** | Web and backend agree: OpenAPI schema tests — the UI never calls a field the API does not return | OpenAPI validator in CI; Pact (optional, Phase W3) | Every PR | 100% of endpoints covered by schema |
| **E2E (browser)** | Real user journeys in a real browser: Journey A (W2), Journey B (W3), login → upload → approve | Playwright (Chromium + mobile viewport 390px) | Every PR (smoke set); nightly (full set) | Every journey green; a journey that fails blocks the merge |
| **API / Postman** | The backend gate as curls, kept as a collection that runs in CI | Postman collection + Newman in CI | Nightly + on demand | Phase 0 gate (health → register → login → profile → resume → approval → approve) always green |
| **Agent evals** | The Coach gives good, grounded, non-hallucinated answers | Eval suite 20 → 50 questions + LangSmith; score per prompt version | Every agent PR + nightly | A prompt change that drops the eval score does not ship |
| **Accessibility** | Keyboard + screen-reader basics, colour contrast | Playwright + axe | Nightly | Zero critical axe violations on the 12 pages |
| **Performance** | Page load, API latency under load | Lighthouse CI (web), k6 (API, W4) | Nightly (W4 onward) | p95 API < 500ms on the core journeys; Lighthouse performance ≥ 80 |
| **Security** | Auth, injection, secrets, dependency vulns | OWASP ZAP (W4), Dependabot + `npm audit` / OWASP Dependency-Check, secret scan in CI | Every PR (deps/scan); nightly (ZAP, W4) | Zero high-severity vulns in production deps; no secrets in the repo |

**Rules:** a failing test is never "fixed" by deleting the test — fix the code or fix the test
with a note explaining why the expectation was wrong. Flaky tests get quarantined (moved to a
`flaky` tag, fixed within a week) — they never silently stay red. Test data is local/synthetic
only; no real user resumes in tests.

---

## 8. The daily QA agent — one agent runs all tests every morning and reports to you

This is the part you asked for. It is two pieces that work together: **automation that runs the
tests** (GitHub Actions, free for public repos and 2,000 minutes/month on the free plan for
private repos) and **an agent that reads the results and tells you what matters** (a scheduled
Muse job — set it up once, it runs daily).

### How it works

| Step | What happens | Tool |
|---|---|---|
| 1. **Nightly run — 6:00 AM ET, daily** | GitHub Actions cron runs the full suite: backend unit + integration (Testcontainers), web unit (Vitest), E2E (Playwright, full journey set), API collection (Newman), evals (LangSmith), axe, Lighthouse | GitHub Actions (free tier) |
| 2. **Results collected** | JUnit/Playwright reports + screenshots/video of failures are uploaded as run artifacts; a one-line summary (pass/fail counts per layer) is written to a `qa-reports/` file in the repo | GitHub Actions artifacts |
| 3. **QA agent triage — 7:00 AM ET, daily** | A scheduled Muse job opens the latest run, reads the failures, and works out: is it a real regression, a flaky test, or an environment problem (e.g. Ollama not up)? It does **not** change code on its own — it reports | Muse scheduled job (daily cron) |
| 4. **Report to you — 7:05 AM ET, in this chat** | You get a short morning report (format below). Green days are one line. Red days name the failing journey, the likely cause, and the fix to try first | Chat message |

### The daily report format

| Section | Content |
|---|---|
| **Verdict** | 🟢 All green (X tests, Y journeys) — or 🔴 Red: N failures |
| **Failures** | Each failure: journey/layer, what broke, first failing step, link to the run + failure screenshot |
| **Likely cause** | Regression (which PR/area) / flaky (how many times this week) / environment |
| **What to do first** | The single most useful next step (e.g. "re-run the W2 Journey A E2E locally — it failed at the approval step, which changed in yesterday's commit") |
| **Trend** | Pass rate this week vs last week; flaky-test count |

### What the QA agent does NOT do

- It does **not** auto-merge, auto-fix code, or auto-retry a red run into green. It reports;
  you (or a build session with you) decide the fix. Auto-fixing hides regressions.
- It does **not** run on your Mac — it runs in GitHub Actions, so your laptop being off or
  asleep changes nothing.
- It does **not** replace the per-PR checks. PRs still run the fast suite (unit + integration
  + E2E smoke) before merge; the nightly run is the deep suite.

### Setup (one-time, ~1 hour, during W0 Week 2)

1. Push the web + backend code to a GitHub repo (private is fine).
2. Add `.github/workflows/ci.yml` (PR checks) and `.github/workflows/nightly.yml` (6 AM ET cron,
   full suite + evals).
3. Create the daily 7 AM Muse scheduled job for the triage + report. (I can set this up for you
   once the repo exists — it needs the repo name and where the run reports live.)
4. First green nightly run = W0's final gate item. If the nightly run is not green by Nov 20,
   W0 is not done — the cake rule applies to the QA pipeline too.

**Cost:** $0. GitHub Actions free tier, Playwright/Vitest/JUnit/Testcontainers all free,
LangSmith has a free tier for evals. The only paid piece is LLM eval calls, kept tiny by
evaluating on the local Ollama model where possible.

---

## 9. Project tracking — which free tool, and my recommendation

| Tool | Free plan | Best for | Watch out | Verdict |
|---|---|---|---|---|
| **GitHub Projects — RECOMMENDED** | Free, unlimited projects (private repos included) | **You.** Issues live next to the code and the CI: an issue → branch → PR → auto-moves the card to Done when the PR merges. Milestones = phases (W0, W1, W2…), labels = modules (M1–M14) + wedge. Built-in automation (no extra tool), and the nightly QA run can open an issue automatically on failure | Only good if the code lives on GitHub (it should — see §8 setup). Reporting is basic vs Jira | **Use this.** One login, one place, code + tasks + CI together |
| **Trello** | Free: 10 boards, unlimited cards, 10MB attachments | Simplest Kanban; non-technical, fast to start | No code link (no PR/CI automation), no milestones/sprints on free, gets messy past ~50 cards | Runner-up if you want the simplest possible board and do not want GitHub |
| **Jira (free)** | Free up to 10 users, 2GB storage | Real Scrum: sprints, story points, burndown, enterprise Agile | Heavy setup and heavyweight process for a solo dev; separate from the code; you will spend more time *managing* Jira than building | Overkill for a solo founder. Revisit only if a team joins later |
| **ClickUp (free)** | Free forever, 100MB storage, unlimited tasks | All-in-one (docs + tasks + goals) | Noisy, lots of clicks, weak GitHub integration on free | Not recommended — too much surface for one person |
| **Linear** | Free up to 10 users (250 issues) | Fast, keyboard-first, loved by devs | 250-issue cap on free; no built-in CI link like GitHub; better for a team than a solo | Good tool, wrong fit while the code is on GitHub |

**My call: GitHub Projects.** Set it up like this:

- **One project, "EpitomeJob".** Columns: Backlog → Ready → In Progress → In Review → Done.
- **Milestones = phases:** W0, W1, W2, W3, W4, Phase M, Phase 5.
- **Labels:** one per module (M1–M14), plus `wedge-h1b`, `testing`, `qa-agent`, `docs`.
- **One issue per feature slice** (not per task) — e.g. "M6: matching with explanations",
  "Wedge: sponsorship filter", "QA: nightly workflow". Each issue carries its exit criteria
  from §5–6 in its description, so Done means the gate passed, not "I think it's done".
- **Automation:** PR linked to an issue moves it to In Review; merged PR moves it to Done.
  Nightly QA failure opens an issue labelled `qa-agent` automatically.
- **Friday ritual (15 min):** move cards, update % complete, and update the Excel tracker
  (`EpitomeJob-Project-Tracker.xlsx`) from the board — the Excel file becomes the forecast
  record, the board becomes the day-to-day truth.

---

## 10. Execution rhythm — how a week actually runs

| Day | What happens |
|---|---|
| **Monday** | Pick the week's issues from the board (Ready → In Progress). One feature at a time. Read the morning QA report first — a red board means fix first, build second |
| **Tue–Thu** | Build the feature slice (UI + API + tests in the same PR). Run the fast suite locally before pushing |
| **Friday** | Gate day: run the phase gate, demo the working app to yourself (click every journey), update the board + the Excel tracker (% complete, status), decide: gate passed → next phase; gate failed → the same phase continues Monday |
| **Daily 7:05 AM** | QA report arrives (see §8). Green = build. Red = fix first |

**Definition of Done for a feature:** code merged + tests green in CI + E2E journey passes +
flag works (on and off) + docs/notes updated in the LLD Bible if the design changed +
board card moved to Done.

---

## 11. Risks (kept from the research — do not re-learn these the hard way)

| Risk | Mitigation |
|---|---|
| Distribution — nobody knows the product exists | The H-1B wedge *is* the distribution strategy: one community (laid-off visa workers), one message (60-day plan + sponsorship filter), Reddit/communities + a Chrome extension in W2–W3. No paid ads until a sprint pass sells |
| Job-data access — Indeed has no self-serve API, LinkedIn is partnership-only | Budget $50–900/mo for JobsPipe/JSearch/Adzuna; provider behind one interface (§4) so it can be swapped; never scrape in a way that risks the product |
| LLM cost tail — one heavy user can cost $32–162/mo on frontier models | Meter usage (credits), cheap model for matching/scoring, frontier only for final resume polish, cache aggressively; no unlimited-AI subscription tier |
| Seasonality — December is dead, January is the biggest (and most competitive) month | Launch the wedge in January if W2 slips; never judge the product by December numbers |
| Scope creep — learning library, mobile, microservices before the wedge is proven | §1.4, §1.7 and the not-to-build list below. The wedge sells first; everything else waits |

## 12. What NOT to build yet

- No learning course library (M10) until the second wedge.
- No mobile code (Phase M) before web v1.0 (Jan 22, 2027).
- No microservices split before Phase 5.
- No live-interview copilot, no blind auto-apply — ever.
- No R8/ProGuard, App Store submission, or full billing UI until the web journeys work.
- No Jira, no second tracker, no second board — one board (GitHub Projects), one forecast
  (Excel tracker). Two sources of truth is how plans die.

---

## 13. Forecast (unchanged dates — v3 changes *what* is built, not *when*)

| Milestone | Date |
|---|---|
| W0 Web UI foundation (+ nightly QA pipeline green) | Fri Nov 20, 2026 |
| W1 UI + real backend | Fri Nov 27, 2026 |
| W2 Core journeys + H-1B wedge | Fri Dec 11, 2026 |
| W3 Agent brain on web | Fri Jan 1, 2027 |
| **Web v1.0 on AKS** | **Fri Jan 22, 2027** |
| Mobile decision checkpoint | Mon Jan 25, 2027 |
| **Mobile v1.0 (Android + iOS)** | **Fri Feb 19, 2027** |

*Companions: LLD Bible v2 (M1–M14), Best Execution Plan (backend phases), Project Tracker
Excel (forecast record), Phase 0 backend starter (already delivered), Market Research page
(EpitomeJob Market Research artifact, Nov 9, 2026).*
