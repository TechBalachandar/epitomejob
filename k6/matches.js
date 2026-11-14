// Quester k6 — matches journey (GET /matches/me).
//
// Thresholds: p95 < 500ms, error rate < 1%, checks > 99%.
// Load shape: 5 VUs for 30s, 1s think time — latency smoke test, not a
// load test. Matches are computed for the signed-in user's latest resume
// (web/src/lib/api.ts: /matches/me); a fresh perf user has no resume and
// no jobs, so an empty match list is a legitimate 200 — the check is
// status + valid JSON, not a match count.
import http from 'k6/http';
import { check, sleep } from 'k6';
import { BASE_URL, registerAndLogin, warmupGet } from './lib/common.js';

export const options = {
  scenarios: {
    matches: { executor: 'constant-vus', vus: 5, duration: '30s', gracefulStop: '5s' },
  },
  thresholds: {
    http_req_duration: ['p(95)<500'],
    http_req_failed: ['rate<0.01'],
    checks: ['rate>0.99'],
  },
};

export function setup() {
  const token = registerAndLogin();
  warmupGet(token, '/matches/me');
  return { token: token };
}

export default function (data) {
  const res = http.get(`${BASE_URL}/matches/me`, {
    headers: { Authorization: `Bearer ${data.token}` },
    tags: { journey: 'matches' },
  });
  check(res, {
    'matches 200': (r) => r.status === 200,
    'matches body is JSON': (r) => r.status === 200 && r.json() !== undefined,
  });
  sleep(1);
}
