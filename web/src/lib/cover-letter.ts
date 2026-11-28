// Cover Letter Generator — pure helpers (no React / Next imports), so
// Vitest can cover the prompt, validation, local save and export-state
// rules without a DOM.
//
// How generation works: the existing Co-Pilot endpoint
// (POST /resume-builder/documents/{id}/copilot) accepts any
// `active_section` string — the agent-brain Co-Pilot forwards it into
// its response and builds options from `user_context` + `target_jd` +
// `resume_state`, with no allowlist on the section name (verified in
// agent-brain/app/graph/copilot.py). So cover letters go through that
// same endpoint with active_section "cover_letter" and a user_context
// that asks for a full letter, not resume bullets. No new BFF route
// was needed.
//
// How saving works: the builder documents API has no document
// type/kind field (ResumeDocument is {userId, title, templateId,
// state, createdAt, updatedAt} — verified in
// resume-builder-service/.../model/ResumeDocument.kt), so a cover
// letter cannot be stored server-side as a distinct kind. Letters are
// therefore saved locally on this device (localStorage). Export still
// reuses the real export route: on export the letter is written into a
// builder document (contact from the resume, letter as the document
// body) and downloaded as PDF / DOCX from that document — the export
// renders the letter as the document body, and the document stays
// listed under Resume Builder titled "Cover Letter — …".

import type { CopilotOption, ResumeBuilderState } from "./types";

/** The active_section value sent to Co-Pilot for a cover letter. */
export const COVER_LETTER_SECTION = "cover_letter";

export interface CoverLetterInput {
  resumeLabel: string;
  jobDescription: string;
  company?: string;
  role?: string;
  hiringManager?: string;
  extraNotes?: string;
}

export interface CoverLetterDraft {
  id: string;
  title: string;
  resumeId: string;
  resumeLabel: string;
  jobDescription: string;
  company: string;
  role: string;
  body: string;
  updatedAt: string;
}

const STORAGE_KEY = "epitomejob.cover-letters.v1";

/** Minimal storage surface, so tests can pass a fake. */
export interface CoverLetterStorage {
  getItem(key: string): string | null;
  setItem(key: string, value: string): void;
  removeItem(key: string): void;
}

/**
 * The user_context sent to Co-Pilot. It asks for a complete cover
 * letter — greeting, 2–3 body paragraphs, closing — as 2 distinct
 * options, each option's content lines being the letter's paragraphs
 * in order. Zero-hallucination rules are stated the same way the
 * resume Co-Pilot states them: only facts from the resume, [X%] /
 * [$Y] / [N+ users] placeholders where a number is missing.
 *
 * NOTE: 2 options, not 3-5. On a CPU-only Mac the local 8B model writes
 * ~5 tok/s, so 3-5 full letters take 5-9 min and always blow the 120s
 * timeout; even the fast 1.5B Co-Pilot model needs ~118s for 3 letters.
 * 2 letters fit in ~80s. Raise this back to 3-5 when the Mac is faster
 * (Apple Silicon) or the Co-Pilot goes async.
 */
export function buildCoverLetterPrompt(input: CoverLetterInput): string {
  const lines = [
    "Write a complete cover letter for the job in target_jd, using only facts from resume_state.",
    `The applicant is ${input.resumeLabel || "the owner of this resume"}.`,
  ];
  if (input.role) lines.push(`The role is ${input.role}.`);
  if (input.company) lines.push(`The company is ${input.company}.`);
  lines.push(
    input.hiringManager
      ? `Address the letter to ${input.hiringManager}.`
      : "Address the letter to the hiring manager (\"Dear Hiring Manager,\") — do not invent a name.",
    "Return 2 distinct full-letter options. Each option's content must be the letter's paragraphs in order: greeting, 2–3 body paragraphs connecting the applicant's experience to the job description, and a closing paragraph with a call to action.",
    "Do not invent employers, titles, dates, or numbers that are not in resume_state. Where a measurable result would strengthen the letter but no number is in the resume, use an editable placeholder such as [X%], [$Y], or [N+ users].",
    "Keep each letter under 400 words.",
  );
  if (input.extraNotes?.trim()) {
    lines.push(`Additional instructions from the applicant: ${input.extraNotes.trim()}`);
  }
  lines.push(`Job description (also in target_jd):\n${input.jobDescription.trim()}`);
  return lines.join("\n");
}

