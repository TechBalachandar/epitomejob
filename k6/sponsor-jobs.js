// Quester k6 — sponsor-filtered jobs journey (F6 sponsor alerts).
//
// GET /jobs?sponsorsH1b=true — the filtered list the sponsor-alerts
// feature reads (web/src/lib/jobs.ts sets sponsorsH1b=true when the
// filter is on; the backend filters on Job.sponsorsH1b). The unfiltered
// list is already covered by jobs.js; this script covers only the
// filtered path, including the per-row assertion that every returned
// job actually sponsors H-1B — a wrong filter would pass a status-only
// check and only this check catches it. An empty result is legitimate
// (no sponsoring jobs in the database), so the check is status + valid
// JSON + per-row filter, not a row count. The seeded jobs (V3 migration)
// include 13 sponsorsH1b=TRUE rows out of 18, so in the perf stack the
// list is non-empty.
//
// Thresholds: p95 < 500ms, error rate < 1%, checks > 99% — same as
// jobs.js. Load shape: 5 VUs for 30s, 1s think time — latency smoke
// test, not a load test.
import http from 'k6/http';
import { check, sleep } from 'k6';
import { BASE_URL, registerAndLogin, warmupGet } from './lib/common.js';

export const options = {
  scenarios: {
    sponsorJobs: { executor: 'constant-vus', vus: 5, duration: '30s', gracefulStop: '5s' },
  },
  thresholds: {
    http_req_duration: ['p(95)<500'],
    http_req_failed: ['rate<0.01'],
    checks: ['rate>0.99'],
  },
};

export function setup() {
  const token = registerAndLogin();
  warmupGet(token, '/jobs?sponsorsH1b=true');
  return { token: token };
}

export default function (data) {
  const res = http.get(`${BASE_URL}/jobs?sponsorsH1b=true`, {
    headers: { Authorization: `Bearer ${data.token}` },
    tags: { journey: 'sponsor-jobs' },
  });
  check(res, {
    'sponsor jobs 200': (r) => r.status === 200,
    'sponsor jobs body is JSON page': (r) => r.status === 200 && Array.isArray(r.json('content')),
    'every returned job sponsors H-1B': (r) =>
      r.status === 200 && r.json('content').every((j) => j.sponsorsH1b === true),
  });
  sleep(1);
}
