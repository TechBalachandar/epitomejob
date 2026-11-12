# EpitomeJob — Phase 0 Backend (Modular Monolith, Clean Architecture)

**What this is:** Phase 0 of the Best Execution Plan — login, profile, resume upload, approval gate + SSE. One Spring Boot deployable. Microservices come in Phase 5; do not split this yet.

**Stack (all pinned, see pom.xml):** Java 21, Spring Boot 3.5.4, Spring Security 6, JJWT 0.12.6, Flyway, Postgres 16, RustFS (S3-compatible), PDFBox 3.0.2, JUnit 5, Mockito, Testcontainers, JaCoCo, springdoc OpenAPI 2.8.13.

**Prerequisites on your Mac:** Java 21, Maven 3.9+, Docker Desktop (for Postgres + RustFS only).

## Run it
```bash
cp .env.example .env        # then edit APP_JWT_SECRET (64+ hex chars)
docker compose up -d        # Postgres :5432, RustFS :9000 / console :9001
export $(grep -v '^#' .env | xargs)
mvn spring-boot:run         # app on http://localhost:8080
```
API docs: http://localhost:8080/swagger-ui.html (OpenAPI JSON at /v3/api-docs).

## Tests & coverage
```bash
mvn verify                  # unit + slice + integration tests, then the JaCoCo gate
open target/site/jacoco/index.html   # coverage report
```
Coverage gate (enforced in CI, build fails below it): **≥80% lines, ≥70% branches** overall. Security-critical paths (auth, tokens, approval decisions) are covered near 100%. Integration tests run the full gate journey against real Postgres + RustFS via Testcontainers (Docker must be running).

## Phase 0 gate (run these, in order)
```bash
# 1. Health
curl http://localhost:8080/actuator/health
# 2. Register + login (dev seed user also exists: dev@example.com / DevPass123! — dev profile only, change it)
curl -X POST http://localhost:8080/auth/register -H 'Content-Type: application/json' \
  -d '{"email":"you@example.com","fullName":"Your Name","password":"Passw0rd!"}'
TOKEN=$(curl -s -X POST http://localhost:8080/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"you@example.com","password":"Passw0rd!"}' | python3 -c 'import sys,json;print(json.load(sys.stdin)["accessToken"])')
# 3. Profile
curl -X PUT http://localhost:8080/profiles/me -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"headline":"Java Developer","location":"Reston, VA","workType":"REMOTE"}'
# 4. Resume upload (PDF only in Phase 0)
curl -X POST http://localhost:8080/resumes -H "Authorization: Bearer $TOKEN" -F file=@resume.pdf
# 5. Approval: create a draft, then approve it.
#    NOTE: payload is a JSON *string*, not an object — the backend stores it as TEXT.
curl -X POST http://localhost:8080/approvals -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"action":"CREATE_APPLICATION","payload":"{\"jobId\":\"job-1\",\"resumeId\":\"<resume-id>\"}"}'
curl -X POST http://localhost:8080/approvals/<approval-id>/approve -H "Authorization: Bearer $TOKEN"
# 6. SSE approval inbox (in a browser or curl): GET /approvals/stream with the Bearer token
```
Or run the Postman collection `EpitomeJob-Phase0.postman_collection.json` (Runner runs 1–7; the SSE stream is manual-only).

## W2 — jobs, matching, tracker, interviews (Flyway V3)

18 seeded jobs (6 roles) and a 30-question bank (5 per role) ship in
`V3__jobs_matching_tracker_interview.sql`. New modules: `job` (M5),
`matching` (M6), `tracker` (M9), `interview` (M8). All endpoints need a
Bearer token; no security-config change was needed.

| Endpoint | Notes |
|---|---|
| `GET /jobs?query=&location=&workType=&sponsorsH1b=&page=0&size=20` | Filters per contract §2; `size` clamped to 50. Returns `{content, page, size, totalElements, totalPages}`. |
| `GET /jobs/{id}` | 404 when missing. |
| `GET /matches/me` | Top 20 by score desc. Score = skills 50 + location 10 + work type 10 + salary 15 + sponsorship 15. `source` is `RESUME` when a READY resume exists, else `PROFILE`. |
| `GET /matches/{jobId}` | 404 when the job is missing. |
| `POST /tracker {jobId, resumeId?}` | 201 `SAVED`; 409 on duplicate user+job; 404 job missing. |
| `GET /tracker/me` | Newest first. |
| `PUT /tracker/{id} {status, notes?}` | 400 invalid status; 404 not owner; absent `notes` keeps existing notes. |
| `DELETE /tracker/{id}` | 204; 404 not owner. |
| `GET /interviews/questions?jobId=` | 5 questions for the job's role (General fallback); 400 when `jobId` is absent, 404 job missing. |
| `POST /interviews/sessions {jobId}` | 201; always a new session (retakes allowed). |
| `POST /interviews/sessions/{id}/answers {questionId, answerText}` | 200; re-posting replaces the previous answer and re-scores it. 400 empty answer / question outside the session's role set; 404 session not owner. |
| `GET /interviews/sessions/me` | Newest first. |
| `GET /interviews/sessions/{id}` | 404 not owner. Session becomes `COMPLETED` (with `completedAt` and `averageScore`) once every role question has an answer. |

