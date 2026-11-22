// ATS parse preview — pure helpers (no React / Next imports), so Vitest
// can cover the flagging rules without a DOM.
//
// What this does: given the REAL parsed text returned by
// GET /resumes/{id}/text (the backend's PDFBox / POI extraction, stored
// as parsedText), it reports only what is observable in that text —
// missing contact fields, sections that are absent or empty, and
// characters the parser could not decode. It renders the raw parsed
// text exactly as the parser produced it; nothing is re-formatted.
//
// What it deliberately does NOT do:
// - No simulated ATS scoring and no invented rules about what an ATS
//   "likes": there is no ATS here, only the parser output.
// - No per-field confidence scores: the backend parse output exposes
//   only the text (ResumeTextResponse {resumeId, text}) — no
//   confidence, so none is shown or invented. ATS_HAS_CONFIDENCE is
//   false and the UI says so.
// - No claim about content the parser dropped: without the source file
//   there is nothing to compare the parsed text against, so a section
//   that is absent from the text is reported as "not found in the
//   parsed text", never as "the parser dropped it".

export type AtsSectionName = "Summary" | "Experience" | "Education" | "Skills";

export type AtsSectionStatus = "present" | "empty" | "missing";

export interface AtsSectionResult {
  name: AtsSectionName;
  status: AtsSectionStatus;
  /** Countable detail, e.g. "3 lines" / "heading found, no content lines". */
  detail: string;
  contentLines: number;
}

export interface AtsFlag {
  id: string;
  /** "missing" = something expected is absent; "warning" = text the parser could not read cleanly. */
  severity: "missing" | "warning";
  message: string;
}

export interface AtsAnalysis {
  /** False when the backend returned no parsed text at all. */
  hasText: boolean;
  characters: number;
  words: number;
  lines: number;
  hasEmail: boolean;
  hasPhone: boolean;
  hasLinkedin: boolean;
  sections: AtsSectionResult[];
  /** Lines containing U+FFFD (the parser could not decode those characters). */
  undecodableLines: number;
  flags: AtsFlag[];
}

/** The backend parse output carries no per-field confidence. */
export const ATS_HAS_CONFIDENCE = false;

export const ATS_NO_CONFIDENCE_NOTE =
  "No confidence scores: the backend parse output returns only the parsed text, with no per-field confidence, so this view flags only what is missing, empty, or undecodable in that text.";

