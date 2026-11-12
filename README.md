# EpitomeJob

**AI career assistant that finds matching jobs, explains *why* each matches, tailors your
resume for that job, preps you for the interview, tracks applications — and never applies
or writes anything without your approval.**

- **First wedge:** laid-off H-1B / visa-dependent tech workers (60-day clock, sponsorship
  filter, 60-day countdown plan). See `docs/EpitomeJob-Market-Research.md`.
- **Pricing:** $79–99 one-time 30-day sprint pass, $149 lifetime, $299–499 human-reviewed
  package. No generic $20–30/mo subscription.
- **Architecture:** modular monolith first (Spring Boot), microservices later via a Phase 5
  strangler split. 14 modules (M1–M14), Supervisor + 6 specialist agents, approval gate
  (24h signed expiry, idempotent, audited).
- **Build order:** web UI first (Next.js 16 + React 19), then mobile (React Native / Expo).
  Web v1.0: Fri Jan 22, 2027. Mobile v1.0: Fri Feb 19, 2027.

## Repo layout

| Path | What it is |
|---|---|
| `backend_backup` | Phase 0 backend starter — Spring Boot 3.5.4 / Java 21, Postgres 16 + MinIO, JWT auth (M2), Profile (M3), Resume PDF upload (M4), approval gate + SSE (M7). See `backend_backup` for the run + gate steps |
| `docs/` | Execution plan v3 (current), UI-first plan v2, best execution plan, system design, plan validation, LLD Bible v2 (HTML), market research report |
| `.github/workflows/` | `ci.yml` (PR checks) and `nightly.yml` (full suite, daily 6:00 AM ET) |

## Quick start (backend)

```bash
cd backend
cp .env.example .env   # change APP_JWT_SECRET
docker compose up -d
mvn spring-boot:run    # http://localhost:8080
```

Run the Phase 0 gate (health → register → login → profile → resume → approval → approve)
with the curls in `backend_backup`. The web UI (W0) is being built next — see
`docs/EpitomeJob-Execution-Plan-v3-Updated.md`.

## Rules

- **Cake rule:** every phase ends with a working app; a phase is Done only when its gate passes.
- **Agents never approve each other.** Only the user approves a write.
- **Every feature is plug-and-play:** own package, own tables/migration, feature flag,
  own tests, rollback = flag off.
- **No live-interview copilot, no blind auto-apply — ever.**
- **Local Ollama is free for Dev/QA/PreProd.** Cloud models only for PROD, metered.
