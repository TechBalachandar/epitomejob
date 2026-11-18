// Quester k6 — approvals journey (F2 tailor+apply gate).
//
// POST /approvals proposes an apply approval, POST /approvals/{id}/approve
// decides it — the human-in-the-loop gate every Apply click goes through.
// The payload is a JSON *string* (the backend stores it as TEXT):
// {"jobId":"...","resumeId":"..."}, not an object. Action APPLY_JOB is
// the value the web UI sends (AppConstants.Approval.ACTION_APPLY_JOB).
// Approve only flips the status (ApprovalService.decide) — no side-effect
// execution happens, so proposing + approving in a loop is safe and does
// not create tracker entries or send applications.
//
// The jobId comes from the seeded jobs (V3 migration inserts 18 jobs, so
// GET /jobs is non-empty in a fresh perf database); the resumeId is null
// on a fresh perf user and is sent as such — the approval payload is
// opaque to the backend, only the apply flow interprets it.
//
// Thresholds: p95 < 500ms, error rate < 1%, checks > 99% — same as the
// other write journeys. Load shape: 2 VUs for 30s, 1s think time —
// latency smoke test, not a load test.
import http from 'k6/http';
import { check, sleep } from 'k6';
import { BASE_URL, registerAndLogin, authHeaders } from './lib/common.js';

export const options = {
  scenarios: {
    approvals: { executor: 'constant-vus', vus: 2, duration: '30s', gracefulStop: '5s' },
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
    tags: { journey: 'approvals-setup' },
  });
  check(jobs, {
    'seeded jobs 200': (r) => r.status === 200,
    'seeded jobs non-empty': (r) =>
      r.status === 200 && Array.isArray(r.json('content')) && r.json('content').length > 0,
  });
  if (jobs.status !== 200 || !Array.isArray(jobs.json('content')) || jobs.json('content').length === 0) {
    throw new Error('approvals setup: GET /jobs returned no seeded jobs — cannot propose an apply');
  }
  return { token: token, jobId: jobs.json('content')[0].id };
}

export default function (data) {
  const params = authHeaders(data.token);
  params.tags = { journey: 'approvals' };

  const payload = JSON.stringify({ jobId: data.jobId, resumeId: null });
  const proposed = http.post(
    `${BASE_URL}/approvals`,
    JSON.stringify({ action: 'APPLY_JOB', payload: payload }),
    params,
  );
  check(proposed, {
    'propose approval 201': (r) => r.status === 201,
    'propose returns approval id': (r) => r.status === 201 && typeof r.json('id') === 'string',
    'proposed approval is pending': (r) => r.status === 201 && r.json('status') === 'PENDING',
  });
  if (proposed.status !== 201) {
    sleep(1);
    return;
  }
  const approvalId = proposed.json('id');

  const decided = http.post(`${BASE_URL}/approvals/${approvalId}/approve`, null, params);
  check(decided, {
    'approve 200': (r) => r.status === 200,
    'approved status is APPROVED': (r) => r.status === 200 && r.json('status') === 'APPROVED',
  });
  sleep(1);
}
