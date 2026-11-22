// Quester k6 — interview questions journey (F4 interview prep).
//
// GET /interviews/questions?jobId= — 5 questions for the job's role.
// The jobId is required (missing -> 400) and must be a real job
// (unknown -> 404), so setup() takes the first id from GET /jobs. The
// seeded jobs from the V3 migration (18 rows, 6 roles x 5-question bank)
// guarantee a non-empty list in a fresh perf database; if the list is
// empty the run fails loudly in setup() with the reason instead of
// measuring a 404 loop. The POST /coach/chat leg of interview prep is
// already covered by coach.js and is not duplicated here.
//
// Thresholds: p95 < 500ms, error rate < 1%, checks > 99% — same as the
// other read journeys (jobs.js, tracker.js). Load shape: 5 VUs for 30s,
// 1s think time — latency smoke test, not a load test.
import http from 'k6/http';
import { check, sleep } from 'k6';
import { BASE_URL, registerAndLogin, authHeaders, warmupGet } from './lib/common.js';

export const options = {
  scenarios: {
    interviews: { executor: 'constant-vus', vus: 5, duration: '30s', gracefulStop: '5s' },
  },
  thresholds: {
    http_req_duration: ['p(95)<500'],
    http_req_failed: ['rate<0.01'],
    checks: ['rate>0.99'],
  },
};

export function setup() {
  const token = registerAndLogin();
  const jobs = http.get(`${BASE_URL}/jobs?size=1`, {
    headers: { Authorization: `Bearer ${token}` },
    tags: { journey: 'interviews-setup' },
  });
  check(jobs, {
    'seeded jobs 200': (r) => r.status === 200,
    'seeded jobs non-empty': (r) =>
      r.status === 200 && Array.isArray(r.json('content')) && r.json('content').length > 0,
  });
  if (jobs.status !== 200 || !Array.isArray(jobs.json('content')) || jobs.json('content').length === 0) {
    throw new Error('interviews setup: GET /jobs returned no seeded jobs — no jobId for /interviews/questions');
  }
  const jobId = jobs.json('content')[0].id;
  warmupGet(token, `/interviews/questions?jobId=${jobId}`);
  return { token: token, jobId: jobId };
}

export default function (data) {
  const res = http.get(`${BASE_URL}/interviews/questions?jobId=${data.jobId}`, {
    headers: { Authorization: `Bearer ${data.token}` },
    tags: { journey: 'interviews' },
  });
  check(res, {
    'questions 200': (r) => r.status === 200,
    'questions body is a JSON array': (r) => r.status === 200 && Array.isArray(r.json()),
    'questions returns 5 questions': (r) => r.status === 200 && r.json().length === 5,
  });
  sleep(1);
}
