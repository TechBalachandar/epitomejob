# EpitomeJob — Phase 0 Starter (Modular Monolith)

**What this is:** Phase 0 of the Best Execution Plan — login, profile, resume upload, approval gate + SSE. One Spring Boot deployable, modules in separate packages, each with its own Flyway schema. Microservices come in Phase 5; do not split this yet.

**Stack (all pinned, see pom.xml):** Java 21, Spring Boot 3.5.4, Spring Security 6, JJWT 0.12.6, Flyway, Postgres 16, MinIO (S3-compatible), PDFBox 3.0.2, JUnit 5.

**Prerequisites on your Mac:** Java 21, Maven 3.9+, Docker Desktop (for Postgres + MinIO only).

## Run it
```bash
cp .env.example .env        # then edit APP_JWT_SECRET (64+ hex chars)
docker compose up -d        # Postgres :5432, MinIO :9000 / console :9001
export $(grep -v '^#' .env | xargs)
mvn spring-boot:run         # app on http://localhost:8080
```

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
# 5. Approval: create a draft, then approve it
curl -X POST http://localhost:8080/approvals -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"action":"CREATE_APPLICATION","payload":{"jobId":"job-1","resumeId":"<resume-id>"}}'
curl -X POST http://localhost:8080/approvals/<approval-id>/approve -H "Authorization: Bearer $TOKEN"
# 6. SSE approval inbox (in a browser or curl): GET /approvals/stream with the Bearer token
```

## Honest limits (Phase 0)
- NOT compiled on the assistant's VM (no Java/Maven/Docker there). First `mvn spring-boot:run` on your Mac is the real build check — send the error tail only if it fails.
- Dev seed user + dev JWT secret are dev-only. Change them before any shared deployment.
- Resume parsing is PDF-only in Phase 0 (DOCX comes in Phase 3). No refresh-token flow UI yet — the API exists, the Android client wires it in Phase 1.
- No LangGraph / MCP / RAG yet — those are Phase 2–3. This starter is the system of record they will plug into.

## Layout
```
src/main/java/com/epitomejob/
  EpitomeJobApplication.java
  common/      GlobalExceptionHandler, ApiError, SecurityUtil
  identity/    User, RefreshToken, JwtService, AuthService, AuthController, JwtAuthFilter, SecurityConfig, AuthSeeder
  profile/     Profile, ProfileService, ProfileController
  resume/      Resume, ResumeService, ResumeController, BlobStorageService
  approval/    Approval, ApprovalService, ApprovalController, ApprovalSseController
src/main/resources/application.yml, db/migration/V1__identity.sql, V2__profile_resume_approval.sql
src/test/java/com/epitomejob/identity/JwtServiceTest.java, approval/ApprovalServiceTest.java
```
