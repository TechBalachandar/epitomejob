// Quester k6 — Resume Builder documents journey (F2 tailor+apply, F5 cover letter).
//
// Covers the Resume Builder service paths the wow-factor features use that
// had no k6 coverage: GET /resume-builder/templates (seed for setup),
// POST /resume-builder/documents (create), GET /resume-builder/documents
// (list), GET /resume-builder/documents/{id} (read). The LLM-backed
// copilot call itself lives in copilot.js; the export call is a binary
// download and is covered by the manual export check in the perf report,
// not by k6 (k6 would measure PDFBox render time, not the journey).
//
// Self-seeding: setup() registers a fresh user, fetches the template list
// and creates one document from the first template. Create requires a
// valid templateId (ResumeDocumentService.create: blank templateId -> 400),
// so the templates call is a real prerequisite, not a nicety — if the
// template list is empty the run fails loudly in setup() with the reason.
//
// Thresholds: p95 < 500ms, error rate < 1%, checks > 99% — same as the
// other read/write journeys (jobs.js, tracker.js). Load shape: 5 VUs for
// 30s, 1s think time — latency smoke test, not a load test.
import http from 'k6/http';
import { check, sleep } from 'k6';
import { BASE_URL, registerAndLogin, authHeaders, warmupGet } from './lib/common.js';

export const options = {
  scenarios: {
    resumeBuilder: { executor: 'constant-vus', vus: 5, duration: '30s', gracefulStop: '5s' },
  },
  thresholds: {
    http_req_duration: ['p(95)<500'],
    http_req_failed: ['rate<0.01'],
    checks: ['rate>0.99'],
  },
};

export function setup() {
  const token = registerAndLogin();
  const params = authHeaders(token);

  const templates = http.get(`${BASE_URL}/resume-builder/templates`, {
    headers: { Authorization: `Bearer ${token}` },
    tags: { journey: 'resume-builder-setup' },
  });
  check(templates, {
    'templates 200': (r) => r.status === 200,
    'templates is a non-empty list': (r) =>
      r.status === 200 && Array.isArray(r.json()) && r.json().length > 0,
  });
  if (templates.status !== 200 || !Array.isArray(templates.json()) || templates.json().length === 0) {
    throw new Error('resume-builder setup: no templates returned — cannot create a document');
  }
  const templateId = templates.json()[0].id;

  const created = http.post(
    `${BASE_URL}/resume-builder/documents`,
    JSON.stringify({ title: 'Quester Perf Resume', templateId: templateId, state: null }),
    params,
  );
  check(created, {
    'create document 201': (r) => r.status === 201,
    'create returns document id': (r) => r.status === 201 && typeof r.json('id') === 'string',
  });
  if (created.status !== 201) {
    throw new Error(`resume-builder setup: create document failed with ${created.status}`);
  }
  const docId = created.json('id');

  warmupGet(token, '/resume-builder/documents');
  return { token: token, docId: docId };
}

export default function (data) {
  const params = authHeaders(data.token);
  params.tags = { journey: 'resume-builder' };

  const list = http.get(`${BASE_URL}/resume-builder/documents`, params);
  check(list, {
    'documents list 200': (r) => r.status === 200,
    'documents list is JSON array': (r) => r.status === 200 && Array.isArray(r.json()),
    'documents list contains the setup document': (r) =>
      r.status === 200 && r.json().some((d) => d.id === data.docId),
  });

  const one = http.get(`${BASE_URL}/resume-builder/documents/${data.docId}`, params);
  check(one, {
    'document read 200': (r) => r.status === 200,
    'document read returns the same id': (r) => r.status === 200 && r.json('id') === data.docId,
  });
  sleep(1);
}
