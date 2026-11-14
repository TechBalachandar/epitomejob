// Shared types. Field names match the real backend contract exactly
// (W2-API-CONTRACT.md is authoritative; W0/W1 names workMode,
// sponsorsVisa and postedDaysAgo are gone).
// AuthResponse is what the BFF returns: the backend's refreshToken is
// stripped out by the BFF and lives in the httpOnly cookie instead.
export interface AuthResponse {
  accessToken: string;
  expiresInMs: number;
  userId: string;
  email: string;
  role: string;
}

export type WorkAuthorization =
  | "US_CITIZEN"
  | "GREEN_CARD"
  | "EAD"
  | "H1B"
  | "OTHER";

export interface Profile {
  id: string;
  userId: string;
  headline: string;
  summary: string;
  location: string;
  workType: string;
  salaryMin: number | null;
  salaryMax: number | null;
  currency: string;
  visibility: string;
  /** Profile skills, sorted alphabetically by the server, never null. */
  skills: string[];
  /** H-1B 60-day clock start date (ISO "YYYY-MM-DD"), null when unset. */
  h1bClockStartDate: string | null;
  /** W6: LinkedIn profile URL, null when unset. Adds no completeness points. */
  linkedinUrl: string | null;
  /** W6: work authorisation, null when unset. Adds no completeness points. */
  workAuthorization: WorkAuthorization | null;
  completeness: number;
  createdAt: string;
  updatedAt: string;
}

export interface ProfileUpdate {
  headline?: string;
  summary?: string;
  location?: string;
  workType?: string;
  salaryMin?: number | null;
  salaryMax?: number | null;
  skills?: string[];
  h1bClockStartDate?: string | null;
  visibility?: string;
  currency?: string;
  linkedinUrl?: string | null;
  workAuthorization?: WorkAuthorization | null;
}

export type ResumeStatus = "PROCESSING" | "READY" | "FAILED";

export interface ResumeItem {
  id: string;
  /** W6: display label; the API never returns null (falls back to fileName). */
  label: string;
  fileName: string;
  fileType: string;
  fileSize: number;
  status: ResumeStatus;
  createdAt: string;
}

export type ApprovalStatus = "PENDING" | "APPROVED" | "REJECTED" | "EXPIRED";

export interface Approval {
  id: string;
  userId: string;
  action: string;
  /** JSON-encoded string, e.g. '{"jobId":"job-1","resumeId":"...","trackerId":"..."}' */
  payload: string;
  status: ApprovalStatus;
  expiresAt: string;
  decidedAt: string | null;
}

export type WorkType = "Remote" | "Hybrid" | "Onsite";
export type JobRole = "Backend" | "AI" | "Android" | "Platform" | "ML" | "General";

/** W6: which portal / source a job came from (JobDto.source). */
export type JobSource =
  | "LINKEDIN"
  | "INDEED"
  | "MONSTER"
  | "NAUKRI"
  | "COMPANY"
  | "SEED";

export interface Job {
  id: string;
  title: string;
  company: string;
  location: string;
  workType: WorkType;
  salaryMin: number;
  salaryMax: number;
  currency: string;
  sponsorsH1b: boolean;
  skills: string[];
  summary: string;
  role: JobRole;
  postedAt: string;
  /** W6: job source; older stub data without it renders as SEED. */
  source: JobSource;
  /** W6: apply link back to the source portal; null = badge only, no link. */
  sourceUrl: string | null;
}

