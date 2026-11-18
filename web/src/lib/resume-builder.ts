// Resume Builder — pure helpers (no React / Next imports), so Vitest can
// cover the rendering, validation and state-patch rules without a DOM.
// The template HTML uses {{token}} placeholders; renderTemplateHtml
// substitutes the escaped document state, renders experience / education /
// skills blocks, and highlights [placeholder] text with an amber <mark>
// so the user sees what still needs a real value.
import type {
  BuilderContact,
  BuilderEducationItem,
  BuilderExperienceItem,
  BuilderSection,
  BuilderTemplate,
  CopilotOption,
  CopilotResponse,
  CopilotTarget,
  ResumeBuilderDocument,
  ResumeBuilderState,
  SaveToProfileResult,
} from "./types";

export const BUILDER_ACCEPT = ".pdf,.doc,.docx";
export const MAX_BUILDER_BYTES = 10 * 1024 * 1024;

const BUILDER_EXTENSIONS = [".pdf", ".doc", ".docx"];

/** Client-side file check; the server re-validates and its error wins. */
export function validateBuilderFile(file: File): string | null {
  const name = file.name.toLowerCase();
  const okExtension = BUILDER_EXTENSIONS.some((ext) => name.endsWith(ext));
  if (!okExtension) {
    return "Choose a .pdf, .doc or .docx file.";
  }
  if (file.size === 0) return "That file is empty. Choose a file with content.";
  if (file.size > MAX_BUILDER_BYTES) {
    return "File is larger than 10 MB. Please choose a smaller file.";
  }
  return null;
}

let nextLocalId = 1;
/** Stable-ish client id for list keys on items created in the editor. */
export function newBuilderItemId(): string {
  return `local-${Date.now()}-${nextLocalId++}`;
}

export function emptyBuilderContact(): BuilderContact {
  return {
    fullName: "",
    title: "",
    email: "",
    phone: "",
    location: "",
    linkedin: "",
  };
}

export function emptyBuilderState(): ResumeBuilderState {
  return {
    contact: emptyBuilderContact(),
    summary: "",
    experience: [],
    education: [],
    skills: [],
  };
}

export function emptyExperienceItem(): BuilderExperienceItem {
  return {
    id: newBuilderItemId(),
    title: "",
    company: "",
    startDate: "",
    endDate: "",
    bullets: [""],
  };
}

export function emptyEducationItem(): BuilderEducationItem {
  return {
    id: newBuilderItemId(),
    school: "",
    degree: "",
    startDate: "",
    endDate: "",
  };
}

/** Sample state used for the gallery thumbnails (never user data). */
export function sampleBuilderState(): ResumeBuilderState {
  return {
    contact: {
      fullName: "Alex Morgan",
      title: "Senior Software Engineer",
      email: "alex.morgan@example.com",
      phone: "(571) 555-0142",
      location: "Reston, VA",
      linkedin: "linkedin.com/in/alexmorgan",
    },
    summary:
      "Software engineer with 8 years building Java and Spring Boot services, focused on reliable APIs and measurable delivery.",
    experience: [
      {
        id: "sample-exp-1",
        title: "Senior Software Engineer",
        company: "Meridian Care Technologies",
        startDate: "2021",
        endDate: "Present",
        bullets: [
          "Led the migration of 12 services to Spring Boot 3, cutting p95 latency by 38%.",
          "Mentored 4 engineers; shipped the claims API used by 200k members a month.",
        ],
      },
    ],
    education: [
      {
        id: "sample-edu-1",
        school: "George Mason University",
        degree: "B.S. Computer Science",
        startDate: "2013",
        endDate: "2017",
      },
    ],
    skills: ["Java", "Spring Boot", "Kafka", "Azure", "PostgreSQL"],
  };
}

// --- Template HTML ---------------------------------------------------------

function builtinTemplateCss(accent: string, font: string): string {
  return `<style>
    * { box-sizing: border-box; margin: 0; padding: 0; }
    body { font-family: ${font}; color: #0f172a; background: #fff; padding: 32px 36px; font-size: 14px; line-height: 1.5; }
    h1 { font-size: 26px; line-height: 1.2; }
    .title { font-size: 15px; color: ${accent}; font-weight: 600; margin-top: 2px; }
    .contact { font-size: 12px; color: #64748b; margin-top: 6px; }
    h2 { font-size: 13px; letter-spacing: .08em; text-transform: uppercase; color: ${accent}; border-bottom: 2px solid ${accent}; padding-bottom: 4px; margin: 18px 0 8px; }
    .job-head { display: flex; justify-content: space-between; gap: 12px; font-weight: 700; }
    .job-dates { font-weight: 400; color: #64748b; white-space: nowrap; }
    .job { margin-bottom: 10px; }
    ul { padding-left: 18px; margin-top: 2px; }
    li { margin-bottom: 2px; }
    .skills { color: #334155; }
    .skills-list { list-style: disc; padding-left: 18px; margin-top: 2px; }
    .skills-list li { margin-bottom: 2px; }
    .skills-cols { column-count: 3; column-gap: 16px; }
    .skills-cols span { display: block; margin-bottom: 2px; }
  </style>`;
}

function templateBody(): string {
  return `<h1>{{fullName}}</h1>
    <div class="title">{{title}}</div>
    <div class="contact">{{email}} · {{phone}} · {{location}} · {{linkedin}}</div>
    <h2>Summary</h2>
    <p>{{summary}}</p>
    <h2>Experience</h2>
    {{experience}}
    <h2>Education</h2>
    {{education}}
    <h2>Skills</h2>
    <div class="skills">{{skills}}</div>`;
}

function builtinTemplateHtml(accent: string, font: string): string {
  return `<!doctype html><html><head><meta charset="utf-8">${builtinTemplateCss(accent, font)}</head><body>${templateBody()}</body></html>`;
}

/**
 * Built-in Modern / Classic / Minimal templates. The gallery uses these
 * when GET /resume-builder/templates is unavailable, so the builder works
 * before the backend ships the endpoint — and the live thumbnails always
 * render from the same HTML the editor preview uses.
 */
export const BUILTIN_TEMPLATES: BuilderTemplate[] = [
  {
    id: "modern",
    name: "Modern",
    description: "Clean indigo accents, bold name, clear section rules.",
    html: builtinTemplateHtml("#4f46e5", "ui-sans-serif, system-ui, sans-serif"),
  },
  {
    id: "classic",
    name: "Classic",
    description: "Serif type and restrained rules — the traditional look.",
    html: builtinTemplateHtml("#334155", "Georgia, 'Times New Roman', serif"),
  },
  {
    id: "minimal",
    name: "Minimal",
    description: "Lots of whitespace, small-caps headings, no decoration.",
    html: builtinTemplateHtml("#0f172a", "ui-sans-serif, system-ui, sans-serif"),
  },
];