const EMAIL_RE = /[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/;
// Mirrors the parser's phone heuristic (ResumeStateParser PHONE): a
// 10-digit North-American number, optional country code / separators.
// The area code is required: with it optional, a 7-digit number such
// as "555-0142" matched, contradicting the 10-digit rule.
const PHONE_RE = /(\+?1[\s.-]?)?\(?\d{3}\)?[\s.-]?\d{3}[\s.-]\d{4}/;
const LINKEDIN_RE = /linkedin\.com\/in\/[^\s,;]+/i;
const REPLACEMENT_CHAR = "�";

/**
 * Section headings, mirroring the backend ResumeStateParser heading
 * variants exactly (SUMMARY / PROFILE / OBJECTIVE / ABOUT …,
 * EXPERIENCE / WORK EXPERIENCE / WORK HISTORY …, EDUCATION …,
 * SKILLS / TECHNICAL SKILLS …). A trailing colon is stripped, as the
 * parser does.
 */
const SECTION_HEADINGS: Record<AtsSectionName, string[]> = {
  Summary: [
    "SUMMARY",
    "PROFILE",
    "OBJECTIVE",
    "ABOUT",
    "EXECUTIVE SUMMARY",
    "PROFESSIONAL SUMMARY",
    "CAREER SUMMARY",
    "PROFESSIONAL PROFILE",
    "CAREER PROFILE",
    "PROFESSIONAL OBJECTIVE",
    "CAREER OBJECTIVE",
  ],
  Experience: [
    "EXPERIENCE",
    "WORK EXPERIENCE",
    "WORK HISTORY",
    "EMPLOYMENT",
    "PROFESSIONAL EXPERIENCE",
    "EMPLOYMENT HISTORY",
  ],
  Education: [
    "EDUCATION",
    "EDUCATION & CERTIFICATIONS",
    "EDUCATION AND CERTIFICATIONS",
  ],
  Skills: [
    "SKILLS",
    "TECHNICAL SKILLS",
    "CORE SKILLS",
    "SKILLS & TECHNOLOGIES",
  ],
};

const ALL_HEADINGS = new Map<string, AtsSectionName>();
for (const [name, variants] of Object.entries(SECTION_HEADINGS) as [
  AtsSectionName,
  string[],
][]) {
  for (const variant of variants) ALL_HEADINGS.set(variant, name);
}

function headingOf(line: string): AtsSectionName | null {
  let upper = line.trim().toUpperCase();
  if (upper.endsWith(":")) upper = upper.slice(0, -1);
  return ALL_HEADINGS.get(upper) ?? null;
}

/** Non-empty lines of the parsed text, in order. */
export function parsedTextLines(text: string): string[] {
  return text
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter((line) => line.length > 0);
}

function analyzeSections(lines: string[]): AtsSectionResult[] {
  const order: AtsSectionName[] = ["Summary", "Experience", "Education", "Skills"];
  const found = new Map<AtsSectionName, number>();
  for (let i = 0; i < lines.length; i++) {
    const section = headingOf(lines[i]);
    if (section && !found.has(section)) found.set(section, i);
  }
  return order.map((name) => {
    const at = found.get(name);
    if (at === undefined) {
      return {
        name,
        status: "missing" as const,
        detail: "Not found in the parsed text",
        contentLines: 0,
      };
    }
    // Content lines run until the next recognised heading (or end).
    let contentLines = 0;
    for (let i = at + 1; i < lines.length; i++) {
      if (headingOf(lines[i])) break;
      contentLines++;
    }
    if (contentLines === 0) {
      return {
        name,
        status: "empty" as const,
        detail: "Heading found, but no content lines follow it",
        contentLines: 0,
      };
    }
    return {
      name,
      status: "present" as const,
      detail: `${contentLines} line${contentLines === 1 ? "" : "s"}`,
      contentLines,
    };
  });
}

/**
 * Flag what the parser could not read cleanly, from observable evidence
 * only: missing contact fields, empty/missing sections, undecodable
 * characters. Nothing is scored.
 */
export function analyzeParsedText(text: string | null | undefined): AtsAnalysis {
  const raw = typeof text === "string" ? text : "";
  const lines = parsedTextLines(raw);
  const hasText = raw.trim().length > 0;
  const words = hasText ? raw.trim().split(/\s+/).length : 0;
  const undecodableLines = lines.filter((line) =>
    line.includes(REPLACEMENT_CHAR),
  ).length;
  const sections = analyzeSections(lines);
  const hasEmail = EMAIL_RE.test(raw);
  const hasPhone = PHONE_RE.test(raw);
  const hasLinkedin = LINKEDIN_RE.test(raw);

  const flags: AtsFlag[] = [];
  if (!hasText) {
    flags.push({
      id: "no-text",
      severity: "missing",
      message:
        "No parsed text — the parser returned nothing for this file, so there is nothing an ATS could read.",
    });
    return {
      hasText,
      characters: 0,
      words: 0,
      lines: 0,
      hasEmail: false,
      hasPhone: false,
      hasLinkedin: false,
      sections,
      undecodableLines: 0,
      flags,
    };
  }
  if (!hasEmail) {
    flags.push({
      id: "no-email",
      severity: "missing",
      message: "No email address found in the parsed text.",
    });
  }
  if (!hasPhone) {
    flags.push({
      id: "no-phone",
      severity: "missing",
      message: "No phone number found in the parsed text.",
    });
  }
  for (const section of sections) {
    if (section.status === "missing") {
      flags.push({
        id: `section-missing-${section.name.toLowerCase()}`,
        severity: "missing",
        message:
          `${section.name} not found in the parsed text — either it is missing from the resume or the parser did not recognise its heading.`,
      });
    } else if (section.status === "empty") {
      flags.push({
        id: `section-empty-${section.name.toLowerCase()}`,
        severity: "warning",
        message: `${section.name} heading is present, but no content lines follow it in the parsed text.`,
      });
    }
  }
  if (undecodableLines > 0) {
    flags.push({
      id: "undecodable",
      severity: "warning",
      message: `${undecodableLines} line${undecodableLines === 1 ? "" : "s"} contain${undecodableLines === 1 ? "s" : ""} characters the parser could not decode (shown as �).`,
    });
  }
  return {
    hasText,
    characters: raw.length,
    words,
    lines: lines.length,
    hasEmail,
    hasPhone,
    hasLinkedin,
    sections,
    undecodableLines,
    flags,
  };
}

export interface ResumeTextResult {
  resumeId: string;
  text: string | null;
}

/**
 * Normalise the GET /resumes/{id}/text response ({resumeId, text}).
 * Returns null when the shape is not a resume-text response at all.
 */
export function normalizeResumeText(raw: unknown): ResumeTextResult | null {
  if (typeof raw !== "object" || raw === null) return null;
  const r = raw as Record<string, unknown>;
  if (typeof r.resumeId !== "string" || r.resumeId === "") return null;
  return {
    resumeId: r.resumeId,
    text: typeof r.text === "string" ? r.text : null,
  };
}
