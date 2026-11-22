// Interview Prep from the job description — pure logic (no fetch, no React).
//
// Model answers come from the Coach (POST /api/coach/chat, see
// sendCoachMessage in lib/api.ts); the Coach needs Ollama on the user's
// Mac, so nothing here invents an answer. This module only:
//   - builds the resume context the Coach must ground its answers in,
//   - builds the prompt that tells the Coach to ground, not hallucinate,
//   - parses the Coach's free-text reply back into question/answer pairs,
//   - checks each model answer against the resume for cited vs. ungrounded
//     experience (a heuristic flag for the user to verify, not a verdict).
import type {
  Profile,
  ResumeBuilderDocument,
} from "./types";

/** Pasted job descriptions shorter than this are almost certainly a title, not a JD. */
export const MIN_PASTED_JD_LENGTH = 40;

/** The Coach is asked for at most this many questions when it generates them. */
export const PREP_QUESTION_COUNT = 5;

export interface ResumeContext {
  /** Plain-text resume summary sent to the Coach as grounding. */
  text: string;
  /** Human label for where the context came from, shown in the UI. */
  sourceLabel: string;
  /** Companies the user actually worked at (builder experience + none else). */
  companies: string[];
  /** Roles the user actually held. */
  roles: string[];
  /** Skills from the profile and the builder document, de-duplicated. */
  skills: string[];
  /** Schools from the builder document (so education is not flagged). */
  schools: string[];
}

export interface ResumeContextInput {
  profile?: Profile | null;
  /** Newest-first builder documents; the first one is the resume context. */
  builderDocuments?: ResumeBuilderDocument[] | null;
}

function dedupe(items: string[]): string[] {
  const seen = new Set<string>();
  const out: string[] = [];
  for (const item of items) {
    const trimmed = item.trim();
    if (!trimmed) continue;
    const key = trimmed.toLowerCase();
    if (!seen.has(key)) {
      seen.add(key);
      out.push(trimmed);
    }
  }
  return out;
}

/**
 * Build the resume context the Coach must ground model answers in.
 * Prefers the newest Resume Builder document (full experience bullets);
 * falls back to the profile (headline / summary / skills). When neither
 * exists the text is empty and the caller must not generate prep — a
 * model answer with nothing to ground in would be invented experience.
 */
export function buildResumeContext(input: ResumeContextInput): ResumeContext {
  const { profile, builderDocuments } = input;
  const doc = (builderDocuments ?? []).find((d) => d.state) ?? null;
  const state = doc?.state ?? null;

  const companies = dedupe(
    (state?.experience ?? []).map((e) => e.company),
  );
  const roles = dedupe((state?.experience ?? []).map((e) => e.title));
  const schools = dedupe((state?.education ?? []).map((e) => e.school));
  const skills = dedupe([
    ...(profile?.skills ?? []),
    ...(state?.skills ?? []),
  ]);

  const lines: string[] = [];
  if (profile?.headline) lines.push(`Headline: ${profile.headline}`);
  if (profile?.summary) lines.push(`Profile summary: ${profile.summary}`);
  if (state?.summary) lines.push(`Resume summary: ${state.summary}`);
  for (const exp of state?.experience ?? []) {
    const dates = [exp.startDate, exp.endDate].filter(Boolean).join(" – ");
    lines.push(
      `Role: ${exp.title} at ${exp.company}${dates ? ` (${dates})` : ""}`,
    );
    for (const bullet of exp.bullets ?? []) {
      if (bullet.trim()) lines.push(`  - ${bullet.trim()}`);
    }
  }
  for (const edu of state?.education ?? []) {
    lines.push(`Education: ${edu.degree} at ${edu.school}`);
  }
  if (skills.length > 0) lines.push(`Skills: ${skills.join(", ")}`);

  const sourceLabel = doc
    ? `Resume Builder document “${doc.title ?? "Untitled"}”`
    : profile
      ? "your profile"
      : "No resume on file";

  return {
    text: lines.join("\n"),
    sourceLabel,
    companies,
    roles,
    skills,
    schools,
  };
}