export function builtinTemplateById(id: string | null): BuilderTemplate {
  return (
    BUILTIN_TEMPLATES.find((t) => t.id === id) ?? BUILTIN_TEMPLATES[0]
  );
}

// --- Design settings (Designer mode) --------------------------------------
// Every Designer control maps to a real, testable value in this object.
// The preview applies it by injecting one override <style> block into the
// rendered template HTML (see designToCss / applyDesignOverrides), so the
// same settings drive the gallery thumbnails and the editor preview.

export type HeaderAlign = "left" | "center" | "right";
/**
 * Job-head secondary alignment (Teal's "Location Alignment"): where the
 * job's date range sits — grouped with the title/company on the left, or
 * right-aligned opposite it. Our job entries carry no location field, so
 * the date range is the job head's right-side element; the control is
 * named the Teal way and drives the same layout.
 */
export type LocationAlign = "left" | "right";
/** How the skills block renders: comma-joined, a bulleted list, or columns. */
export type SkillsLayout = "comma" | "list" | "columns";

export interface DesignMargins {
  top: number;
  right: number;
  bottom: number;
  left: number;
}

export interface DesignSettings {
  /** Key into FONT_OPTIONS. */
  font: string;
  /** Body line-height multiplier, 1.0 – 2.0. */
  lineHeight: number;
  /** Accent colour (hex) used for the name title, section rules, headings. */
  accent: string;
  /** How a start–end date range is joined in the rendered document. */
  dateSeparator: string;
  /** Alignment of the name / title / contact header block. */
  headerAlign: HeaderAlign;
  /** Job-head layout: dates grouped left with the title/company, or right-aligned. */
  locationAlign: LocationAlign;
  /** How the skills block renders (comma / list / columns). */
  skillsLayout: SkillsLayout;
  /** Page padding (px) — the "margins" of the Letter page. */
  margins: DesignMargins;
}

export const FONT_OPTIONS: { id: string; label: string; stack: string }[] = [
  { id: "sans", label: "Sans (System)", stack: "ui-sans-serif, system-ui, sans-serif" },
  { id: "serif", label: "Serif (Georgia)", stack: "Georgia, 'Times New Roman', serif" },
  { id: "mono", label: "Mono", stack: "ui-monospace, SFMono-Regular, Menlo, monospace" },
  { id: "rounded", label: "Rounded", stack: "'Segoe UI', ui-sans-serif, system-ui, sans-serif" },
];

export const ACCENT_OPTIONS: { id: string; label: string; value: string }[] = [
  { id: "indigo", label: "Indigo", value: "#4f46e5" },
  { id: "teal", label: "Teal", value: "#0f766e" },
  { id: "slate", label: "Slate", value: "#334155" },
  { id: "emerald", label: "Emerald", value: "#047857" },
  { id: "amber", label: "Amber", value: "#b45309" },
  { id: "rose", label: "Rose", value: "#be123c" },
  { id: "sky", label: "Sky", value: "#0369a1" },
  { id: "violet", label: "Violet", value: "#6d28d9" },
];

export const DATE_SEPARATOR_OPTIONS: { id: string; label: string; value: string }[] = [
  { id: "en-dash", label: "2021 – Present (en dash)", value: " – " },
  { id: "em-dash", label: "2021 — Present (em dash)", value: " — " },
  { id: "hyphen", label: "2021 - Present (hyphen)", value: " - " },
  { id: "to", label: "2021 to Present", value: " to " },
];

export const SKILLS_LAYOUT_OPTIONS: { id: SkillsLayout; label: string }[] = [
  { id: "comma", label: "Comma separated" },
  { id: "list", label: "Comma separated list" },
  { id: "columns", label: "Columns" },
];

export const DEFAULT_DESIGN: DesignSettings = {
  font: "sans",
  lineHeight: 1.5,
  accent: "#4f46e5",
  dateSeparator: " – ",
  headerAlign: "left",
  locationAlign: "right",
  skillsLayout: "comma",
  margins: { top: 32, right: 36, bottom: 32, left: 36 },
};

export function fontStackFor(fontId: string): string {
  return FONT_OPTIONS.find((f) => f.id === fontId)?.stack ?? FONT_OPTIONS[0].stack;
}

/**
 * The override CSS for a design. Injected after the template's own
 * <style> so it wins (with !important where the template sets the same
 * property). Pure — the Designer panel and the tests both use it.
 */
export function designToCss(design: DesignSettings): string {
  const { top, right, bottom, left } = design.margins;
  const dateCss =
    design.locationAlign === "right"
      ? `.job-head { display: flex !important; justify-content: space-between !important; gap: 12px; } .job-dates { white-space: nowrap; }`
      : `.job-head { display: block !important; } .job-dates { display: inline !important; margin-left: 8px; }`;
  const skillsCss =
    design.skillsLayout === "list"
      ? `.skills-list { list-style: disc !important; padding-left: 18px !important; margin-top: 2px !important; } .skills-list li { margin-bottom: 2px !important; }`
      : design.skillsLayout === "columns"
        ? `.skills-cols { column-count: 3 !important; column-gap: 16px !important; } .skills-cols span { display: block !important; margin-bottom: 2px !important; }`
        : "";
  return [
    `body { font-family: ${fontStackFor(design.font)} !important; line-height: ${design.lineHeight} !important; padding: ${top}px ${right}px ${bottom}px ${left}px !important; }`,
    `h1, .title, .contact { text-align: ${design.headerAlign}; }`,
    `h2 { color: ${design.accent} !important; border-bottom-color: ${design.accent} !important; }`,
    `.title { color: ${design.accent} !important; }`,
    dateCss,
    skillsCss,
  ].filter(Boolean).join("\n");
}

/** Inject the design override <style> into rendered template HTML. */
export function applyDesignOverrides(html: string, design: DesignSettings): string {
  const style = `<style data-design>\n${designToCss(design)}\n</style>`;
  if (/<\/head>/i.test(html)) return html.replace(/<\/head>/i, `${style}</head>`);
  if (/<body/i.test(html)) return html.replace(/<body[^>]*>/i, (m) => `${m}${style}`);
  return `${style}${html}`;
}

// --- Analyzer (deterministic, derived from the document only) ---------------
// No invented scores: every check counts something actually in the
// document (or, for keywords, in the pasted target job description).
// The score is the share of applicable checks that pass.

export type ResumeCheckCategory = "Structure" | "Measurable results" | "Keyword usage";

export interface ResumeCheck {
  id: string;
  category: ResumeCheckCategory;
  label: string;
  passed: boolean;
  /** Countable detail, e.g. "3 of 5 bullets include a number". */
  detail: string;
  count: number;
  total: number;
}

