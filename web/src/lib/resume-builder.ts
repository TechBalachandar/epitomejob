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
    <p class="skills">{{skills}}</p>`;
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

// --- Rendering -------------------------------------------------------------

export function escapeHtml(value: string): string {
  return value
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;");
}

function renderExperience(state: ResumeBuilderState): string {
  if (state.experience.length === 0) return "<p></p>";
  return state.experience
    .map((item) => {
      const dates = [item.startDate, item.endDate].filter(Boolean).join(" – ");
      const bullets = item.bullets.filter((b) => b.trim() !== "");
      const list =
        bullets.length > 0
          ? `<ul>${bullets.map((b) => `<li>${escapeHtml(b)}</li>`).join("")}</ul>`
          : "";
      return `<div class="job"><div class="job-head"><span>${escapeHtml(item.title)}${item.title && item.company ? " — " : ""}${escapeHtml(item.company)}</span><span class="job-dates">${escapeHtml(dates)}</span></div>${list}</div>`;
    })
    .join("");
}

function renderEducation(state: ResumeBuilderState): string {
  if (state.education.length === 0) return "<p></p>";
  return state.education
    .map((item) => {
      const dates = [item.startDate, item.endDate].filter(Boolean).join(" – ");
      return `<div class="job"><div class="job-head"><span>${escapeHtml(item.degree)}${item.degree && item.school ? " — " : ""}${escapeHtml(item.school)}</span><span class="job-dates">${escapeHtml(dates)}</span></div></div>`;
    })
    .join("");
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
): Record<string, string> => ({
  fullName: state.contact.fullName,
  title: state.contact.title,
  email: state.contact.email,
  phone: state.contact.phone,
  location: state.contact.location,
  linkedin: state.contact.linkedin,
  summary: state.summary,
  experience: renderExperience(state),
  education: renderEducation(state),
  skills: state.skills.join(", "),
  // Backend template tokens (resume-builder-service templates use the
  // *Html suffix; the web preview must substitute them too, or the
  // sections render empty — the cleanup regex below strips any token
  // that is not substituted).
  experienceHtml: renderExperience(state),
  educationHtml: renderEducation(state),
  skillsHtml: state.skills.join(", "),
});

/**
 * Render the document through a template's HTML. The gallery thumbnails
 * and the editor preview both call this, so what you see is what the
 * export is built from. A template without {{tokens}} falls back to the
 * built-in layout for its id (default Modern).
 */
export function renderTemplateHtml(
  template: BuilderTemplate,
  state: ResumeBuilderState,
): string {
  const raw = template.html.includes("{{")
    ? template.html
    : builtinTemplateById(template.id).html;
  const values = TOKEN_VALUES(state);
  let html = raw;
  for (const [token, value] of Object.entries(values)) {
    const replacement =
      token === "experience" || token === "education"
        ? value
        : escapeHtml(value);
    html = html.split(`{{${token}}}`).join(replacement);
  }
  // Any token the template did not use is dropped, never shown literally.
  html = html.replace(/\{\{[a-zA-Z]+\}\}/g, "");
  const withPlaceholders = highlightPlaceholders(html);
  if (/<html/i.test(withPlaceholders)) return withPlaceholders;
  return `<!doctype html><html><head><meta charset="utf-8"></head><body>${withPlaceholders}</body></html>`;
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
