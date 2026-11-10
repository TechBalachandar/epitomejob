# EpitomeJob — Execution Plan, UI-First (Revised v2)
## Web first, then mobile | 2026-11-09 | Supersedes the Android-first order in the Best Execution Plan
**The confusion, cleared in one line:** Build the **web UI first** (so you can see and click the product), connect it to the **backend you already have** (Phase 0 starter), add the **agent brain on the web**, ship the **web v1.0**, and only then build **mobile — one codebase for Android + iOS**. Do not build mobile first, do not build the agent brain before the UI works, and do not build three clients.

## 1. The tech stack — my recommendation (latest, Nov 2026)
| Layer | Pick | Version (pin it) | Why this one |
|---|---|---|---|
| Web framework | **Next.js (App Router)** | 16.4.x | Current LTS line, React 19 built in, Turbopack, routing + layouts + SSR for public pages out of the box. Next.js 15 is now maintenance-only (security fixes to Jan 31, 2027). |
| UI library | **React** | 19.3.1 | Latest stable, `useActionState` / `useOptimistic`, required by Next.js 16. |
| Language | **TypeScript** | 5.9.x | Strict mode on. Types are the contract between UI and the Spring Boot API. |
| Styling | **Tailwind CSS** | 4.3.4 | Latest, Oxide engine, no PostCSS config needed. |
| Components | **shadcn/ui** | latest (Radix + Tailwind) | Copy-paste components you own — Button, Card, Dialog, Table, Toast, Form. No vendor lock-in, looks professional on day 1. |
| Data fetching | **TanStack Query** | 5.x | Server state (jobs, matches, approvals) with caching, retries, refetch. Do not hand-roll `useEffect` fetch calls. |
| Client state | **Zustand** | 5.x | Tiny store for auth user, approval inbox, chat draft. |
| Forms | **React Hook Form + Zod** | latest | Validation schema shared with the API contract. |
| Testing | **Vitest + Playwright** | latest | Vitest for components, Playwright for the end-to-end journeys (login → upload → approve). |
| Package manager / runtime | **pnpm + Node 22 LTS** | Node 22 | pnpm is faster and stricter than npm; Node 22 is LTS. |
| API contract | **OpenAPI from the Spring Boot backend** | — | The backend is the source of truth. The web app never invents a field the API does not return. |
| Mock data (UI phase only) | **MSW (Mock Service Worker)** | latest | UI works before/without the backend running. Delete the mocks when the real API is wired — do not ship mocks. |

**What NOT to use:** plain Create React App (dead), Tailwind v3 (superseded by v4), Redux (overkill for this app), a separate Vite SPA (you lose Next.js routing/SSR for the public landing + recruiter pages, and you gain nothing — the dashboard is still React).

## 2. First / Next / Next — the whole build in order
### FIRST — Phase W0: Web UI foundation (Weeks 1–2: Nov 9–20, 2026)
**What you build:** the app shell you can click, with mock data only — no backend needed yet.
1. `npx create-next-app@latest` (TypeScript, Tailwind, App Router, src/) + shadcn/ui init + TanStack Query + Zustand.
2. Design system first: colours, fonts, Button/Card/Input/Dialog/Table/Toast, dark mode, mobile-responsive layout.
3. Pages: `/login`, `/register`, `/` dashboard, `/profile`, `/resumes` (upload), `/jobs`, `/matches`, `/coach` (chat), `/approvals` (inbox), `/interviews`, `/tracker`, `/learning`.
4. Auth flow in the UI: login form → store JWT in memory + refresh token in httpOnly cookie (never localStorage for the access token) → protected routes redirect to `/login`.
5. Coach chat UI + approval card UI (Approve / Reject buttons) — with MSW mock replies.
6. Resume upload UI: drag-and-drop, PDF only, progress, status READY / FAILED.
**Done looks like:** `pnpm dev` → log in with a mock user → click every page → upload a fake resume → see an approval card → Approve → it disappears from the inbox. Nothing is real yet, but everything is visible.
**Gate:** every page renders, no console errors, mobile width (390px) does not break the layout.

### NEXT — Phase W1: Connect the UI to the real backend (Week 3: Nov 23–27, 2026)
**What you build:** replace MSW mocks with the Phase 0 backend starter you already have (Spring Boot 3.5.4, Postgres + MinIO, JWT, Profile/Resume, approval + SSE).
1. Run the backend (`docker compose up -d`, `mvn spring-boot:run`) and run the Phase 0 gate curls first — if the backend does not pass, fix that before touching the UI.
2. Wire TanStack Query to the real endpoints: `/auth/login`, `/profiles/me`, `/resumes`, `/approvals/pending`, `/approvals/stream` (SSE).
3. Delete the MSW mocks for the wired endpoints, one endpoint at a time. A page is done only when it works against the real API.
4. Error states in the UI: 401 → back to login, 403 → "not allowed", 404 → empty state, 500 → "try again" (never a stack trace).
**Done looks like:** register a real user in the web UI → upload a real PDF → it appears in MinIO → create an approval in the UI → approve it → the audit trail shows it.
**Gate:** the Phase 0 gate, done through the browser instead of curl.

### NEXT — Phase W2: Core journeys on the web (Weeks 4–5: Nov 30–Dec 11, 2026)
**What you build:** Job Discovery + Matching on the web (M5 + M6), Application Tracker kanban (M9), Interview Prep (M8).
1. Jobs search page (filters, saved searches) + job detail.
2. Matches page: ranked jobs with the explanation ("why this matches" + gaps) — the explanation is the product.
3. Tracker kanban: SAVED → APPLIED → INTERVIEW → OFFER → REJECTED, with follow-up reminders.
4. Interview mock: 5 questions, answer, rubric score, feedback report.
**Gate:** Journey A end to end in the browser — register → profile → upload resume → see 5 ranked jobs with explanations → save one → mock interview for it.