export interface ResumeAnalysis {
  /** 0–100: the share of applicable checks that pass. */
  score: number;
  checks: ResumeCheck[];
  /** First failing check, for the tip card; null when everything passes. */
  weakest: ResumeCheck | null;
}

const MEASURABLE_RE = /(\d|%|\$|€|£)/;
const KEYWORD_STOPWORDS = new Set([
  "with", "that", "this", "have", "will", "your", "from", "about", "into",
  "over", "under", "more", "most", "such", "than", "then", "when", "what",
  "where", "which", "while", "years", "year", "experience", "work", "team",
  "teams", "role", "roles", "candidate", "candidates", "looking", "join",
  "help", "also", "their", "them", "they", "our", "you", "the", "and",
  "for", "are", "but", "not", "all", "any", "can", "had", "her", "was",
  "one", "out", "day", "days", "get", "has", "him", "his", "how", "man",
  "new", "now", "old", "see", "two", "way", "who", "did", "does", "its",
  "let", "put", "say", "she", "too", "use", "able", "per", "jobs",
]);

/** Top keywords of a job description, by frequency. Words under 4 chars
 * are dropped unless they are known tech terms (aws, sql, api, git, c#,
 * go) — otherwise a JD asking for AWS/SQL/API/Git yields no keywords at
 * all. TECH_TERMS / TECH_WORDS (Job Matcher section below) are the
 * allowlists; they are only read at call time, after the module loads. */
