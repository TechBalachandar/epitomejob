# Quester Issue-Replication Sweep — 2026-11-18

**Scope:** Local clone `~/workspace/epitomejob-qa-clone`, branch `kotlin-migration`,
head `a7822a2` ("Resume Builder iteration 2", committed Nov 18 ~04:18 ET).
**This report is about THIS clone's code only.** Several fixes below were made on
other branches / in the `qa` branch; where a fix commit is an ancestor of
`a7822a2` it is stated explicitly (checked with
`git merge-base --is-ancestor <commit> a7822a2`).

**What was NOT available on this VM (no live browser, no Docker/backend,
no `mvn verify`):** anything marked NEEDS MAC below still needs the Mac
(live compose stack, browser, `mvn verify`). No GitHub writes of any kind
were made for this sweep (no issues filed/closed/assigned, no commits,
no product files modified). No temporary test files were created; the
existing web Vitest suite was run as-is.

## Test runs performed

- Web Vitest, full suite: **15 files, 255 tests, 255 passed, 0 failed**
  (`cd web && npx vitest run`, ~25 s). Includes:
  - `tests/approval.test.tsx` — 9 passed (incl. "expired card shows no
    decision buttons, only the expired note")
  - `tests/auth-store.test.ts` — 4 passed
  - `tests/resume-builder.test.tsx` — 101 passed (incl. "typing updates
    the live preview", "add / remove experience items updates state and
    preview")
  - `tests/resume-validation.test.ts` — 6 passed (incl. "accepts DOC and
    DOCX as well as PDF")
- Backend / gateway / agent-brain tests: **not run** — `mvn` is not
  available on this VM. Backend and gateway verdicts below rest on code
  reading + test existence, and are flagged where the test run itself
  still needs the Mac.
- YAML check: `python3 -c "yaml.safe_load(...)"` on
  `.github/workflows/quester-qa.yml` — parses OK, 8 jobs.

## Verdicts

| # | Issue | Verdict | Evidence |
|---|-------|---------|----------|
| 8 | Full page refresh returns to /login — session not restored | **CANNOT REPLICATE** | `web/src/components/app-shell.tsx:108-144`: on load, before any redirect, `restoreSession()` is called and the redirect waits on `restoreChecked`. `web/src/lib/api.ts:106-109`: `restoreSession()` calls `refreshAccessToken()` (BFF `POST /api/auth/refresh`, httpOnly cookie, `web/src/app/api/auth/refresh/route.ts:15-19`). The described "nothing calls refresh on load" is absent. Live reload in a real browser still needs Mac confirmation. |
| 9 | Resume upload on /resumes accepts only PDF | **CANNOT REPLICATE** | `web/src/app/resumes/page.tsx:182`: `accept=".pdf,.doc,.docx"`, button label is now "Choose file" (line ~192). BFF `web/src/app/api/resumes/route.ts:1-10` is a raw multipart passthrough (no type filter). Backend `backend/src/main/kotlin/com/epitomejob/resume/service/ResumeService.kt:45-80` accepts pdf/docx/doc and records the format. `web/tests/resume-validation.test.ts:15` ("accepts DOC and DOCX as well as PDF") passes. Live DOC/DOCX upload end-to-end still needs the Mac. |
| 10 | Resume Builder form fields do not build the resume | **CANNOT REPLICATE** | Current wiring (not the Nov 14 version): every section input calls `onChange` with a patched state (`web/src/app/resume-builder/page.tsx:213-577`, e.g. contact/title/summary/experience `title`/`company` at 375-387); page-level `onChange` at line 1073 is `setBuilderState(next)`; `ResumePreview` receives `state={builderState}` (line 1450-1455) and re-renders `srcDoc` on every keystroke (`web/src/components/resume-builder/resume-preview.tsx:2,38`). Vitest `resume-builder.test.tsx`: 101 passed, incl. "typing updates the live preview". |
| 11 | Coach is not working as expected in the live app | **NEEDS MAC** | Code path exists and the known gateway cause is fixed in this clone: gateway `application.yml:238-267` has the `coach` route (`Path=/coach/**`, `lb://epitomejob-backend`) with `ThreadPoolBulkhead` + its own `coach` CircuitBreaker (120 s), with a comment that the old 3 s `backend` limiter "cancelled every Coach call (#11)" — fix commit `41aeed9` is an ancestor of `a7822a2`. `backend/src/main/kotlin/com/epitomejob/coach/controller/CoachController.kt:21-22` + `coach/service/CoachService.kt:13-17` forward to agent-brain verbatim. But the exact live symptom (error vs blank vs wrong reply) was never pinned down, and the live agent-brain/Ollama path cannot run here. **Mac must prove:** log in, send a Coach message, confirm a real answer with tool use and cited sources, and record the exact failure if it still fails. |
| 12 | Resume Builder: Job Title and Company swapped | **CANNOT REPLICATE** (code-level; test run needs Mac) | `resume-builder-service/src/main/kotlin/com/epitomejob/resumebuilder/service/ResumeStateParser.kt:197-218` has an explicit `splitHeader` heuristic (company-score, location never a company) whose comment names issue #12; form wiring binds `item.title` → Title field and `item.company` → Company field (`web/src/app/resume-builder/page.tsx:375-387`). Test `ResumeStateParserTest.kt:75` `companyFirstHeaderIsNotSwapped` asserts the exact reported case ("Zillion Technologies/Navy Federal \| Lead Engineer"). Fix commit `41aeed9` is an ancestor. Caveat: the Kotlin test itself was **not executed** here (no `mvn`); the Mac `mvn verify` must run `ResumeStateParserTest` to execute this proof. |
| 13 | Job Apply links go to generic portal/company pages | **CANNOT REPLICATE** | `backend/src/main/resources/db/migration/V9__seed_jobs_no_apply_link.sql` sets `source_url = NULL` for all 18 seeded jobs, including the reported Navy Federal "Senior Android Engineer" (`10000000-0000-4000-8000-000000000009`, seeded in `V3__jobs_matching_tracker_interview.sql:79`, whose V8 generic URL `https://www.navyfederal.org/careers` is the exact reported link). Web `web/src/components/job-source.tsx:17-18` renders nothing when `sourceUrl` is null. Live feed is safe in code: `AdzunaJobProvider.kt:185-191` sets `sourceUrl` only from a non-blank `redirect_url` starting `http://`/`https://`. Note: the Jobs page "Apply" button is the approval-gate flow (`web/src/app/jobs/page.tsx:55-64`), not an external link. Live Adzuna redirect correctness still needs the Mac/browser. |
| 15 | Quester QA workflow never runs — YAML error line 144 | **CANNOT REPLICATE** | `.github/workflows/quester-qa.yml` parses cleanly with `yaml.safe_load` (8 jobs: backend, gateway, python-tests, python-evals, web-unit, web-e2e, smoke, triage). The quoted `-w "web: %{http_code}\n"` now sits at line 195 inside a `run: \|` block scalar, where the colon is legal. Fix commits `5a8645e` / `05eaaf0` are ancestors of `a7822a2`. The workflow itself was not executed (needs CI). |
| 16 | P0: Resumes not scoped to the user | **CANNOT REPLICATE** | Fix commit `1bff530` is an ancestor of `a7822a2`. Every resume access path in this clone is owner-scoped: `ResumeService.listMine()` uses `findByUserIdOrderByCreatedAtDesc(currentUserId)` (`ResumeService.kt:129-131`); `upload()` stamps `SecurityUtil.currentUserId()` (`:71-72`); `renameLabel()` and `getParsedText()` filter `findById(id)` by owner, else 404 (`:95-101`, `:134-140`); `MatchingService.loadCandidate()` owner-filters a chosen resume id (`MatchingService.kt:162-169`); builder documents use `findByIdAndUserId` / `findByUserIdOrderByUpdatedAtDesc` (`resume-builder-service/.../ResumeDocumentService.kt:59,93`). No download or delete endpoint exists on `ResumeController` (only upload, rename, list `/me`, get text), so there is no unscoped path to leak through. A live two-account check still needs the Mac for end-to-end proof. |
| 19 | Quester QA smoke job fails at compose build/start | **NEEDS MAC** | The workflow's smoke job (`quester-qa.yml`, smoke) now pre-creates the external volumes (`docker volume create backend_pgdata` / `backend_rustfsdata`) before `docker compose up -d --build`, and dumps `docker compose ps -a` + logs on failure — the missing-error problem from the filed runs is addressed in the file. Static reading of `docker-compose.yml` (18 services) shows no obvious break. The compose failure itself can only be reproduced by running it. **Mac/CI must prove:** run the smoke job (or `docker compose up -d --build` from the repo root) and capture the exact failing service/error from the failure-dump step. |
| 21 | Gateway RouteConfigTest fails on Mac — route count 14 vs 13, MCP route http vs lb, retry NPE | **NEEDS MAC** (for the `mvn` run; config side reconciled in code) | Config and test now agree in this clone: `gateway-service/src/main/resources/application.yml` defines **14** routes; `RouteConfigTest.kt:22,40-56` expects 14 (12 `lb://epitomejob-backend` + `mcp` static `http://mcp-server:8001` at yml line 367 + `resume-builder` `lb://`); the test asserts `http` scheme/host `mcp-server`/port 8001 for MCP (lines 120-122). The retry NPE path is guarded: the test throws a named `AssertionError` if a Retry/CircuitBreaker filter is missing and asserts `cb.args.containsKey("fallbackUri")` before the `!!` deref (lines 190-202). CI gap also closed in the file: `quester-qa.yml` `gateway` job runs root `mvn -B verify`, and root `pom.xml:19-24` modules are `libs/epitomejob-common`, `eureka-server`, `gateway-service` — so CI now runs the gateway tests (fix commit `ae0db9d` is an ancestor). **Mac must prove:** run `mvn -B verify` (root aggregator) and report the gateway `RouteConfigTest` result (26 tests). |
| 22 | Gateway tests job fails on first CI run — RouteConfigTest auth fallbackUri assertion | **NEEDS MAC** (for the `mvn`/CI run; config side fixed in code) | Same test as #21. In this clone the YAML **does** carry `fallbackUri` on all 14 routes including `auth` (`application.yml:92` for auth, and lines 116, 140, 164, 188, 212, 236, 268, 292, 316, 340, 364, 388, 412 for the rest), and the test guards with `containsKey("fallbackUri")` before dereferencing (`RouteConfigTest.kt:198-202`), so the reported "args map does not contain it" + NPE cannot occur from the config as written. The CI failure at run 43685482075 / head `3cdede7` predates fix commit `ae0db9d` (ancestor of `a7822a2`). **Mac/CI must prove:** a fresh `mvn -B verify` gateway run (or the next CI run of the `gateway` job in `quester-qa.yml`) with `RouteConfigTest` green. |
| 25 | Jobs page fails entirely when the jobs provider throws — no fallback to seeded jobs | **CANNOT REPLICATE** (code-level; test run needs Mac) | Fix commit `20d4e9b` is an ancestor of `a7822a2`. `backend/src/main/kotlin/com/epitomejob/job/service/JobService.kt:28-38`: `search()` wraps `provider.search(...)` in try/catch, logs the failure, and falls back to `SeedJobProvider(jobs).search(...)`. Test `JobServiceTest.kt:98` `searchFallsBackToSeededJobsWhenProviderThrows` exists. Caveat: the backend test was **not executed** here (no `mvn`); the Mac `mvn verify` must run `JobServiceTest` to execute this proof. |
| 26 | Resume Builder shows gateway 503 fallback after restart — verify breaker closed | **NEEDS MAC** | Verification issue, not a code bug. In code the route exists (`application.yml:390-412`, `resume-builder` → `lb://resume-builder-service`, own `resume-builder` CircuitBreaker with `fallbackUri: forward:/fallback/resume-builder`) and the fallback controller exists (`FallbackController.kt:52`). Whether the breaker is actually closed after the 02:56 gateway restart can only be observed live. **Mac/browser must prove:** open the Resume Builder, confirm templates load with no 503 fallback message, and confirm the gateway `/actuator/health` + circuit-breaker state show the `resume-builder` breaker closed. |
| 27 | Expired approvals are not displayed properly — look like live pending items | **CANNOT REPLICATE** | Fix commit `8c47320` is an ancestor of `a7822a2`. `web/src/components/approval-card.tsx:20-33`: `isExpired` (expiresAt ≤ now) renders the card muted (`opacity-60`) with an expired note and **no** decision buttons. Vitest `tests/approval.test.tsx` (9 tests, all pass) includes "expired card shows no decision buttons, only the expired note" (line 104). Live rendering still needs the Mac/browser. |
| 28 | Approve/Reject buttons shown on expired approvals | **CANNOT REPLICATE** | Same fix as #27 (`8c47320`, ancestor). `approval-card.tsx:33-48`: the Approve/Reject buttons render only in the non-expired branch. Same Vitest evidence as #27 (approval suite 9/9 pass). Live rendering still needs the Mac/browser. |

## Summary counts

- **CANNOT REPLICATE: 10** — #8, #9, #10, #12, #13, #15, #16, #25, #27, #28
- **CAN REPLICATE: 0**
- **NEEDS MAC: 5** — #11, #19, #21, #22, #26

## What could NOT be verified here

- No live browser: session restore on reload (#8), DOC/DOCX upload end-to-end
  (#9), Builder live preview/export in the real app (#10, #12, #26), Coach
  behaviour (#11), live Apply/redirect links (#13), two-account resume
  isolation (#16), expired-approval rendering (#27, #28) — all still need
  the Mac browser against the compose stack.
- No Docker/backend: compose smoke failure (#19), Coach agent-brain path
  (#11), gateway breaker state after restart (#26).
- No `mvn verify` on this VM: backend tests for #12 (`ResumeStateParserTest`,
  incl. `companyFirstHeaderIsNotSwapped`), #25 (`JobServiceTest`, incl.
  `searchFallsBackToSeededJobsWhenProviderThrows`), and gateway
  `RouteConfigTest` (#21, #22) were read and their assertions checked, but
  not executed. The Mac `mvn verify` runs are the remaining proof for those.
- No CI run was triggered: #15 (YAML parse only) and #19/#22 (workflow
  content only) were verified statically.

## Notes for the Developer (no CAN REPLICATE items, so no fix work arises)

- The five NEEDS MAC items are verification-only; the Mac run list is:
  1. `mvn -B verify` (root aggregator) — must show gateway `RouteConfigTest`
     green (#21, #22) and backend `JobServiceTest` +
     `ResumeStateParserTest` green (#25, #12).
  2. `docker compose up -d --build` / smoke job — capture the exact
     compose error if it still fails (#19).
  3. Browser: refresh-keeps-login (#8), DOC/DOCX upload (#9), Builder
     preview + title/company mapping (#10, #12), Coach reply with sources
     (#11), Adzuna Apply redirect (#13), expired-approval cards (#27, #28),
     Resume Builder templates with no 503 after the gateway restart (#26).
