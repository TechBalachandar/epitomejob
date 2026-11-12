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
}

export type ResumeStatus = "PROCESSING" | "READY" | "FAILED";

export interface ResumeItem {
  id: string;
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