export function keywordsFromJobDescription(jd: string, limit = 20): string[] {
  const counts = new Map<string, number>();
  for (const raw of jd.toLowerCase().split(/[^a-z0-9+#./]+/)) {
    // Strip leading #/. (hashtags, ".net") and trailing ./ ("C#.", "ci/cd/")
    // — but keep a trailing # so "c#" survives as a keyword.
    const word = raw.replace(/^[#./]+|[./]+$/g, "");
    if (word.length === 0 || KEYWORD_STOPWORDS.has(word)) continue;
    // A slash compound is only a keyword when it is a known term (ci/cd);
    // otherwise "and/or" or "site/remote" would pollute the keywords.
    if (word.includes("/") && !TECH_TERMS.has(word)) continue;
    if (word.length < 4 && !TECH_TERMS.has(word) && !TECH_WORDS.has(word)) continue;
    counts.set(word, (counts.get(word) ?? 0) + 1);
  }
  return [...counts.entries()]
    .sort((a, b) => b[1] - a[1] || a[0].localeCompare(b[0]))
    .slice(0, limit)
    .map(([word]) => word);
}

function escapeRegExp(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

/**
 * Whole-token match, not substring: "java" must not match "JavaScript",
 * "react" must not match "reaction", "rust" must not match "trust".
 * A keyword counts only when it is not immediately preceded or followed
 * by a word character. Lookarounds (not \b) are used so keywords ending
 * in a non-word character (c#, c++) still match: after "#" a space is
 * not a word boundary, so \b would fail there. Plurals are tolerated in
 * both directions ("api" matches "APIs", and a JD keyword "apis" matches
 * a resume "API") via an optional trailing "s" — a single "s", not an
 * open-ended suffix, so "test" still does not match "testing".
 *
 * The plural forms are generated, never guessed from a substring:
 * - "-es" plurals are added for any stem ending in s/x/z/ch/sh/o
 *   ("class" → "classes", "box" → "boxes", "tomato" → "tomatoes") —
 *   no length gate, because adding "es" only ever matches a longer,
 *   specific word. The "o" stem is gated to words longer than 2 chars
 *   so the Go language ("go") does not gain the verb form "goes".
 * - The singular fallback (stripping "s") is gated: only for words
 *   longer than 3 chars, and never off a word ending in "ss"/"us"
 *   ("class", "status", "boss", "pass" are singular, not plurals —
 *   stripping gives nonsense stems like "statu"/"clas"/"bos" that can
 *   match real text). An "-es" plural strips "es" ("classes" → "class",
 *   "tomatoes" → "tomato"), never a single "s" ("classe"/"tomatoe" is
 *   nonsense) — except that a stem ending in "o" or "ss"/"us" is tried
 *   both ways, because it may be a truncated e-word ("shoes" → "shoe",
 *   "canoes" → "canoe", "houses" → "house", "reuses" → "reuse") rather
 *   than a true -es plural. That generates the harmless nonsense forms
 *   "tomatoe"/"heroe"/"echoe"/"classe"/"statuse" and the surnames "cano"
 *   and "hous" as residual false positives; all are accepted because
 *   the nonsense forms never occur in real text and the surnames only
 *   fire on JD keywords like "canoes"/"houses", which are practically
 *   impossible, while dropping either form would break a real match.
 *   Only the verbs "do" and "go" push nothing ("does" must not become
 *   "doe", "goes" must not become "goe"). And an "s" stem is only a
 *   true -es plural when it ends in "ss"/"us" ("class", "bus");
 *   otherwise it is a truncated e-word ("doses" → "dose", "cases" →
 *   "case"), so the single-"s" strip is used.
 */
/** Compound terms that imply a base keyword: a resume saying "MySQL"
 * is a resume that knows SQL, so keyword "sql" also matches the SQL
 * family (MySQL, PostgreSQL, NoSQL, SQLite, MSSQL). The reverse does
 * not hold — a JD asking for "MySQL" specifically wants MySQL, so a
 * generic "sql" resume does not match it. Unlike java/JavaScript
 * (different languages, correctly false), MySQL *is* SQL, so this is a
 * genuine under-match without the map. */
const KEYWORD_COMPOUNDS: Record<string, string[]> = {
  sql: ["mysql", "postgresql", "nosql", "sqlite", "mssql", "sqlserver"],
};

/** Single-letter keywords need a stricter right boundary: "+" and "#"
 * are non-word characters, so (?!\w) passes after the "C" in "C++" /
 * "C#", and "&" passes after the "R" in "R&D" — but those are compound
 * terms, not the standalone letter. ("Objective-C" still matches "c":
 * it is a strict superset of C, so that match is defensible.) */
const SINGLE_LETTER_GUARD: Record<string, string> = {
  c: "(?![+#])",
  r: "(?![&])",
};

export function containsKeyword(haystack: string, keyword: string): boolean {
  if (!keyword) return false;
  const base = keyword.toLowerCase();
  const forms = [base];
  // SQL-family compounds (see KEYWORD_COMPOUNDS above).
  forms.push(...(KEYWORD_COMPOUNDS[base] ?? []));
  // "-es" plurals (both directions) — no length gate.
  if (base.endsWith("s")) {
    // Singular words ending in "s" (class, status, bus) take -es.
    forms.push(`${base}es`);
  } else if (/(?:x|z|ch|sh)$/.test(base) || (base.length > 2 && /o$/.test(base))) {
    // Stems ending in x/z/ch/sh/o take -es ("box" → "boxes",
    // "tomato" → "tomatoes"). The "o" stem is gated to words longer
    // than 2 chars so the Go language ("go") does not gain "goes".
    forms.push(`${base}es`);
  }
  // Singular fallback — gated (see the comment above).
  if (base.length > 3) {
    if (base.endsWith("es")) {
      const stem = base.slice(0, -2);
      if (/(?:x|z|ch|sh)$/.test(stem)) {
        // "-es" plural → singular ("boxes" → "box"). No length gate:
        // 3-char x-stems ("box", "tax") are real singulars.
        forms.push(stem);
      } else if (/(?:ss|us)$/.test(stem)) {
        // "-es" plural of a word ending in "ss"/"us" ("classes" →
        // "class", "buses" → "bus"). The single-"s" strip is also
        // tried: the stem may be a truncated e-word ("houses" →
        // "house", "reuses" → "reuse") rather than a true -es plural.
        // The strip is a real word for e-words and nonsense
        // ("classe", "statuse") for true -es plurals — harmless,
        // since the nonsense forms never occur in real text. The
        // stem itself ("hous") is a residual in the same class as
        // "cano": it only fires on a JD keyword like "houses".
        forms.push(stem);
        forms.push(base.slice(0, -1));
      } else if (/o$/.test(stem)) {
        // Stem ends in "o": either a true -es plural ("tomatoes" →
        // "tomato") or a regular plural of a word ending in "e"
        // ("shoes" → "shoe", "canoes" → "canoe"). Only the verbs
        // "do" and "go" push nothing ("does" must not become "doe",
        // "goes" must not become "goe"). A 2-3 char stem ("to",
        // "fo", "wo", "sho", "obo") is a truncated e-word, so only
        // the single-"s" strip is real. A longer stem is tried both
        // ways ("cano" is a surname, but "canoes" must still match
        // "canoe").
        if (stem === "do" || stem === "go") {
          // Verb, not a noun — push nothing.
        } else if (stem.length > 3) {
          forms.push(stem);
          forms.push(base.slice(0, -1));
        } else {
          forms.push(base.slice(0, -1));
        }
      } else {
        // Regular plural that happens to end in "es" ("services" →
        // "service", "doses" → "dose", "cases" → "case").
        forms.push(base.slice(0, -1));
      }
    } else if (base.endsWith("s")) {
      if (!/(?:ss|us)$/.test(base)) forms.push(base.slice(0, -1));
    }
  }
  return forms.some((form) => {
    const guard = SINGLE_LETTER_GUARD[form] ?? "";
    // Single-character forms take no plural: "c" + "s?" would match the
    // token "CS" (Computer Science), a real false positive on most
    // resumes ("B.S. in CS" has no C).
    const plural = form.length > 1 ? "s?" : "";
    return new RegExp(
      `(?<!\\w)${escapeRegExp(form)}${guard}${plural}(?!\\w)`,
      "i",
    ).test(haystack);
  });
}

/** The lowercased text the matchers search: title, summary, experience,
 * education, skills — one string, built once, shared by analyzeResume
 * and matchJobDescription so the two never drift. */
export function resumeHaystack(state: ResumeBuilderState): string {
  return [
    state.contact.title,
    state.summary,
    ...state.experience.flatMap((e) => [e.title, e.company, ...e.bullets]),
    ...state.education.flatMap((e) => [e.school, e.degree]),
    ...state.skills,
  ]
    .join(" ")
    .toLowerCase();
}

export function analyzeResume(
  state: ResumeBuilderState,
  targetJd?: string,
): ResumeAnalysis {
  const checks: ResumeCheck[] = [];
  const contactOk = Boolean(state.contact.fullName.trim()) && Boolean(state.contact.email.trim());
  checks.push({
    id: "contact",
    category: "Structure",
    label: "Contact details complete",
    passed: contactOk,
    detail: contactOk ? "Name and email present" : "Add your name and email",
    count: contactOk ? 1 : 0,
    total: 1,
  });
  const summaryLen = state.summary.trim().length;
  checks.push({
    id: "summary",
    category: "Structure",
    label: "Summary present",
    passed: summaryLen >= 40,
    detail:
      summaryLen === 0
        ? "No summary yet — add 2–3 sentences"
        : `Summary is ${summaryLen} characters`,
    count: summaryLen >= 40 ? 1 : 0,
    total: 1,
  });
  checks.push({
    id: "experience",
    category: "Structure",
    label: "Experience listed",
    passed: state.experience.length >= 1,
    detail:
      state.experience.length === 0
        ? "No roles yet — add your first role"
        : `${state.experience.length} role${state.experience.length === 1 ? "" : "s"}`,
    count: state.experience.length >= 1 ? 1 : 0,
    total: 1,
  });
  checks.push({
    id: "education",
    category: "Structure",
    label: "Education listed",
    passed: state.education.length >= 1,
    detail:
      state.education.length === 0
        ? "No education yet — add your first entry"
        : `${state.education.length} entr${state.education.length === 1 ? "y" : "ies"}`,
    count: state.education.length >= 1 ? 1 : 0,
    total: 1,
  });
  checks.push({
    id: "skills",
    category: "Structure",
    label: "Skills listed",
    passed: state.skills.length >= 3,
    detail: `${state.skills.length} skill${state.skills.length === 1 ? "" : "s"} listed (aim for 3+)`,
    count: state.skills.length >= 3 ? 1 : 0,
    total: 1,
  });
  const bullets = state.experience.flatMap((e) => e.bullets).filter((b) => b.trim() !== "");
  const measurable = bullets.filter((b) => MEASURABLE_RE.test(b)).length;
  checks.push({
    id: "measurable",
    category: "Measurable results",
    label: "Bullets show measurable results",
    passed: bullets.length > 0 && measurable >= Math.ceil(bullets.length / 2),
    detail:
      bullets.length === 0
        ? "No experience bullets yet — add numbers, %, or $ to your impact"
        : `${measurable} of ${bullets.length} bullets include a number or metric`,
    count: measurable,
    total: bullets.length,
  });
  const jd = (targetJd ?? "").trim();
  if (jd.length > 0) {
    const keywords = keywordsFromJobDescription(jd);
    const haystack = resumeHaystack(state);
    // Each keyword is evaluated once, not once per filter.
    const matched = new Map(keywords.map((k) => [k, containsKeyword(haystack, k)]));
    const hits = keywords.filter((k) => matched.get(k));
    const missing = keywords.filter((k) => !matched.get(k));
    checks.push({
      id: "keywords",
      category: "Keyword usage",
      label: "Job keywords covered",
      passed: keywords.length > 0 && hits.length / keywords.length >= 0.5,
      detail:
        keywords.length === 0
          ? "No keywords found in the job description"
          : missing.length === 0
            ? `${hits.length} of ${keywords.length} job keywords appear in your resume`
            : `${hits.length} of ${keywords.length} job keywords appear — missing: ${missing.slice(0, 5).join(", ")}`,
      count: hits.length,
      total: keywords.length,
    });
  }
  const passed = checks.filter((c) => c.passed).length;
  const score = checks.length === 0 ? 0 : Math.round((100 * passed) / checks.length);
  return { score, checks, weakest: checks.find((c) => !c.passed) ?? null };
}

// --- Job Matcher (deterministic, derived from the document + JD only) -------
// The match score is the share of the job description's keywords that
// appear in the resume — counted, never guessed. The skills table lists
// those keywords with a Hard/Soft classification and a matched/missing
// status. The classification is a heuristic (documented): a keyword is a
// Hard Skill when the resume claims it in its skills list or it matches a
// known technical term; everything else is a Soft Skill.

export type SkillMatchStatus = "matched" | "missing";
export type SkillMatchType = "Hard Skill" | "Soft Skill";

export interface SkillMatchRow {
  keyword: string;
  type: SkillMatchType;
  status: SkillMatchStatus;
}

export interface JobMatch {
  /** 0–100 keyword coverage, or null when the JD has no keywords to match. */
  score: number | null;
  totalKeywords: number;
  matchedKeywords: number;
  missingKeywords: string[];
  skills: SkillMatchRow[];
}

const TECH_TERMS = new Set([
  "java", "kotlin", "python", "javascript", "typescript", "react", "angular",
  "vue", "node", "nodejs", "node.js", "spring", "spring boot", "springboot", "kafka",
  "kubernetes", "docker", "aws", "azure", "gcp", "google cloud", "sql",
  "postgres", "postgresql", "mysql", "mongodb", "redis", "graphql", "rest",
  "restful", "api", "microservices", "ci/cd", "cicd", "jenkins", "terraform",
  "linux", "git", "github", "html", "css", "android", "ios", "swift", "go",
  "golang", "rust", "ruby", "rails", "php", "c++", "c#", "c", "r", ".net", "net", "dotnet",
  "machine learning", "ml", "ai", "artificial intelligence", "devops",
  "agile", "scrum", "jira", "figma", "hadoop", "spark", "airflow", "flask",
  "django", "express", "next.js", "nextjs", "nest", "grpc", "oauth", "jwt",
  "selenium", "cypress", "jest", "vitest", "playwright", "maven", "gradle",
  "bash", "shell", "powershell", "nginx", "apache", "tomcat", "elasticsearch",
  "dynamodb", "snowflake", "databricks", "tableau", "power bi", "excel",
  "scala", "perl", "ansible", "prometheus", "grafana", "helm",
]);

/** Every individual word inside TECH_TERMS ("spring boot" → spring, boot;
 * "machine learning" → machine, learning). Keywords are single tokens,
 * so a multi-word term can never match one directly — its words can. */
const TECH_WORDS = new Set(
  [...TECH_TERMS].flatMap((term) => term.split(/[\s/]+/)),
);

function skillTypeOf(keyword: string, resumeSkills: Set<string>): SkillMatchType {
  if (resumeSkills.has(keyword)) return "Hard Skill";
  if (TECH_TERMS.has(keyword) || TECH_WORDS.has(keyword)) return "Hard Skill";
  return "Soft Skill";
}

export function matchJobDescription(
  state: ResumeBuilderState,
  jd: string,
  limit = 12,
): JobMatch {
  const keywords = keywordsFromJobDescription(jd, 20);
  const haystack = resumeHaystack(state);
  const resumeSkills = new Set(state.skills.map((s) => s.toLowerCase().trim()));
  // Each keyword is evaluated once, not once per filter/map (3× before).
  const matched = new Map(keywords.map((k) => [k, containsKeyword(haystack, k)]));
  const hits = keywords.filter((k) => matched.get(k));
  const missing = keywords.filter((k) => !matched.get(k));
  const skills: SkillMatchRow[] = keywords.slice(0, limit).map((keyword) => ({
    keyword,
    type: skillTypeOf(keyword, resumeSkills),
    status: matched.get(keyword) ? "matched" : "missing",
  }));
  // Matched first, then alphabetical — the table reads best that way.
  skills.sort(
    (a, b) =>
      (a.status === b.status ? 0 : a.status === "matched" ? -1 : 1) ||
      a.keyword.localeCompare(b.keyword),
  );
  return {
    score: keywords.length === 0 ? null : Math.round((100 * hits.length) / keywords.length),
    totalKeywords: keywords.length,
    matchedKeywords: hits.length,
    missingKeywords: missing,
    skills,
  };
}

// --- Resume list (home) ----------------------------------------------------
// The list endpoint returns full documents; the home only needs id/title/
// template/updatedAt, but it normalises the whole document so opening one
// never needs a second fetch.

export function normalizeBuilderDocuments(raw: unknown): ResumeBuilderDocument[] {
  const list = Array.isArray(raw)
    ? raw
    : Array.isArray((raw as Record<string, unknown> | null)?.documents)
      ? ((raw as Record<string, unknown>).documents as unknown[])
      : [];
  return list.map((item) => normalizeBuilderDocument(item));
}

/** Case-insensitive title search for the Recent Resumes list. */
export function filterBuilderDocuments(
  docs: ResumeBuilderDocument[],
  query: string,
): ResumeBuilderDocument[] {
  const q = query.trim().toLowerCase();
  if (!q) return docs;
  return docs.filter((d) => (d.title ?? "").toLowerCase().includes(q));
}

export type BuilderDocumentsSort = "recent" | "title";

/** Recent = updatedAt desc (nulls last); title = A–Z. */
export function sortBuilderDocuments(
  docs: ResumeBuilderDocument[],
  sort: BuilderDocumentsSort,
): ResumeBuilderDocument[] {
  const copy = [...docs];
  if (sort === "title") {
    return copy.sort((a, b) =>
      (a.title ?? "").localeCompare(b.title ?? ""),
    );
  }
  return copy.sort((a, b) => {
    const at = a.updatedAt ? new Date(a.updatedAt).getTime() : 0;
    const bt = b.updatedAt ? new Date(b.updatedAt).getTime() : 0;
    return bt - at;
  });
}

// --- Rendering -------------------------------------------------------------

export function escapeHtml(value: string): string {
  return value
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;");
}

function renderExperience(state: ResumeBuilderState, dateSep = " – "): string {
  if (state.experience.length === 0) return "<p></p>";
  return state.experience
    .map((item) => {
      const dates = [item.startDate, item.endDate].filter(Boolean).join(dateSep);
      const bullets = item.bullets.filter((b) => b.trim() !== "");
      const list =
        bullets.length > 0
          ? `<ul>${bullets.map((b) => `<li>${escapeHtml(b)}</li>`).join("")}</ul>`
          : "";
      return `<div class="job"><div class="job-head"><span>${escapeHtml(item.title)}${item.title && item.company ? " — " : ""}${escapeHtml(item.company)}</span><span class="job-dates">${escapeHtml(dates)}</span></div>${list}</div>`;
    })
    .join("");
}

function renderEducation(state: ResumeBuilderState, dateSep = " – "): string {
  if (state.education.length === 0) return "<p></p>";
  return state.education
    .map((item) => {
      const dates = [item.startDate, item.endDate].filter(Boolean).join(dateSep);
      return `<div class="job"><div class="job-head"><span>${escapeHtml(item.degree)}${item.degree && item.school ? " — " : ""}${escapeHtml(item.school)}</span><span class="job-dates">${escapeHtml(dates)}</span></div></div>`;
    })
    .join("");
}

/**
 * Skills block for the chosen layout. Each skill name is escaped; only
 * the wrapping markup is HTML, so the token is substituted raw (like
 * experience / education). "comma" is the historical rendering.
 */
export function renderSkills(
  state: ResumeBuilderState,
  layout: SkillsLayout = "comma",
): string {
  const names = state.skills.filter((s) => s.trim() !== "");
  if (names.length === 0) return "";
  if (layout === "list") {
    return `<ul class="skills-list">${names.map((s) => `<li>${escapeHtml(s)}</li>`).join("")}</ul>`;
  }
  if (layout === "columns") {
    return `<div class="skills-cols">${names.map((s) => `<span>${escapeHtml(s)}</span>`).join("")}</div>`;
  }
  return names.map((s) => escapeHtml(s)).join(", ");
}

/** Amber highlight for [placeholder] text, e.g. [X%] or [company name]. */
export function highlightPlaceholders(html: string): string {
  return html.replace(
    /\[([^\]\[]+)\]/g,
    '<mark style="background:#fde68a;color:#92400e;padding:0 2px;border-radius:2px;">[$1]</mark>',
  );
}

const TOKEN_VALUES = (
  state: ResumeBuilderState,
  dateSep = " – ",
  skillsLayout: SkillsLayout = "comma",
): Record<string, string> => ({
  fullName: state.contact.fullName,
  title: state.contact.title,
  email: state.contact.email,
  phone: state.contact.phone,
  location: state.contact.location,
  linkedin: state.contact.linkedin,
  summary: state.summary,
  experience: renderExperience(state, dateSep),
  education: renderEducation(state, dateSep),
  skills: renderSkills(state, skillsLayout),
  // Backend template tokens (resume-builder-service templates use the
  // *Html suffix; the web preview must substitute them too, or the
  // sections render empty — the cleanup regex below strips any token
  // that is not substituted).
  experienceHtml: renderExperience(state, dateSep),
  educationHtml: renderEducation(state, dateSep),
  skillsHtml: renderSkills(state, skillsLayout),
});

/**
 * Options for the live preview: a Designer design (font, line-height,
 * accent, alignment, margins — applied as one override <style>) and
 * sections hidden from the preview only (the document state is untouched).
 */
export interface RenderTemplateOptions {
  design?: DesignSettings;
  hideSections?: BuilderSection[];
}

function applyHiddenSections(
  state: ResumeBuilderState,
  hideSections: BuilderSection[],
): ResumeBuilderState {
  if (hideSections.length === 0) return state;
  const hide = new Set(hideSections);
  return {
    contact: hide.has("contact") ? emptyBuilderContact() : state.contact,
    summary: hide.has("summary") ? "" : state.summary,
    experience: hide.has("experience") ? [] : state.experience,
    education: hide.has("education") ? [] : state.education,
    skills: hide.has("skills") ? [] : state.skills,
  };
}

/**
 * Render the document through a template's HTML. The gallery thumbnails
 * and the editor preview both call this, so the preview shows the design
 * and hidden sections as the user set them. A template without {{tokens}}
 * falls back to the built-in layout for its id (default Modern). A design,
 * when given, overrides font / line-height / accent / alignment / margins
 * in the preview only — exports are rendered by the backend from the
 * stored document and do NOT apply the preview's design or hidden
 * sections (the backend has no design/visibility fields yet). So the
 * preview is not what the export is built from: hidden sections and
 * Designer choices are preview-only until the backend honours them.
 * Known limitation: the heading-strip below requires the <h2> label to
 * match exactly ("Summary"/"Experience"/"Education"/"Skills"). A future
 * template with "Work Experience", nested markup inside the <h2>, or
 * static text inside the section wrapper would leave an orphan heading
 * or text — no shipped template hits this (all 6 use the exact labels).
 */
export function renderTemplateHtml(
  template: BuilderTemplate,
  state: ResumeBuilderState,
  options?: RenderTemplateOptions,
): string {
  const displayState = applyHiddenSections(state, options?.hideSections ?? []);
  const design = options?.design;
  const raw = template.html.includes("{{")
    ? template.html
    : builtinTemplateById(template.id).html;
  const values = TOKEN_VALUES(
    displayState,
    design?.dateSeparator ?? " – ",
    design?.skillsLayout ?? "comma",
  );
  let html = raw;
  for (const [token, value] of Object.entries(values)) {
    const replacement =
      token === "experience" ||
      token === "education" ||
      token === "skills" ||
      token === "experienceHtml" ||
      token === "educationHtml" ||
      token === "skillsHtml"
        ? value
        : escapeHtml(value);
    html = html.split(`{{${token}}}`).join(replacement);
  }
  // A hidden section must vanish entirely — heading included. Every
  // template (built-in and backend) renders <h2>Label</h2> followed by
  // the section token, so after substitution a hidden section is a
  // heading followed by an empty block (or nothing). Drop the heading
  // with it, or the preview shows an orphan heading for a section the
  // user excluded. The match is case-insensitive on the label and the
  // empty block may be a bare empty element or a wrapper holding only
  // an empty <p></p> (renderExperience renders an empty section as
  // "<p></p>"), so a template that wraps the token in a <div> or
  // <section> is handled too.
  const SECTION_HEADINGS: Record<string, string> = {
    summary: "Summary",
    experience: "Experience",
    education: "Education",
    skills: "Skills",
  };
  for (const section of options?.hideSections ?? []) {
    const label = SECTION_HEADINGS[section];
    if (!label) continue;
    html = html.replace(
      new RegExp(
        `<h2[^>]*>\\s*${label}\\s*<\\/h2>\\s*(?:<([a-z][a-z0-9]*)[^>]*>(?:\\s|<p>\\s*<\\/p>)*<\\/\\1>)?`,
        "gi",
      ),
      "",
    );
  }
  // Contact has no <h2>, so the heading-strip above cannot hide it.
  // Hiding contact empties every contact field, which leaves a
  // separator-only line (" ·  ·  · " built-in, " |  |  | " backend)
  // plus an empty <h1> and title. Strip the whole header block: the
  // contact element whose text is only separators/whitespace, any
  // separator-only paragraph (the backend header's plain <p>), the
  // empty <h1> and title div, and the header wrapper once emptied.
  if ((options?.hideSections ?? []).includes("contact")) {
    html = html.replace(
      /<(div|p)[^>]*class="[^"]*\bcontact\b[^"]*"[^>]*>[\s·|,]*<\/\1>/gi,
      "",
    );
    html = html.replace(/<p[^>]*>[\s·|,]*<\/p>/gi, "");
    html = html.replace(/<h1[^>]*>\s*<\/h1>/gi, "");
    html = html.replace(
      /<div[^>]*class="[^"]*\btitle\b[^"]*"[^>]*>\s*<\/div>/gi,
      "",
    );
    html = html.replace(
      /<div[^>]*class="[^"]*\bheader\b[^"]*"[^>]*>\s*<\/div>/gi,
      "",
    );
  }
  // Any token the template did not use is dropped, never shown literally.
  html = html.replace(/\{\{[a-zA-Z]+\}\}/g, "");
  const withPlaceholders = highlightPlaceholders(html);
  const withDesign = design ? applyDesignOverrides(withPlaceholders, design) : withPlaceholders;
  if (/<html/i.test(withDesign)) return withDesign;
  return `<!doctype html><html><head><meta charset="utf-8"></head><body>${withDesign}</body></html>`;
}

/** True when the rendered document still has [placeholder] text to fill in. */
export function hasPlaceholders(state: ResumeBuilderState): boolean {
  const haystack = [
    state.contact.fullName,
    state.contact.title,
    state.summary,
    ...state.experience.flatMap((e) => [e.title, e.company, ...e.bullets]),
    ...state.education.flatMap((e) => [e.school, e.degree]),
    ...state.skills,
  ].join("\n");
  return /\[[^\]\[]+\]/.test(haystack);
}

// --- Placeholder splitting (AI option cards, React side) -------------------

export interface PlaceholderPart {
  text: string;
  isPlaceholder: boolean;
}

/** Split "[X%] growth" into [{text:"[X%]",isPlaceholder:true}, ...]. */
export function splitPlaceholders(text: string): PlaceholderPart[] {
  const parts: PlaceholderPart[] = [];
  const re = /(\[[^\]\[]+\])/g;
  let last = 0;
  for (const match of text.matchAll(re)) {
    const idx = match.index ?? 0;
    if (idx > last) parts.push({ text: text.slice(last, idx), isPlaceholder: false });
    parts.push({ text: match[0], isPlaceholder: true });
    last = idx + match[0].length;
  }
  if (last < text.length) parts.push({ text: text.slice(last), isPlaceholder: false });
  if (parts.length === 0) parts.push({ text, isPlaceholder: false });
  return parts;
}

// --- Normalisers (backend shape tolerance) ---------------------------------

function asString(value: unknown): string {
  return typeof value === "string" ? value : "";
}

function asStringArray(value: unknown): string[] {
  if (Array.isArray(value)) return value.filter((v): v is string => typeof v === "string");
  if (typeof value === "string" && value.trim() !== "") return [value];
  return [];
}

/** Bullets may arrive as an array or one newline-separated string. */
function asBullets(value: unknown): string[] {
  if (Array.isArray(value)) return asStringArray(value);
  if (typeof value === "string") {
    return value
      .split(/\n+/)
      .map((line) => line.replace(/^[-•\s]+/, "").trim())
      .filter(Boolean);
  }
  return [];
}

function normalizeState(raw: unknown): ResumeBuilderState {
  const r = (raw ?? {}) as Record<string, unknown>;
  const contact = (r.contact ?? {}) as Record<string, unknown>;
  const experience = Array.isArray(r.experience) ? r.experience : [];
  const education = Array.isArray(r.education) ? r.education : [];
  return {
    contact: {
      fullName: asString(contact.fullName ?? contact.full_name),
      title: asString(contact.title),
      email: asString(contact.email),
      phone: asString(contact.phone),
      location: asString(contact.location),
      linkedin: asString(contact.linkedin),
    },
    summary: asString(r.summary),
    experience: experience.map((item) => {
      const it = (item ?? {}) as Record<string, unknown>;
      return {
        id: asString(it.id) || newBuilderItemId(),
        title: asString(it.title),
        company: asString(it.company),
        startDate: asString(it.startDate ?? it.start_date),
        endDate: asString(it.endDate ?? it.end_date),
        bullets: asBullets(it.bullets),
      };
    }),
    education: education.map((item) => {
      const it = (item ?? {}) as Record<string, unknown>;
      return {
        id: asString(it.id) || newBuilderItemId(),
        school: asString(it.school),
        degree: asString(it.degree),
        startDate: asString(it.startDate ?? it.start_date),
        endDate: asString(it.endDate ?? it.end_date),
      };
    }),
    skills: asStringArray(r.skills),
  };
}

export function normalizeBuilderState(raw: unknown): ResumeBuilderState {
  return normalizeState(raw);
}

/** Accepts {id, templateId, title, state} and the snake_case / documentId aliases. */
export function normalizeBuilderDocument(raw: unknown): ResumeBuilderDocument {
  const r = (raw ?? {}) as Record<string, unknown>;
  const stateRaw = r.state ?? r.resume_state ?? r;
  const id = r.id ?? r.documentId ?? r.document_id ?? null;
  return {
    id: typeof id === "string" && id !== "" ? id : null,
    templateId:
      typeof (r.templateId ?? r.template_id) === "string"
        ? (r.templateId ?? r.template_id) as string
        : null,
    title: typeof r.title === "string" ? r.title : null,
    state: normalizeState(stateRaw),
    createdAt: typeof r.createdAt === "string" ? r.createdAt : null,
    updatedAt: typeof r.updatedAt === "string" ? r.updatedAt : null,
  };
}

export function normalizeBuilderTemplates(raw: unknown): BuilderTemplate[] {
  const list = Array.isArray(raw)
    ? raw
    : Array.isArray((raw as Record<string, unknown> | null)?.templates)
      ? ((raw as Record<string, unknown>).templates as unknown[])
      : [];
  return list.map((item) => {
    const t = (item ?? {}) as Record<string, unknown>;
    const id = asString(t.id) || "modern";
    return {
      id,
      name: asString(t.name) || id,
      description: asString(t.description),
      html: typeof t.html === "string" ? t.html : builtinTemplateById(id).html,
    };
  });
}

/** Accepts {options:[...]} or {suggestions:[...]}; snake_case keywords too. */
export function normalizeCopilotResponse(raw: unknown): CopilotResponse {
  const r = (raw ?? {}) as Record<string, unknown>;
  const list = Array.isArray(r.options)
    ? r.options
    : Array.isArray(r.suggestions)
      ? r.suggestions
      : [];
  return {
    options: list.map((item, i) => {
      const o = (item ?? {}) as Record<string, unknown>;
      return {
        id: typeof o.id === "string" ? o.id : `option-${i}`,
        angle: asString(o.angle) || "Suggested rewrite",
        bullets: asBullets(o.bullets ?? o.text),
        atsKeywords: asStringArray(o.atsKeywords ?? o.ats_keywords ?? o.keywords),
      };
    }),
  };
}

/** Accepts {resumeId} or a plain ResumeItem {id, label}. */
export function normalizeSaveToProfile(raw: unknown): SaveToProfileResult {
  const r = (raw ?? {}) as Record<string, unknown>;
  const resumeId = r.resumeId ?? r.resume_id ?? r.id;
  return {
    resumeId: typeof resumeId === "string" ? resumeId : "",
    label: typeof r.label === "string" ? r.label : null,
  };
}

// --- State patches (the editor's single source of truth) -------------------

export function patchContact(
  state: ResumeBuilderState,
  patch: Partial<BuilderContact>,
): ResumeBuilderState {
  return { ...state, contact: { ...state.contact, ...patch } };
}

export function updateExperienceItem(
  state: ResumeBuilderState,
  index: number,
  patch: Partial<BuilderExperienceItem>,
): ResumeBuilderState {
  return {
    ...state,
    experience: state.experience.map((item, i) =>
      i === index ? { ...item, ...patch } : item,
    ),
  };
}

export function addExperienceItem(state: ResumeBuilderState): ResumeBuilderState {
  return { ...state, experience: [...state.experience, emptyExperienceItem()] };
}

export function removeExperienceItem(
  state: ResumeBuilderState,
  index: number,
): ResumeBuilderState {
  return {
    ...state,
    experience: state.experience.filter((_, i) => i !== index),
  };
}

export function updateEducationItem(
  state: ResumeBuilderState,
  index: number,
  patch: Partial<BuilderEducationItem>,
): ResumeBuilderState {
  return {
    ...state,
    education: state.education.map((item, i) =>
      i === index ? { ...item, ...patch } : item,
    ),
  };
}

export function addEducationItem(state: ResumeBuilderState): ResumeBuilderState {
  return { ...state, education: [...state.education, emptyEducationItem()] };
}

export function removeEducationItem(
  state: ResumeBuilderState,
  index: number,
): ResumeBuilderState {
  return {
    ...state,
    education: state.education.filter((_, i) => i !== index),
  };
}

/** The current block text sent as user_context for a Co-Pilot call. */
export function copilotContextFor(
  state: ResumeBuilderState,
  target: CopilotTarget,
): string {
  switch (target.section) {
    case "contact":
      return [state.contact.title, state.contact.fullName]
        .filter(Boolean)
        .join(" — ");
    case "summary":
      return state.summary;
    case "experience": {
      const item = state.experience[target.index ?? 0];
      if (!item) return "";
      return [item.title, item.company, ...item.bullets]
        .filter(Boolean)
        .join("\n");
    }
    case "education": {
      const item = state.education[target.index ?? 0];
      if (!item) return "";
      return [item.degree, item.school].filter(Boolean).join(" — ");
    }
    case "skills":
      return state.skills.join(", ");
  }
}

/**
 * Apply one Co-Pilot option to its block. Summary/contact take the bullets
 * as prose; an experience item takes them as its bullets; education takes
 * them as the degree line; skills replaces the list with the option's
 * bullets + ATS keywords, de-duplicated.
 */
export function applyCopilotOption(
  state: ResumeBuilderState,
  target: CopilotTarget,
  option: CopilotOption,
): ResumeBuilderState {
  switch (target.section) {
    case "contact":
      return patchContact(state, {
        title: option.bullets[0] ?? state.contact.title,
      });
    case "summary":
      return { ...state, summary: option.bullets.join(" ") };
    case "experience": {
      const index = target.index ?? 0;
      if (!state.experience[index]) return state;
      return updateExperienceItem(state, index, { bullets: option.bullets });
    }
    case "education": {
      const index = target.index ?? 0;
      if (!state.education[index]) return state;
      return updateEducationItem(state, index, {
        degree: option.bullets.join(" "),
      });
    }
    case "skills": {
      const merged = [...option.bullets, ...option.atsKeywords]
        .map((s) => s.trim())
        .filter(Boolean);
      return { ...state, skills: [...new Set(merged)] };
    }
  }
}

/** Section label for the Co-Pilot panel heading. */
export function copilotTargetLabel(target: CopilotTarget): string {
  if (target.section === "experience") return `Experience ${(target.index ?? 0) + 1}`;
  if (target.section === "education") return `Education ${(target.index ?? 0) + 1}`;
  return target.section[0].toUpperCase() + target.section.slice(1);
}

// --- Export download -------------------------------------------------------

/** Style label for the Template Library filter, derived from id/name. */
export function templateStyleOf(template: BuilderTemplate): string {
  const haystack = `${template.id} ${template.name}`.toLowerCase();
  for (const style of ["modern", "classic", "minimal", "creative", "professional"]) {
    if (haystack.includes(style)) return style[0].toUpperCase() + style.slice(1);
  }
  return "Other";
}

/**
 * Layout label for the Template Library filter, derived from the template
 * HTML. Two-column layouts declare a grid with two columns or a sidebar;
 * everything else is a single column.
 */
export function templateLayoutOf(template: BuilderTemplate): string {
  const html = template.html.toLowerCase();
  if (
    /grid-template-columns\s*:\s*[^;]*\s[^;]+/.test(html) ||
    /sidebar/.test(html) ||
    /columns\s*:\s*2/.test(html)
  ) {
    return "Two column";
  }
  return "Single column";
}

/** Filename from a Content-Disposition header, else the fallback. */
export function filenameFromContentDisposition(
  header: string | null,
  fallback: string,
): string {
  if (!header) return fallback;
  const match = /filename\*?=(?:UTF-8''|")?([^";\n]+)/i.exec(header);
  if (!match) return fallback;
  try {
    return decodeURIComponent(match[1].replace(/"$/, ""));
  } catch {
    return match[1].replace(/"$/, "");
  }
}

/** Trigger a browser download of a Blob (export endpoints return a file). */
export function triggerBlobDownload(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  a.remove();
  window.setTimeout(() => URL.revokeObjectURL(url), 1000);
}

export type { BuilderSection };
