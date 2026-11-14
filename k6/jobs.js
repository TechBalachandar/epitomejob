// Quester k6 — jobs list journey (GET /jobs).
//
// Thresholds: p95 < 500ms, error rate < 1%, checks > 99%.
// Load shape: 5 VUs for 30s, 1s think time — latency smoke test, not a
// load test. Lists the first page with no filters, exactly like the jobs
// page's default load (web/src/lib/api.ts: apiFetch(`/jobs${query}`) with
// an empty query). No job id is assumed: the perf database starts empty,
// so the list may legitimately be empty — the check is status + valid
// JSON, not a row count.
import http from 'k6/http';
import { check, sleep } from 'k6';
import { BASE_URL, registerAndLogin, warmupGet } from './lib/common.js';

export const options = {
  scenarios: {
    jobs: { executor: 'constant-vus', vus: 5, duration: '30s', gracefulStop: '5s' },
  },
  thresholds: {
    http_req_duration: ['p(95)<500'],
    http_req_failed: ['rate<0.01'],
    checks: ['rate>0.99'],
  },
};

export function setup() {
  const token = registerAndLogin();
  warmupGet(token, '/jobs');
  return { token: token };
}

export default function (data) {
  const res = http.get(`${BASE_URL}/jobs`, {
    headers: { Authorization: `Bearer ${data.token}` },
    tags: { journey: 'jobs' },
  });
  check(res, {
    'jobs 200': (r) => r.status === 200,
    'jobs body is JSON': (r) => r.status === 200 && r.json() !== undefined,
  });
  sleep(1);
}
