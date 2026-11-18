// Quester k6 — resume text journey (F3 ATS preview).
//
// GET /resumes/{id}/text returns the parsed text of an uploaded resume —
// the endpoint the ATS preview reads. The resume is uploaded in setup()
// as multipart (POST /resumes, file field) from the minimal fixture PDF
// k6/fixtures/minimal-resume.pdf; upload parses synchronously
// (ResumeService.upload sets parsedText before returning 201), so the
// text read right after it must be 200. If the parse produced no text
// the endpoint answers 400 (ResumeNotReadyException) and setup() fails
// loudly instead of measuring a 400 loop.
//
// Thresholds: p95 < 500ms, error rate < 1%, checks > 99% — same as the
// other read journeys (jobs.js, tracker.js). Load shape: 2 VUs for 30s,
// 1s think time — latency smoke test, not a load test. The upload itself
// is setup-only traffic (one file per run); the measured journey is the
// text read, which is what the ATS preview hammers on every keystroke
// of the preview pane.
import http from 'k6/http';
import { check, sleep } from 'k6';
import { BASE_URL, registerAndLogin, authHeaders } from './lib/common.js';

const pdf = open('./fixtures/minimal-resume.pdf', 'b');

export const options = {
  scenarios: {
    resumeText: { executor: 'constant-vus', vus: 2, duration: '30s', gracefulStop: '5s' },
  },
  thresholds: {
    http_req_duration: ['p(95)<500'],
    http_req_failed: ['rate<0.01'],
    checks: ['rate>0.99'],
  },
};

export function setup() {
  const token = registerAndLogin();

  const file = http.file(pdf, 'minimal-resume.pdf', 'application/pdf');
  const upload = http.post(
    `${BASE_URL}/resumes`,
    { file: file, label: 'Quester Perf Resume' },
    { headers: { Authorization: `Bearer ${token}` }, tags: { journey: 'resume-text-setup' } },
  );
  check(upload, {
    'resume upload 201': (r) => r.status === 201,
    'upload returns resume id': (r) => r.status === 201 && typeof r.json('id') === 'string',
  });
  if (upload.status !== 201) {
    throw new Error(`resume-text setup: resume upload failed with ${upload.status}`);
  }
  const resumeId = upload.json('id');

  const text = http.get(`${BASE_URL}/resumes/${resumeId}/text`, {
    headers: { Authorization: `Bearer ${token}` },
    tags: { journey: 'resume-text-setup' },
  });
  check(text, {
    'resume text 200 after upload': (r) => r.status === 200,
    'resume text body has non-empty text': (r) =>
      r.status === 200 && typeof r.json('text') === 'string' && r.json('text').length > 0,
  });
  if (text.status !== 200) {
    throw new Error(
      `resume-text setup: GET /resumes/${resumeId}/text returned ${text.status} — parse produced no text`,
    );
  }
  return { token: token, resumeId: resumeId };
}

export default function (data) {
  const res = http.get(`${BASE_URL}/resumes/${data.resumeId}/text`, {
    headers: { Authorization: `Bearer ${data.token}` },
    tags: { journey: 'resume-text' },
  });
  check(res, {
    'resume text 200': (r) => r.status === 200,
    'resume text body has text': (r) =>
      r.status === 200 && typeof r.json('text') === 'string' && r.json('text').length > 0,
    'resume text is the uploaded resume': (r) => r.status === 200 && r.json('resumeId') === data.resumeId,
  });
  sleep(1);
}
