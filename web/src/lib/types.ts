// Shared types. Field names match the real backend contract exactly.
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
  /** JSON-encoded string, e.g. '{"jobId":"job-1","resumeId":"..."}' */
  payload: string;
  status: ApprovalStatus;
  expiresAt: string;
  decidedAt: string | null;
}

export interface Job {
  id: string;
  title: string;
  company: string;
  location: string;
  workMode: "Remote" | "Hybrid" | "Onsite";
  salaryMin: number;
  salaryMax: number;
  sponsorsVisa: boolean;
  postedDaysAgo: number;
  summary: string;
  skills: string[];
}

export interface Match {
  jobId: string;
  score: number;
  reasons: string[];
  gaps: string[];
}

export type TrackerStatus = "SAVED" | "APPLIED" | "INTERVIEW" | "OFFER" | "REJECTED";

export interface TrackerCard {
  id: string;
  jobId: string;
  company: string;
  title: string;
  status: TrackerStatus;
  appliedAt: string;
  note: string;
}

export interface Interview {
  id: string;
  company: string;
  role: string;
  date: string;
  status: "UPCOMING" | "COMPLETED";
  score: number | null;
  feedback: string | null;
}

export interface ChatMessage {
  id: string;
  from: "user" | "coach";
  text: string;
  at: string;
}
