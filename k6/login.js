// Quester k6 — login journey (POST /auth/register + POST /auth/login).
//
// Thresholds (kept from the brief; see note below):
//   p95 < 500ms, error rate < 1%, checks > 99%.
// Load shape: 5 VUs for 30s with 1s think time — a latency smoke test, NOT
// a load test. It bounds auth latency on a warm JVM; it does not prove any
// throughput number. setup() warms the path once so the first-iteration
// JIT/cold-start spike does not dominate p95 on shared runners.
// Deviation: none for this journey. Register returns 201 + AuthResponse;
// login returns 200 + AuthResponse (verified against backend DTOs).
import { check, sleep } from 'k6';
import { registerAndLogin } from './lib/common.js';

export const options = {
  scenarios: {
    login: { executor: 'constant-vus', vus: 5, duration: '30s', gracefulStop: '5s' },
  },
  thresholds: {
    http_req_duration: ['p(95)<500'],
    http_req_failed: ['rate<0.01'],
    checks: ['rate>0.99'],
  },
};

export function setup() {
  // Warmup only; the measured logins below each register a fresh user.
  registerAndLogin();
  return {};
}

export default function () {
  // Each iteration is a full fresh-user register + login: this journey
  // measures auth end to end, not a cached session.
  const token = registerAndLogin();
  check(token, { 'got a token': (t) => typeof t === 'string' && t.length > 0 });
  sleep(1);
}