/** One Co-Pilot option becomes the editable letter: its content lines are the paragraphs. */
export function letterFromOption(option: CopilotOption): string {
  return option.bullets
    .map((line) => line.trim())
    .filter(Boolean)
    .join("\n\n");
}

/** Validate before generating: a resume must be selected and a real job description pasted. */
export function validateCoverLetterInputs(input: {
  resumeId: string | null;
  jobDescription: string;
}): string | null {
  if (!input.resumeId) return "Choose a resume to base the cover letter on.";
  if (input.jobDescription.trim().length < 20) {
    return "Paste the job description (at least 20 characters) so the letter can be tailored to it.";
  }
  return null;
}

export function coverLetterTitle(input: {
  resumeLabel: string;
  company?: string;
  role?: string;
}): string {
  const what = [input.role?.trim(), input.company?.trim()]
    .filter(Boolean)
    .join(" at ");
  return what ? `Cover Letter — ${what}` : `Cover Letter — ${input.resumeLabel}`;
}

let nextLocalId = 1;
/** Client id for locally saved letters. */
export function newCoverLetterId(): string {
  return `local-${Date.now()}-${nextLocalId++}`;
}

export function newCoverLetterDraft(input: {
  resumeId: string;
  resumeLabel: string;
  jobDescription: string;
  company?: string;
  role?: string;
  body: string;
}): CoverLetterDraft {
  return {
    id: newCoverLetterId(),
    title: coverLetterTitle({
      resumeLabel: input.resumeLabel,
      company: input.company,
      role: input.role,
    }),
    resumeId: input.resumeId,
    resumeLabel: input.resumeLabel,
    jobDescription: input.jobDescription,
    company: input.company?.trim() ?? "",
    role: input.role?.trim() ?? "",
    body: input.body,
    updatedAt: new Date().toISOString(),
  };
}

/** Load locally saved letters (newest first). Corrupt storage reads as empty, never throws. */
export function loadCoverLetters(storage: CoverLetterStorage): CoverLetterDraft[] {
  try {
    const raw = storage.getItem(STORAGE_KEY);
    if (!raw) return [];
    const parsed: unknown = JSON.parse(raw);
    if (!Array.isArray(parsed)) return [];
    return parsed
      .filter(
        (item): item is CoverLetterDraft =>
          typeof item === "object" &&
          item !== null &&
          typeof (item as CoverLetterDraft).id === "string" &&
          typeof (item as CoverLetterDraft).body === "string",
      )
      .sort((a, b) => (b.updatedAt ?? "").localeCompare(a.updatedAt ?? ""));
  } catch {
    return [];
  }
}

/** Insert or replace one letter by id, newest first. */
export function saveCoverLetter(
  storage: CoverLetterStorage,
  letter: CoverLetterDraft,
): CoverLetterDraft[] {
  const next = [
    { ...letter, updatedAt: new Date().toISOString() },
    ...loadCoverLetters(storage).filter((item) => item.id !== letter.id),
  ];
  storage.setItem(STORAGE_KEY, JSON.stringify(next));
  return next;
}

export function deleteCoverLetter(
  storage: CoverLetterStorage,
  id: string,
): CoverLetterDraft[] {
  const next = loadCoverLetters(storage).filter((item) => item.id !== id);
  storage.setItem(STORAGE_KEY, JSON.stringify(next));
  return next;
}

/**
 * The builder-document state used for export: the resume's contact
 * block plus the letter as the document body. Experience / education /
 * skills stay empty, so the exported file contains only the letter.
 * The export route renders a resume layout, so the letter appears as
 * the document body under the contact header — the editor says so
 * next to the export buttons rather than pretending it is a
 * letterhead template.
 */
export function buildCoverLetterExportState(
  contact: ResumeBuilderState["contact"],
  body: string,
): ResumeBuilderState {
  return {
    contact: { ...contact },
    summary: body,
    experience: [],
    education: [],
    skills: [],
  };
}

/** Download filename for an exported letter. */
export function coverLetterFileName(title: string, format: "pdf" | "docx"): string {
  const slug = title
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "")
    .slice(0, 60);
  return `${slug || "cover-letter"}.${format}`;
}