### NEXT — Phase W3: The agent brain, on the web (Weeks 6–7: Dec 14, 2026–Jan 1, 2027)
**What you build:** Python LangGraph Supervisor + 6 specialists, MCP Server, RAG, Temporal — all surfaced in the Coach chat UI you already built in W0.
1. LangGraph service skeleton, one single-task agent first, then the Supervisor graph.
2. Coach chat: "Find me jobs and prepare me" → plan → specialists → approval card → approve → Tracker records it.
3. RAG: ask a question about your resume, answer with citations.
4. Eval suite (20 questions → 50) + LangSmith. A prompt change that drops the score does not ship.
**Gate:** Journey B in the browser — the multi-step job pauses for approval, resumes after Approve, and never executes a write without one.

### NEXT — Phase W4: Polish, containers, cloud (Weeks 8–10: Jan 4–22, 2027)
Docker Compose for the full stack (backend + LangGraph + Postgres + MinIO + Kafka + Ollama), restart-mid-flight recovery test, kind → AKS, GitHub Actions CI with the eval gate, R8/ProGuard is not a web concern — skip it here, it returns for mobile.
**Gate:** push a branch → CI tests + evals → deploys to AKS. **Web v1.0 launches Fri Jan 22, 2027.**

### LAST — Phase M: Mobile, Android + iOS from one codebase (Weeks 11–14: Jan 25–Feb 19, 2027)
**Do not start mobile until web v1.0 is live.** One codebase, two stores. The decision, made now so you can plan for it:

| Option | What it is | Best for | Watch out |
|---|---|---|---|
| **React Native (Expo) — RECOMMENDED** | One TypeScript codebase, native apps for Android + iOS. Reuses your web stack: React, TypeScript, TanStack Query, Zustand, shadcn-style components (via React Native Paper / Tamagui). | You, because the web is already React + TS — you reuse 60–70% of the logic, one language, one team, fastest to two stores. | Native modules (camera, push) need Expo dev builds; UI is not pixel-identical to native, and it does not need to be. |
| Kotlin Multiplatform (KMP) | Share business logic in Kotlin, native UI per platform (Compose + SwiftUI). | Kotlin-first teams who want fully native UI and already have Android (Kotlin) code to share. | **You parked KMP on Oct 30, 2026 because Xcode stopped supporting your setup.** iOS still needs a Mac with a recent Xcode — on your 2019 Intel Mac that is the risk. Only pick KMP if you first verify Xcode builds an iOS app on your machine. |
| Flutter | One Dart codebase, own rendering engine, fast UI. | Teams starting from zero who want one UI everywhere. | Dart is a third language (after Java/Kotlin + TypeScript). You learn a new stack for the mobile slice alone. |

**My call:** **React Native (Expo)**. It is the only option that reuses what you are about to build (React + TS), gets you to both stores fastest, and does not depend on fixing Xcode on a 2019 Intel Mac first. KMP is the runner-up — revisit it only if you verify an Xcode iOS build works on your Mac before Phase M starts, and you are willing to maintain two UIs (Compose + SwiftUI) instead of one.
**Mobile scope (Phase M):** login, profile, resume upload (camera → photo a paper resume), Coach chat, approval inbox (push), matches, tracker. Voice + camera are the mobile-native features — they are the reason mobile exists, not a copy of the web dashboard.
**Gate:** install on a real Android phone + a real iPhone → login → approval push arrives → Approve from the phone → Tracker updates on the web. **Mobile v1.0: Fri Feb 19, 2027.**

## 3. What NOT to build yet
- No native Android app first (the old plan) — web first now.
- No KMP / React Native / Flutter code before web v1.0 (Jan 22, 2027).
- No microservices split before Phase 5 of the backend plan (the modular monolith still stands — this plan changes the *client* order, not the backend architecture).
- No R8/ProGuard, no App Store submission, no billing UI until the web journeys work.

## 4. This week, concretely (Nov 9–13)
1. **Today:** `create-next-app` + shadcn/ui + Tailwind, design tokens, app shell + `/login` page.
2. **Tue–Wed:** dashboard + profile + resumes upload pages (mock data).
3. **Thu:** Coach chat + approval inbox pages (mock data).
4. **Fri:** gate — click every page, 390px width check, no console errors. Send me the result (pass, or the error tail) and I will mark Phase W0 in the tracker and give you the W1 wiring steps.
**If you get stuck on one page, do not jump to the next phase — fix it, the cake rule still applies: a phase is not done until its gate passes.**

## 5. Forecast (revised)
| Milestone | Date |
|---|---|
| Web UI foundation (W0) | Fri Nov 20, 2026 |
| UI + real backend (W1) | Fri Nov 27, 2026 |
| Core journeys (W2) | Fri Dec 11, 2026 |
| Agent brain on web (W3) | Fri Jan 1, 2027 |
| **Web v1.0 on AKS** | **Fri Jan 22, 2027** |
| Mobile decision checkpoint (verify Xcode if KMP is still wanted) | Mon Jan 25, 2027 |
| **Mobile v1.0 (Android + iOS)** | **Fri Feb 19, 2027** |

*Companions: LLD Bible v2 (M1–M14), Best Execution Plan (backend phases), Project Tracker (update the Roadmap sheet to this UI-first order), Phase 0 backend starter (already delivered).*
