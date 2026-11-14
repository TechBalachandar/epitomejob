// Quester k6 — profile journey (GET /profiles/me).
//
// Thresholds: p95 < 500ms, error rate < 1%, checks > 99%.
// Load shape: 5 VUs for 30s, 1s think time — latency smoke test, not a
// load test. One token is minted in setup() and shared by all VUs: this
// journey measures the read path, not auth.
import http from 'k6/http';
import { check, sleep } from 'k6';
import { BASE_URL, registerAndLogin, warmupGet } from './lib/common.js';

export const options = {
  scenarios: {
    profile: { executor: 'constant-vus', vus: 5, duration: '30s', gracefulStop: '5s' },
  },
  thresholds: {
    http_req_duration: ['p(95)<500'],
    http_req_failed: ['rate<0.01'],
    checks: ['rate>0.99'],
  },
};

export function setup() {
  const token = registerAndLogin();
  warmupGet(token, '/profiles/me');
  return { token: token };
}

export default function (data) {
  const res = http.get(`${BASE_URL}/profiles/me`, {
    headers: { Authorization: `Bearer ${data.token}` },
    tags: { journey: 'profile' },
  });
  check(res, {
    'profile 200': (r) => r.status === 200,
    'profile body is JSON': (r) => r.status === 200 && r.json() !== undefined,
  });
  sleep(1);
}