Apply flow (contract §6): the web saves a tracker entry, proposes an
approval with `action: "APPLY_JOB"` and a JSON-string payload
`{"jobId","resumeId","trackerId"}`, and on approval calls
`PUT /tracker/{trackerId} {status: "APPLIED"}`. The backend never
auto-applies; `AppConstants.Approval.ACTION_APPLY_JOB` exists for
documentation only.

Job providers (contract §8): `JobProvider` is the interface.
`SeedJobProvider` (default) reads `job.jobs`. Set
`epitomejob.jobs.provider=adzuna` to select `AdzunaJobProvider`, a stub
that makes no network calls — on first use it throws `IllegalStateException`
naming `ADZUNA_APP_ID` / `ADZUNA_APP_KEY` when they are missing.

## Layout — clean architecture, module first
Modules stay the top-level boundary (one package per bounded context, so a module can be lifted out whole in the Phase 5 strangler split). Inside each module, strict layers: `controller` → `service` → `repository` → `model`, with `dto` at the boundary. Controllers never return entities — only DTOs. Shared cross-cutting code lives in `common/`.

```
src/main/java/com/epitomejob/
  EpitomeJobApplication.java
  common/
    config/      OpenApiConfig
    constants/   AppConstants            # THE rule: a literal used twice lives here
    exception/   ApiError, GlobalExceptionHandler
    filter/      TraceIdFilter           # X-Request-Id in, MDC traceId, echo on response
    security/    SecurityConfig, JwtAuthFilter, AppUserDetailsService
    util/        HashUtil, SecurityUtil
  identity/      M2 — controller/ service/ repository/ model/ dto/ config/ (AuthSeeder, dev only)
  profile/       M3 — controller/ service/ repository/ model/ dto/
  resume/        M4 — controller/ service/ repository/ model/ dto/
  approval/      M7 — controller/ service/ repository/ model/ dto/
  job/           M5 — controller/ service/ (JobProvider, SeedJobProvider, AdzunaJobProvider) repository/ model/ dto/ config/
  matching/      M6 — controller/ service/ dto/
  tracker/       M9 — controller/ service/ repository/ model/ dto/
  interview/     M8 — controller/ service/ repository/ model/ dto/
src/main/resources/application.yml, db/migration/V1__identity.sql, V2__profile_resume_approval.sql, V3__jobs_matching_tracker_interview.sql
src/test/java/com/epitomejob/
  common/ identity/ profile/ resume/ approval/ job/ matching/ tracker/ interview/   # unit + @WebMvcTest slices
  integration/GateJourneyIT.java, W2JourneyIT.java   # Testcontainers end-to-end gates
  EpitomeJobApplicationTests.java                 # context smoke test (H2 profile)
```

## Rules this codebase follows
- **No hardcoded strings:** statuses (`PENDING`, `READY`…), claims, headers, limits, and error messages live in `AppConstants`. If a string appears twice, it moves there.
- **DTOs at the boundary:** requests are validated records; responses are DTOs. Entities never leave the service layer (the resume response hides the storage path and parsed text; the approval response hides the token hash).
- **Security:** BCrypt cost 12; signed JWTs (`parseSignedClaims` — see the SampAgent lesson in `JwtService`); refresh rotation with reuse detection that revokes all tokens on suspected theft; generic login errors; no stack traces to clients; every error carries a `traceId`.
- **Approval gate:** nothing a user asks the system to do on their behalf executes without an approval; decisions are idempotent; approvals expire after 24h.

## Honest limits (Phase 0)
- Dev seed user + dev JWT secret are dev-only. Change them before any shared deployment.
- Resume parsing is PDF-only in Phase 0 (DOCX comes in Phase 3). No refresh-token flow UI yet — the API exists, the mobile client wires it in Phase 1.
- No rate limiting on `/auth/login` yet (brute-force protection is a Phase 1 item; the `login_attempts` table is reserved for it).
- No LangGraph / MCP / RAG yet — those are Phase 2–3. This starter is the system of record they will plug into.
