// Quester k6 — tracker journey (GET /tracker/me).
//
// NOTE (coordinator fix, 2026-11-14): the first draft hit bare GET
// /tracker, which does not exist — TrackerController only has POST
// /tracker, GET /tracker/me, PUT /tracker/{id}, DELETE /tracker/{id}
// (verified against the live controller at main @ 3855e76). A bare GET
// would 405 and fail every check on the first real run.
//
// Thresholds: p95 < 500ms, error rate < 1%, checks > 99%.
// Load shape: 5 VUs for 30s, 1s think time — latency smoke test, not a
// load test. A fresh perf user has no tracker entries, so an empty list
// is a legitimate 200 — the check is status + valid JSON, not a row count.
import http from 'k6/http';
import { check, sleep } from 'k6';
import { BASE_URL, registerAndLogin, warmupGet } from './lib/common.js';

export const options = {
  scenarios: {
    tracker: { executor: 'constant-vus', vus: 5, duration: '30s', gracefulStop: '5s' },
  },
  thresholds: {
    http_req_duration: ['p(95)<500'],
    http_req_failed: ['rate<0.01'],
    checks: ['rate>0.99'],
  },
};

export function setup() {
  const token = registerAndLogin();
  warmupGet(token, '/tracker/me');
  return { token: token };
}

export default function (data) {
  const res = http.get(`${BASE_URL}/tracker/me`, {
    headers: { Authorization: `Bearer ${data.token}` },
    tags: { journey: 'tracker' },
  });
  check(res, {
    'tracker 200': (r) => r.status === 200,
    'tracker body is JSON': (r) => r.status === 200 && r.json() !== undefined,
  });
  sleep(1);
}