/** Pasted-JD validation: null = usable, otherwise the message to show. */
export function validatePastedJD(text: string): string | null {
  if (text.trim().length < MIN_PASTED_JD_LENGTH) {
    return `Paste the full job description (at least ${MIN_PASTED_JD_LENGTH} characters) — a title alone is not enough to prepare from.`;
  }
  return null;
}

export interface PrepPromptInput {
  /** Job picked from the live feed (summary + skills only — no full JD on file). */
  job?: {
    title: string;
    company: string;
    summary: string;
    skills: string[];
  } | null;
  /** Full JD pasted by the user; used instead of the job when present. */
  pastedJD?: string;
  /** The 5 role questions from GET /interviews/questions, when available. */
  questions?: string[];
  resumeContext: ResumeContext;
}

/**
 * Build the Coach message for interview prep. The grounding rules are
 * the point of this prompt: answers may only draw on the resume context
 * supplied here, and anything the resume does not cover must be flagged
 * as a gap, never filled in with an invented role, company, date or
 * number. The Coach replies in a fixed shape the parser below reads.
 */
export function buildPrepPrompt(input: PrepPromptInput): string {
  const { job, pastedJD, questions, resumeContext } = input;
  const lines: string[] = [
    "Help me prepare for a job interview. Draft a model answer for each question below, grounded ONLY in my resume.",
    "",
    "GROUNDING RULES — follow these exactly:",
    "- Use only the roles, companies, skills and results in MY RESUME below. Never invent a role, company, employer, date, metric or project I did not list.",
    "- If my resume has no direct experience for a question, say so in the answer itself (\"No direct experience in your resume for this — …\") and suggest what to draw on instead. Do not fill the gap with invented experience.",
    "- Keep each model answer to 3–5 sentences, specific and in the first person, as if I were answering.",
    "",
  ];

  if (pastedJD?.trim()) {
    lines.push("JOB DESCRIPTION:", pastedJD.trim(), "");
  } else if (job) {
    lines.push(
      `JOB: ${job.title} at ${job.company}`,
      job.summary ? `Summary: ${job.summary}` : "",
      job.skills.length > 0 ? `Skills listed: ${job.skills.join(", ")}` : "",
      "",
    );
  }

  if (questions && questions.length > 0) {
    lines.push("QUESTIONS (draft one model answer per question, in this order):");
    questions.forEach((q, i) => lines.push(`${i + 1}. ${q}`));
  } else {
    lines.push(
      `QUESTIONS: first list the ${PREP_QUESTION_COUNT} most likely interview questions for this job, then draft one model answer per question.`,
    );
  }

  lines.push(
    "",
    "MY RESUME (the only experience you may cite):",
    resumeContext.text,
    "",
    "Reply in this exact format, one block per question:",
    "Q: <the question>",
    "Model answer: <the model answer>",
  );
  return lines.filter((l) => l !== "").join("\n");
}

export interface PrepItem {
  question: string;
  modelAnswer: string;
}

export interface ParsedPrepReply {
  items: PrepItem[];
  /** The raw reply when it could not be parsed into Q/A pairs — shown as received. */
  unparsed: string | null;
}

const QUESTION_START =
  /^(?:Q(?:uestion)?\s*\d*\s*[:.)-]\s*|\d+[.)]\s+)(.+)$/i;
const ANSWER_START = /^(?:Model answer|Suggested answer|Answer|A)\s*:\s*(.*)$/i;

/**
 * Parse a Coach prep reply into question/model-answer pairs. Accepts the
 * prompted "Q: … / Model answer: …" shape and numbered lists
 * ("1. …" with a "Model answer:" line). Anything else is returned
 * unparsed — never reshaped into invented questions or answers.
 */
