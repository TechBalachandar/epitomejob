# INSTALL — EpitomeJob W1 web (wired to the real backend)

> Status 2026-11-14: the W1 zip replacement in §2 is done (the web app is on
> main). The run steps below are updated to the current stack — the full
> 18-service compose stack from the repo root, Mac and Windows. The browser
> gate in §4 still applies. The canonical run guide is the root `README.md`.

The W1 zip replaces the W0 web app entirely. The user has not modified
his local copy, so a full replacement is safe. **The backend must be
running first** — the web app talks to it through its BFF proxy, which now
goes through the gateway on http://localhost:8080.

## 1. Backend first (repo root — full stack)

```bash
cd ~/epitomehub/epitomhubjobs/epitomejob
# If the old backend-only stack is running, stop it first — the two stacks
# both publish :5432 and :9000 and must never run at the same time.
cd backend && docker compose down && cd ..
docker compose up -d --build   # 18 services, from the repo root
docker compose ps              # wait until every service is healthy
```

If `mvn` is not found in a fresh terminal (Mac): `source ~/.zshrc` first
(Maven lives in `~/tools/`, Java 21 via `JAVA_HOME` — see the Phase 0
notes). On Windows use Git Bash; Java 21 (Temurin MSI) + Maven
(Chocolatey `choco install maven`) must be on `PATH`. The gateway must
answer on http://localhost:8080
(check: `curl http://localhost:8080/actuator/health` → `{"status":"UP"}`).
The backend itself is internal on `:8081` in compose — only the gateway
is the external entry point. Full Mac + Windows steps: root `README.md`.

## 2. Replace the web app

```bash
cd ~/epitomehub/epitomhubjobs/epitomejob
rm -rf web
unzip -o ~/Downloads/epitomejob-w1-web-wired.zip
# the zip contains web/ at its top level
```

## 3. Install + run

```bash
cd web
cp .env.example .env.local   # optional — defaults already point at localhost:8080
pnpm install
pnpm dev                     # http://localhost:3000
```

pnpm: `npm install -g pnpm@10` if it is not installed (Node 25+ no longer
ships corepack, so `corepack enable` does not work).

## 4. W1 gate — confirm in the browser

1. Register a new user (fresh unique email) → lands on dashboard.
2. Log out, log back in with `dev@example.com` / `DevPass123!`.
3. Profile page: edit headline → Save → completeness score updates.
4. Resumes: upload a real PDF → status READY. Try a non-PDF → rejected.
5. Matches: click Apply on a job → check Approvals inbox → Approve →
   the card disappears (and stays gone after a reload while signed in).
6. Log out → any protected page redirects to `/login`.

## Notes

- The access token lives in memory only: a full page reload returns to
  `/login` (known W1 limit — session restore on load is a follow-up).
- Jobs / matches / interviews / tracker are wired to the real backend since
  W2, and Coach is the real agent-brain since W3 (LangGraph + RAG) — the
  mock-data and keyword-scripted notes above are W1-era and no longer true.
- MSW was removed in W1 (its handlers for the wired endpoints were
  deleted); nothing in the app needs it anymore.
- Validation on this build: `tsc` clean, `pnpm lint` 0 errors,
  Vitest 31/31, Playwright 10/10, `pnpm build` green — all against
  stubbed BFF responses, **not** the real backend. The Mac run above
  is the real proof.
