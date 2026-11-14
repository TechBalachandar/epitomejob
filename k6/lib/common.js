// Quester k6 shared helpers — core API journeys for EpitomeJob.
//
// Base URL is the gateway (http://localhost:8080 in the perf workflow),
// the public entry point, exactly like the web BFF uses. Backend paths are
// the same paths the BFF forwards (/auth/login, /profiles/me, /jobs,
// /matches/me, /tracker, /coach/chat); the gateway preserves them (no
// StripPrefix — verified against web/src/lib/server-api.ts + api.ts).
//
// Auth shapes (verified against backend DTOs at main @ 3855e76):
//   POST /auth/register { email, fullName, password } -> 201 + AuthResponse
//   POST /auth/login    { email, password }           -> AuthResponse
//   AuthResponse: { accessToken, refreshToken, expiresInMs, userId, email, role }
//
// Perf runs are self-seeding: every script registers a fresh unique user
// (email contains a timestamp + random suffix, so runs never collide) and
// logs in. No seeded user, no secret, no fixture is needed.
//
// Warmup: setup() does one register+login and one hit of the journey
// endpoint, tagged warmup=true. perf-report.py excludes warmup-tagged
// points from p95; k6 thresholds themselves include setup traffic, which
// only makes them (very slightly) stricter — documented, not hidden.
import http from 'k6/http';
import { check } from 'k6';

export const BASE_URL = __ENV.K6_BASE_URL || 'http://localhost:8080';

function uniqueEmail(prefix) {
  const suffix = `${Date.now()}-${Math.floor(Math.random() * 1000000)}`;
  return `${prefix}-${suffix}@quester.perf`;
}

export function registerAndLogin() {
  const email = uniqueEmail('quester');
  const password = 'QuesterPerf1!';
  const payload = JSON.stringify({
    email: email,
    fullName: 'Quester Perf',
    password: password,
  });
  const params = { headers: { 'Content-Type': 'application/json' } };
  const reg = http.post(`${BASE_URL}/auth/register`, payload, params);
  check(reg, {
    'register 201': (r) => r.status === 201,
    'register returns accessToken': (r) => r.status === 201 && r.json('accessToken') !== undefined,
  });
  // Register already returns a session, but login is exercised explicitly:
  // the login journey is a measured endpoint of its own (see login.js).
  const login = http.post(
    `${BASE_URL}/auth/login`,
    JSON.stringify({ email: email, password: password }),
    params,
  );
  check(login, {
    'login 200': (r) => r.status === 200,
    'login returns accessToken': (r) => r.status === 200 && typeof r.json('accessToken') === 'string',
  });
  return login.json('accessToken');
}

export function authHeaders(token) {
  return {
    headers: {
      Authorization: `Bearer ${token}`,
      'Content-Type': 'application/json',
    },
  };
}

// One warmup GET of a journey endpoint, tagged so the report can exclude it.
export function warmupGet(token, path) {
  http.get(`${BASE_URL}${path}`, {
    headers: { Authorization: `Bearer ${token}` },
    tags: { journey: path, warmup: 'true' },
  });
}
