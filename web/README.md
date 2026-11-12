# EpitomeJob — Web App (W1: wired to the real backend)

Next.js 16.4.6 + React 19.3.1 + TypeScript 5.9.3 (strict) + Tailwind CSS 4.3.4
+ TanStack Query 5 + Zustand 5 + React Hook Form 7 + Zod 4 + Vitest 5 +
Playwright 1.65. pnpm 10, Node ≥ 20.9 (Node 22 LTS recommended; Node 26
also works).

## W1 — what changed vs W0

W0 was a UI shell on MSW mocks. W1 wires the app to the **real Spring Boot
backend** (Phase 0, Java 21) through a same-origin **BFF proxy** — the UI is
visually identical; only the data layer changed.

### BFF proxy — why, and how

The backend has **no CORS config**, so the browser must never call it
directly. Every browser call goes to a same-origin Next.js route handler
under `src/app/api/**`, which forwards the request to the backend with
`fetch` and returns the backend's status code + JSON untouched:

| BFF route | Backend |
| --- | --- |
| `POST /api/auth/login` | `POST /auth/login` |
| `POST /api/auth/register` | `POST /auth/register` |
| `POST /api/auth/refresh` | `POST /auth/refresh` |
| `POST /api/auth/logout` | `POST /auth/logout` |
| `GET / PUT /api/profiles/me` | `GET / PUT /profiles/me` |
| `POST /api/resumes` (multipart passthrough) | `POST /resumes` |
| `GET /api/resumes/me` | `GET /resumes/me` |
| `POST /api/approvals` | `POST /approvals` |
| `GET /api/approvals/pending` | `GET /approvals/pending` |
| `POST /api/approvals/[id]/approve` | `POST /approvals/[id]/approve` |
| `POST /api/approvals/[id]/reject` | `POST /approvals/[id]/reject` |
| `GET /api/approvals/stream` (SSE passthrough) | `GET /approvals/stream` |

Shared forwarding helpers live in `src/lib/server-api.ts`
(`backendFetch`, `proxyJson`, `sessionResponse`, `resolveAccessToken`).
The backend base URL is `API_URL` if set, else `NEXT_PUBLIC_API_URL`,
else `http://localhost:8080` — see `.env.example`.

### Auth — tokens

- **Access token (15 min):** returned to the browser by the login/register
  BFF handlers and kept in the Zustand store (`src/lib/auth-store.ts`)
  **in memory only** — never localStorage, never a cookie.
- **Refresh token:** the BFF handler sets it as an **httpOnly,
  SameSite=Lax, `path=/` cookie** and never returns it to browser JS.
  The browser sends it automatically on `/api/auth/refresh`.
- **401 handling** (`src/lib/api.ts`): on a 401 from any non-auth endpoint
  the client calls `/api/auth/refresh` once and retries the request once.
  Concurrent 401s share a single refresh call (single-flight). If the
  refresh fails, the session is cleared and the user lands on `/login`.
- **Logout:** the BFF revokes the refresh token at the backend
  (best-effort) and always clears the cookie.
- **SSE:** `EventSource` cannot send an `Authorization` header, so the
  `/api/approvals/stream` handler authenticates from the httpOnly cookie
  instead (refreshing it if needed). The stream sends the pending list
  once and completes; the approvals page also **polls
  `/api/approvals/pending` every 5 s** as a fallback.
- **Known W1 limit:** a full page reload still returns to `/login` — the
  access token lives in memory only and the refresh cookie does not
  restore the session yet. Session restore on load is a follow-up, not
  part of the W1 gate.

### What is still mocked

Jobs, matches, interviews and tracker have **no backend endpoints yet**,
so those pages keep their mock data from `src/lib/mock-data.ts` and work
exactly as in W0. The Coach chat is still keyword-scripted (W3 builds the
real agent brain). The 60-day H-1B countdown is a widget shell.

### MSW is gone in W1

The W0 MSW handlers for auth/profile/resumes/approvals were deleted —
two sources of truth for the same endpoints would be a bug factory. The
`msw` dependency was removed from `package.json` (it remains only as a
transitive dependency of Vitest). If mocks are ever needed again, re-add
`msw` plus fresh handlers; the plain `mock-data.ts` pages never used MSW.

### API client

One module, `src/lib/api.ts`, used by all TanStack Query hooks. No
component calls `fetch` or hardcodes a URL. The approval `payload` is
sent as a **JSON string** (the backend stores it as TEXT), e.g.
`JSON.stringify({ jobId, resumeId })`. Applying on a match now uses the
user's **real first resume id** when one exists.

## Run it (backend first!)

```bash
# 1. Backend (from the repo root)
cd backend
docker compose up -d          # Postgres + RustFS (object storage)
mvn spring-boot:run           # wait for "Started EpitomeJobApplication"

# 2. Web (from the repo root)
cd web
cp .env.example .env.local    # only if you need a non-default backend URL
pnpm install
pnpm dev                      # http://localhost:3000
```

Seeded login: `dev@example.com` / `DevPass123!` (dev-only seed account).
Registering a new user also works — use a fresh unique email each run.

## Tests

```bash
pnpm test          # Vitest — 31 tests (auth store + API client incl. refresh/retry)
pnpm test:e2e      # Playwright — 10 tests (Chromium + 390px mobile); stubs the
                   # BFF with page.route, so no real backend is needed
pnpm lint          # 0 errors (1 pre-existing informational warning on the
                   # profile page's watch() — same as W0, harmless)
npx tsc --noEmit   # strict, clean
pnpm build         # all 12 pages + 12 BFF routes
```

## W1 gate checklist (Phase 0 gate, done through the browser)

- [ ] Register a new user in the browser (unique email) → lands on dashboard
- [ ] Login with real credentials (`dev@example.com` / `DevPass123!`)
- [ ] Profile page loads the real profile; saving updates the completeness score
- [ ] Upload a real PDF → status READY in the list
- [ ] Upload a non-PDF → rejected client-side AND by the backend
- [ ] Apply on a match → approval appears in the inbox → Approve removes it
      (real backend state — survives a page reload while signed in)
- [ ] Approvals inbox shows the pending list (SSE + 5 s polling fallback)
- [ ] Logout clears the session; a protected page redirects to `/login`
- [ ] W0 items still hold: every page renders, no console errors, 390 px holds

## Pages

`/login` · `/register` · `/` dashboard · `/profile` · `/resumes` · `/jobs` ·
`/matches` · `/coach` · `/approvals` · `/interviews` · `/tracker` ·
`/learning` (placeholder linking out — M10 Learning is cut from v1).

## Environment

| Variable | Default | Purpose |
| --- | --- | --- |
| `API_URL` | — | Backend base URL for the BFF (server-side; wins when set) |
| `NEXT_PUBLIC_API_URL` | `http://localhost:8080` | Backend base URL fallback for the BFF |