export function parsePrepReply(reply: string): ParsedPrepReply {
  const text = reply.trim();
  if (!text) return { items: [], unparsed: null };

  const lines = text.split(/\r?\n/);
  const blocks: string[][] = [];
  let current: string[] | null = null;
  for (const line of lines) {
    const q = QUESTION_START.exec(line.trim());
    if (q) {
      if (current) blocks.push(current);
      current = [q[1].trim()];
    } else if (current) {
      current.push(line);
    }
  }
  if (current) blocks.push(current);

  const items: PrepItem[] = [];
  for (const block of blocks) {
    const answerIdx = block.findIndex((l) => ANSWER_START.test(l.trim()));
    if (answerIdx === -1) continue;
    const question = block.slice(0, answerIdx).join(" ").trim();
    const first = ANSWER_START.exec(block[answerIdx].trim())?.[1] ?? "";
    const modelAnswer = [first, ...block.slice(answerIdx + 1)]
      .join("\n")
      .trim();
    if (question && modelAnswer) items.push({ question, modelAnswer });
  }

  if (items.length === 0) return { items: [], unparsed: text };
  return { items, unparsed: null };
}

export interface GroundingCheck {
  citedCompanies: string[];
  citedRoles: string[];
  citedSkills: string[];
  /** Phrases naming an employer/role not found in the resume — verify before using. */
  flaggedPhrases: string[];
}

function containsEntity(haystack: string, entity: string): boolean {
  return haystack.toLowerCase().includes(entity.toLowerCase());
}

/** A captured phrase is grounded if it contains, or is contained in, a known entity. */
function isGroundedEntity(phrase: string, known: string[]): boolean {
  const p = phrase.toLowerCase();
  return known.some((k) => {
    const kl = k.toLowerCase();
    return p.includes(kl) || kl.includes(p);
  });
}

/**
 * Check a model answer against the resume's known companies, roles,
 * skills and schools. Returns what the answer cites, plus phrases of
 * the form "at <Employer>" / "as a <Role>" that name something the
 * resume does not list — a heuristic prompt for the user to verify,
 * not proof the answer is wrong (e.g. a paraphrased title still flags).
 */
export function checkGrounding(
  modelAnswer: string,
  known: {
    companies: string[];
    roles: string[];
    skills: string[];
    schools: string[];
  },
): GroundingCheck {
  const citedCompanies = known.companies.filter((c) =>
    containsEntity(modelAnswer, c),
  );
  const citedRoles = known.roles.filter((r) => containsEntity(modelAnswer, r));
  const citedSkills = known.skills.filter((s) =>
    containsEntity(modelAnswer, s),
  );

  const flagged: string[] = [];
  const addFlag = (phrase: string) => {
    const cleaned = phrase.replace(/[.,;:!?)\]]+$/, "").trim();
    if (cleaned.length < 2) return;
    if (
      isGroundedEntity(cleaned, [
        ...known.companies,
        ...known.roles,
        ...known.schools,
      ])
    ) {
      return;
    }
    if (!flagged.includes(cleaned)) flagged.push(cleaned);
  };

  // "at <Employer>" / "worked at <Employer>" — capitalized phrase only,
  // so lowercase "at work" / "at home" never matches. The preposition
  // itself may be sentence-initial ("At <Employer>"), but the captured
  // words stay case-sensitive; continuation words need 2+ characters so
  // a lone "I" ("At Google I shipped…") is not swept into the phrase.
  const atPattern =
    /\b[Aa]t\s+([A-Z][A-Za-z0-9&.'-]*(?:\s+(?:and\s+)?[A-Z][A-Za-z0-9&.'-]{1,}){0,3})/g;
  for (const m of modelAnswer.matchAll(atPattern)) addFlag(m[1]);

  // "as a/an <Role>" — the role may carry a Senior/Lead/Principal prefix.
  const rolePattern =
    /\b[Aa]s\s+[Aa]n?\s+((?:Senior|Lead|Principal|Staff)\s+)?([A-Z][A-Za-z0-9&.'-]*(?:\s+[A-Z][A-Za-z0-9&.'-]{1,}){0,3})/g;
  for (const m of modelAnswer.matchAll(rolePattern)) {
    addFlag(`${m[1] ?? ""}${m[2]}`.trim());
  }

  return { citedCompanies, citedRoles, citedSkills, flaggedPhrases: flagged };
}