export interface JobPage {
  content: Job[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface MatchReason {
  label: string;
  points: number;
}

export type MatchSource = "RESUME" | "PROFILE";

export interface Match {
  job: Job;
  score: number;
  reasons: MatchReason[];
  gaps: string[];
  source: MatchSource;
}

export type TrackerStatus = "SAVED" | "APPLIED" | "INTERVIEW" | "OFFER" | "REJECTED";

export interface TrackerDto {
  id: string;
  jobId: string;
  title: string;
  company: string;
  resumeId: string | null;
  status: TrackerStatus;
  notes: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface QuestionDto {
  id: string;
  text: string;
  category: "Behavioral" | "Technical" | "System Design" | "Role-specific";
  role: string;
}

export interface AnswerDto {
  id: string;
  questionId: string;
  questionText: string;
  answerText: string;
  scores: {
    structure: number;
    relevance: number;
    specificity: number;
    conciseness: number;
  };
  overallScore: number;
  feedback: string[];
}

export type InterviewSessionStatus = "IN_PROGRESS" | "COMPLETED";

export interface SessionDto {
  id: string;
  jobId: string;
  jobTitle: string;
  status: InterviewSessionStatus;
  createdAt: string;
  completedAt: string | null;
  answers: AnswerDto[];
  averageScore: number | null;
}

export interface ChatMessage {
  id: string;
  from: "user" | "coach";
  text: string;
  at: string;
  /** RAG citations the agent retrieved for this coach reply (contract §2). */
  citations?: CoachCitation[];
  /** Specialists that worked on this turn, in the order used (contract §2). */
  specialistsUsed?: string[];
}

/** One RAG citation from the agent-brain (§2): docId is "job:<uuid>" or "resume:<uuid>". */
export interface CoachCitation {
  docId: string;
  chunk: string;
  score: number;
  source: string;
}

/** POST /coach/chat response, verbatim from the agent-brain (contract §2). */
export interface CoachChatResponse {
  reply: string;
  citations: CoachCitation[];
  actions: {
    type: string;
    status: string;
    approvalId: string | null;
    detail?: string;
  }[];
  specialistsUsed: string[];
}

// --- W4: Billing (contract §1e/§6 — camelCase on the wire) ----------------

/** GET /billing/status — always 200, even when the flag is off. */
export interface BillingStatus {
  enabled: boolean;
  provider: "stripe" | "fake";
}

/** GET /billing/plans — one seed plan (contract §1b). */
export interface BillingPlan {
  code: string;
  name: string;
  priceCents: number;
  currency: string;
  durationDays: number | null;
  description: string;
  active: boolean;
}

/** POST /billing/checkout — 201. HUMAN_REVIEWED has checkoutUrl null. */
export interface CheckoutResult {
  checkoutSessionId: string;
  provider: string;
  checkoutUrl: string | null;
  status: "CREATED" | "PENDING_MANUAL";
}

/** GET /billing/entitlement/me. */
export interface Entitlement {
  planCode: string | null;
  status: "ACTIVE" | "EXPIRED" | "PENDING_MANUAL" | "NONE";
  expiresAt: string | null;
}

// --- M13: Notifications (in-app only, contract §2c) -----------------------

export interface Notification {
  id: string;
  type: "APPROVAL_PROPOSED" | "INTERVIEW_CREATED" | "TRACKER_CHANGED" | "CLOCK_WARNING";
  title: string;
  body: string;
  link: string;
  isRead: boolean;
  derived: boolean;
  createdAt: string;
}

export interface NotificationPage {
  content: Notification[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface UnreadCount {
  unreadCount: number;
}

// --- Resume Builder (W6 follow-up) ----------------------------------------
// BFF routes under /api/resume-builder/** proxy 1:1 to the backend
// /resume-builder/* paths (templates, parse, documents, copilot, export,
// save-to-profile). No builder service exists in this repo yet, so the
// field names below follow the task contract; the normalisers in
// src/lib/resume-builder.ts also accept the snake_case aliases the
// backend may use, and the gallery falls back to built-in templates when
// the backend list is unavailable.

export interface BuilderContact {
  fullName: string;
  /** Professional title / headline, e.g. "Senior Java Engineer". */
  title: string;
  email: string;
  phone: string;
  location: string;
  linkedin: string;
}

export interface BuilderExperienceItem {
  id: string;
  title: string;
  company: string;
  startDate: string;
  endDate: string;
  /** One bullet per entry; edited as one bullet per line. */
  bullets: string[];
}

export interface BuilderEducationItem {
  id: string;
  school: string;
  degree: string;
  startDate: string;
  endDate: string;
}

/** The whole editable document — one object, owned by the builder page. */
export interface ResumeBuilderState {
  contact: BuilderContact;
  summary: string;
  experience: BuilderExperienceItem[];
  education: BuilderEducationItem[];
  /** Free-form skill names. */
  skills: string[];
}

/**
 * One template from GET /resume-builder/templates. `html` is the template
 * HTML the editor renders: it uses {{fullName}}, {{title}}, {{email}},
 * {{phone}}, {{location}}, {{linkedin}}, {{summary}}, {{experience}},
 * {{education}} and {{skills}} tokens (see renderTemplateHtml). A template
 * whose html carries no tokens renders with the built-in layout for its id.
 */
export interface BuilderTemplate {
  id: string;
  name: string;
  description: string;
  html: string;
}

/** A saved builder document. `id` is null for a never-saved local draft. */
export interface ResumeBuilderDocument {
  id: string | null;
  templateId: string | null;
  title: string | null;
  state: ResumeBuilderState;
  createdAt: string | null;
  updatedAt: string | null;
}

export type BuilderSection =
  | "contact"
  | "summary"
  | "experience"
  | "education"
  | "skills";

/** Which section/block the Co-Pilot panel is improving. */
export interface CopilotTarget {
  section: BuilderSection;
  /** Experience / education item index; undefined for whole sections. */
  index?: number;
  /** Human label shown in the panel heading, e.g. "Experience 1". */
  label: string;
}

/** POST /resume-builder/documents/{id}/copilot request body. */
export interface CopilotRequest {
  active_section: BuilderSection;
  user_context: string;
  target_jd?: string;
  resume_state: ResumeBuilderState;
}

export interface CopilotOption {
  id: string | null;
  /** Angle tag, e.g. "Impact-led" or "ATS-first". */
  angle: string;
  bullets: string[];
  atsKeywords: string[];
}

/** POST .../copilot response: 3–5 options to pick from. */
export interface CopilotResponse {
  options: CopilotOption[];
}

/** POST .../save-to-profile response (also accepts a plain ResumeItem). */
export interface SaveToProfileResult {
  resumeId: string;
  label: string | null;
}

