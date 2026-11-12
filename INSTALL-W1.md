# INSTALL — EpitomeJob W1 web (wired to the real backend)

The W1 zip replaces the W0 web app entirely. The user has not modified
his local copy, so a full replacement is safe. **The backend must be
running first** — the web app talks to it through its BFF proxy.

## 1. Backend first (repo root)

```bash
cd ~/epitomehub/epitomhubjobs/epitomejob/backend
docker compose up -d        # Postgres + RustFS; wait until both are healthy
mvn spring-boot:run         # wait for "Started EpitomeJobApplication"
```

If `mvn` is not found in a fresh terminal: `source ~/.zshrc` first
(Maven lives in `~/tools/`, Java 21 via `JAVA_HOME` — see the Phase 0
notes). The backend must answer on http://localhost:8080
(check: `curl http://localhost:8080/actuator/health` → `{"status":"UP"}`).

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
- Jobs / matches / interviews / tracker still use mock data (no backend
  endpoints yet); Coach replies are keyword-scripted. That is W2/W3 work.
- MSW was removed in W1 (its handlers for the wired endpoints were
  deleted); nothing in the app needs it anymore.
- Validation on this build: `tsc` clean, `pnpm lint` 0 errors,
  Vitest 31/31, Playwright 10/10, `pnpm build` green — all against
  stubbed BFF responses, **not** the real backend. The Mac run above
  is the real proof.
