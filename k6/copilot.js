// Quester k6 — Co-Pilot journey (F2 tailor, F5 cover letter).
//
// POST /resume-builder/documents/{id}/copilot with
// {active_section, user_context, target_jd, resume_state} — the endpoint
// the Tailor + Apply flow and the Cover Letter generator both call.
// Alternating iterations exercise both: even iterations tailor the
// "summary" section against a job description, odd iterations generate a
// cover letter (active_section "cover_letter", the value
// web/src/lib/cover-letter.ts sends).
//
// DEVIATIONS FROM THE STANDARD THRESHOLDS (deliberate, documented —
// same pattern as coach.js):
// 1. Threshold is p95 < 5000ms, NOT p95 < 500ms. A Co-Pilot answer goes
//    resume-builder-service -> agent-brain -> Ollama LLM; 500ms cannot
//    hold an LLM round trip even on fast hardware.
// 2. NO http_req_failed threshold on 502s as such: 502 is a declared
//    EXPECTED status (expectedStatuses: [200, 502]), so k6's
//    http_req_failed only counts transport errors and unexpected
//    statuses. The 502 case is agent-brain down: the proxy throws
//    AgentUnavailableException, mapped to 502 by the service's
//    GlobalExceptionHandler. A 404 is NOT expected and IS a failure:
//    the agent-brain endpoint POST /resume-builder/copilot exists on
//    this branch (agent-brain/app/main.py, added ed11216 Nov 14), so a
//    404 means the route/proxy is broken, not a missing endpoint.
//    (An earlier draft accepted 404s, repeating a stale Nov 14
//    "contract gap" comment in CopilotProxyService.kt — corrected
//    2026-11-18; the endpoint is present.) The check accepts 200 or
//    502, requires a body either way, and on a 200 requires the body
//    to parse as JSON with an `options` array.
// 3. NO checks-rate threshold on option quality. This script measures
//    routing/availability, not LLM latency or rewrite quality — evals
//    own answer quality. See QUESTER.md.
import http from 'k6/http';
import { check, sleep } from 'k6';
import { BASE_URL, registerAndLogin, authHeaders } from './lib/common.js';

export const options = {
  scenarios: {
    copilot: { executor: 'constant-vus', vus: 2, duration: '30s', gracefulStop: '5s' },
  },
  thresholds: {
    http_req_duration: ['p(95)<5000'],
    http_req_failed: ['rate<0.01'],
    checks: ['rate>0.99'],
  },
};

const TAILOR_BODY = {
  active_section: 'summary',
  user_context: 'Senior Software Engineer with 17 years of experience in Java, Spring Boot and Kafka.',
  target_jd: 'Senior Software Engineer, Backend (Java). Build core banking APIs on Java and Spring Boot.',
};

const COVER_LETTER_BODY = {
  active_section: 'cover_letter',
  user_context: 'Write a complete cover letter for the job in target_jd, using only facts from resume_state.',
  target_jd: 'Senior Software Engineer, Backend (Java) at Northwind Health Systems, Reston, VA. Build core banking APIs on Java and Spring Boot.',
};

export function setup() {
  const token = registerAndLogin();

  const templates = http.get(`${BASE_URL}/resume-builder/templates`, {
    headers: { Authorization: `Bearer ${token}` },
    tags: { journey: 'copilot-setup' },
  });
  if (templates.status !== 200 || !Array.isArray(templates.json()) || templates.json().length === 0) {
    throw new Error('copilot setup: no templates returned — cannot create a document');
  }
  const created = http.post(
    `${BASE_URL}/resume-builder/documents`,
    JSON.stringify({ title: 'Quester Perf Copilot', templateId: templates.json()[0].id, state: null }),
    authHeaders(token),
  );
  if (created.status !== 201) {
    throw new Error(`copilot setup: create document failed with ${created.status}`);
  }
  return { token: token, docId: created.json('id') };
}

export default function (data) {
  const params = authHeaders(data.token);
  // 502 (agent-brain down) is an EXPECTED status here — see header.
  // 404 is NOT expected: the endpoint exists, so a 404 is a failure.
  // The mechanism is responseCallback: http.expectedStatuses(...).
  // NOTE: an earlier draft set params.expectedStatuses = [...] instead
  // — that key does not exist in k6's Params, k6 silently ignores it,
  // and the unexpected statuses still counted in http_req_failed
  // (proven on the stub: 88.23% failed with the params-field form, 0%
  // with responseCallback). coach.js had the same latent bug and was
  // fixed the same way.
  params.responseCallback = http.expectedStatuses(200, 502);
  params.tags = { journey: 'copilot' };
  const isCoverLetter = __ITER % 2 === 1;
  const body = isCoverLetter ? COVER_LETTER_BODY : TAILOR_BODY;
  const res = http.post(
    `${BASE_URL}/resume-builder/documents/${data.docId}/copilot`,
    JSON.stringify(body),
    params,
  );
  check(res, {
    'copilot responded (200 ok, or 502 agent down)': (r) =>
      r.status === 200 || r.status === 502,
    'copilot body is present': (r) =>
      (r.status === 200 || r.status === 502) && r.body !== undefined && r.body.length > 0,
    'copilot 200 body has an options array': (r) => {
      if (r.status !== 200) return true;
      try {
        const b = r.json();
        return Array.isArray(b.options);
      } catch {
        return false;
      }
    },
  });
  sleep(2);
}
