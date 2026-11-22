// One API module for the whole app. Components never hardcode a URL and
// never call fetch directly: they use these functions via TanStack Query.
// W1: calls go to the same-origin BFF proxy (/api/*), which forwards to the
// Spring Boot backend. The access token lives in Zustand memory only and is
// sent as a Bearer header; the refresh token is an httpOnly cookie the
// browser sends automatically, so the client never sees it.
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useAuthStore } from "./auth-store";
import type {
  Approval,
  AuthResponse,
  BillingPlan,
  BillingStatus,
  BuilderTemplate,
  CheckoutResult,
  CoachChatResponse,
  CopilotRequest,
  CopilotResponse,
  Entitlement,
  Notification,
  NotificationPage,
  Profile,
  ProfileUpdate,
  ResumeBuilderDocument,
  ResumeBuilderState,
  ResumeItem,
  SaveToProfileResult,
  UnreadCount,
} from "./types";
import {
  BUILTIN_TEMPLATES,
  filenameFromContentDisposition,
  normalizeBuilderDocument,
  normalizeBuilderDocuments,
  normalizeBuilderTemplates,
  normalizeCopilotResponse,
  normalizeSaveToProfile,
} from "./resume-builder";

import { buildJobsQuery } from "./jobs";
import { clearCachedUserData } from "./query-client";
import type {
  AnswerDto,
  Job,
  JobPage,
  Match,
  QuestionDto,
  SessionDto,
  TrackerDto,
  TrackerStatus,
} from "./types";


const BFF = "/api";

export class ApiError extends Error {
  status: number;
  constructor(status: number, message: string) {
    super(message);
    this.status = status;
  }
}

// --- Token refresh (single-flight) ---------------------------------------
// Concurrent 401s share one refresh call: the first caller starts it, the
// rest await the same promise. Exported for tests.
let refreshPromise: Promise<boolean> | null = null;

export function refreshAccessToken(): Promise<boolean> {
  if (refreshPromise) return refreshPromise;
  refreshPromise = (async () => {
    try {
      const res = await fetch(`${BFF}/auth/refresh`, {
        method: "POST",
        credentials: "same-origin",
      });
      if (!res.ok) return false;
      const data = (await res.json()) as AuthResponse;
      // Restore the full session, not just the token: the refresh
      // response carries the user identity, so a page reload re-hydrates
      // the store (userId/email/role) as well as the access token. For
      // the 401-retry path this is a no-op (same user); for a reload it
      // is what keeps the user logged in. Logout always clears the
      // refresh cookie, so a refresh can never re-login a logged-out user.
      useAuthStore.getState().login({
        userId: data.userId,
        email: data.email,
        role: data.role,
        accessToken: data.accessToken,
      });
      return true;
    } catch {
      return false;
    }
  })();
  void refreshPromise.finally(() => {
    refreshPromise = null;
  });
  return refreshPromise;
}

/** Restore the session on app load: refresh via the httpOnly cookie and
 * re-hydrate the user. Returns true when a session is now active. The
 * AppShell redirects to /login only when this returns false — a reload
 * with a live refresh cookie keeps the user on the same page. */
export async function restoreSession(): Promise<boolean> {
  const ok = await refreshAccessToken();
  return ok && useAuthStore.getState().isAuthenticated;
}

async function apiFetch<T>(
  path: string,
  init: RequestInit = {},
  retried = false,
): Promise<T> {
  const token = useAuthStore.getState().accessToken;
  const headers = new Headers(init.headers);
  if (!(init.body instanceof FormData)) {
    headers.set("Content-Type", "application/json");
  }
  if (token) headers.set("Authorization", `Bearer ${token}`);
  const res = await fetch(`${BFF}${path}`, {
    ...init,
    headers,
    credentials: "same-origin",
  });
  // Expired access token: refresh once (via the httpOnly cookie) and retry.
  // Auth endpoints are excluded — a 401 there means bad credentials, and
  // refreshing would only loop. A failed refresh ends the session.
  if (res.status === 401 && !retried && !path.startsWith("/auth/")) {
    const ok = await refreshAccessToken();
    if (ok) return apiFetch<T>(path, init, true);
    useAuthStore.getState().logout();
  }
  if (!res.ok) {
    let message = `Request failed (${res.status})`;
    try {
      const body = (await res.json()) as { message?: string; error?: string };
      message = body.message ?? body.error ?? message;
    } catch {
      // keep the generic message when the body is not JSON
    }
    throw new ApiError(res.status, message);
  }
  if (res.status === 204) return undefined as T;
  return (await res.json()) as T;
}

// --- Auth -----------------------------------------------------------------

export async function loginRequest(input: {
  email: string;
  password: string;
}): Promise<AuthResponse> {
  const session = await apiFetch<AuthResponse>("/auth/login", {
    method: "POST",
    body: JSON.stringify(input),
  });
  // A new identity is signing in: drop everything cached for the previous
  // user so no stale row (resumes, profile, tracker) can be shown to them.
  clearCachedUserData();
  return session;
}

export async function registerRequest(input: {
  email: string;
  fullName: string;
  password: string;
}): Promise<AuthResponse> {
  const session = await apiFetch<AuthResponse>("/auth/register", {
    method: "POST",
    body: JSON.stringify(input),
  });
  clearCachedUserData();
  return session;
}

/** Revoke the refresh token at the backend and clear the local session. */
export async function signOut(): Promise<void> {
  try {
    await apiFetch("/auth/logout", { method: "POST" });
  } catch {
    // Logout is best-effort: the local session is cleared either way.
  }
  useAuthStore.getState().logout();
  // Drop every cached query so the next login starts from an empty cache
  // and the signed-out user's data never lingers in memory.
  clearCachedUserData();
}

// --- Profile ---------------------------------------------------------------

export function useProfile() {
  // The key carries the user id (P0 #16): a cached profile for one user
  // must never be served to another user after an account switch. The
  // query is disabled when signed out, so a logout (userId -> null) does
  // not trigger a refetch for the logged-out user.
  const userId = useAuthStore((s) => s.userId);
  return useQuery({
    queryKey: ["profile", "me", userId],
    queryFn: () => apiFetch<Profile>("/profiles/me"),
    enabled: userId !== null,
  });
}

export function useUpdateProfile() {
  const queryClient = useQueryClient();
  const userId = useAuthStore((s) => s.userId);
  return useMutation({
    mutationFn: (patch: ProfileUpdate) => updateProfile(patch),
    onSuccess: (profile) => {
      queryClient.setQueryData(["profile", "me", userId], profile);
    },
  });
}

/** Plain PUT /profiles/me (used by the approvals page for UPDATE_PROFILE). */
export async function updateProfile(patch: ProfileUpdate): Promise<Profile> {
  return apiFetch<Profile>("/profiles/me", {
    method: "PUT",
    body: JSON.stringify(patch),
  });
}

// --- W3: Coach -----------------------------------------------------------
// POST /api/coach/chat → backend POST /coach/chat → agent-brain POST /chat.
// Synchronous; the agent-brain returns the reply, RAG citations and the
// specialists it used (contract §2). The access token is forwarded by
// apiFetch exactly like every other BFF call.
export async function sendCoachMessage(message: string): Promise<CoachChatResponse> {
  return apiFetch<CoachChatResponse>("/coach/chat", {
    method: "POST",
    body: JSON.stringify({ message }),
  });
}

// --- W4: Billing (contract §1f) ------------------------------------------
// Same-origin BFF calls through apiFetch, so the 401-refresh behaviour is
// identical to every other BFF call. The plans page calls status first;
// plans are only fetched when billing is enabled (the backend 404s on
// /billing/plans while the flag is off).
export async function fetchBillingStatus(): Promise<BillingStatus> {
  return apiFetch<BillingStatus>("/billing/status");
}

export async function fetchBillingPlans(): Promise<BillingPlan[]> {
  return apiFetch<BillingPlan[]>("/billing/plans");
}

export async function createBillingCheckout(
  planCode: string,
): Promise<CheckoutResult> {
  return apiFetch<CheckoutResult>("/billing/checkout", {
    method: "POST",
    body: JSON.stringify({ planCode }),
  });
}

export async function fetchMyEntitlement(): Promise<Entitlement> {
  return apiFetch<Entitlement>("/billing/entitlement/me");
}

// --- Resumes ---------------------------------------------------------------

export function useResumes(enabled = true) {
  // The key carries the user id (P0 #16): a cached resume list for one
  // user must never be served to another user after an account switch.
  // The ["resumes", "me"] invalidations in the upload/rename mutations
  // still match this key (prefix match), so they keep working. The query
  // is disabled when signed out, so a logout (userId -> null) does not
  // trigger a refetch for the logged-out user.
  const userId = useAuthStore((s) => s.userId);
  return useQuery({
    queryKey: ["resumes", "me", userId],
    queryFn: () => apiFetch<ResumeItem[]>("/resumes/me"),
    enabled: enabled && userId !== null,
  });
}

export function useUploadResume() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: File | { file: File; label?: string }) => {
      const file = input instanceof File ? input : input.file;
      const label = input instanceof File ? undefined : input.label?.trim();
      const form = new FormData();
      form.append("file", file);
      // W6: optional multipart label; empty = omitted (backend: null).
      if (label) form.append("label", label);
      return apiFetch<ResumeItem>("/resumes", { method: "POST", body: form });
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["resumes", "me"] });
    },
  });
}

/** W6: rename a resume's label — PUT /resumes/{id} {label} (1–80 chars). */
export async function renameResume(id: string, label: string): Promise<ResumeItem> {
  return apiFetch<ResumeItem>(`/resumes/${encodeURIComponent(id)}`, {
    method: "PUT",
    body: JSON.stringify({ label }),
  });
}

export function useRenameResume() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: { id: string; label: string }) =>
      renameResume(input.id, input.label),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["resumes", "me"] });
    },
  });
}

// --- Approvals -------------------------------------------------------------

export function usePendingApprovals(enabled = true) {
  return useQuery({
    queryKey: ["approvals", "pending"],
    queryFn: () => apiFetch<Approval[]>("/approvals/pending"),
    // Polling fallback: the SSE stream sends the pending list once and
    // completes, so the inbox re-reads every 5s to stay fresh.
    refetchInterval: 5_000,
    enabled,
  });
}

export function useProposeApproval() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: { action: string; payload: string }) =>
      apiFetch<Approval>("/approvals", {
        method: "POST",
        body: JSON.stringify(input),
      }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["approvals", "pending"] });
    },
  });
}

export function useDecideApproval() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: { id: string; decision: "approve" | "reject" }) =>
      apiFetch<Approval>(`/approvals/${input.id}/${input.decision}`, {
        method: "POST",
      }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["approvals", "pending"] });
    },
  });
}

// --- M13: Notifications (contract §2b) ------------------------------------
// Same-origin BFF calls through apiFetch, so the 401-refresh behaviour is
// identical to every other BFF call. The bell polls the unread count every
// 30s; the list itself is fetched on demand (no polling).

export function useNotifications(page = 0, size = 20) {
  return useQuery({
    queryKey: ["notifications", page, size],
    queryFn: () =>
      apiFetch<NotificationPage>(`/notifications/me?page=${page}&size=${size}`),
  });
}

export function useUnreadCount() {
  return useQuery({
    queryKey: ["notifications", "unread-count"],
    queryFn: () => apiFetch<UnreadCount>("/notifications/unread-count"),
    refetchInterval: 30_000,
  });
}

export function useMarkNotificationRead() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) =>
      apiFetch<Notification>(`/notifications/${id}/read`, { method: "POST" }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["notifications"] });
      void queryClient.invalidateQueries({
        queryKey: ["notifications", "unread-count"],
      });
    },
  });
}

export function useMarkAllNotificationsRead() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () =>
      apiFetch<{ readCount: number }>("/notifications/read-all", {
        method: "POST",
      }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["notifications"] });
      void queryClient.invalidateQueries({
        queryKey: ["notifications", "unread-count"],
      });
    },
  });
}

// --- W2: Jobs --------------------------------------------------------------

export interface JobFilters {
  query?: string;
  location?: string;
  workType?: string;
  sponsorsH1b?: boolean;
  /** W6: exact source filter (LINKEDIN / INDEED / MONSTER / NAUKRI / COMPANY / SEED). */
  source?: string;
  page?: number;
  size?: number;
}

export function useJobs(filters: JobFilters = {}, options?: { enabled?: boolean }) {
  const query = buildJobsQuery(filters);
  return useQuery({
    queryKey: ["jobs", filters],
    queryFn: () => apiFetch<JobPage>(`/jobs${query}`),
    enabled: options?.enabled ?? true,
  });
}

export function useJob(id: string | null) {
  return useQuery({
    queryKey: ["jobs", id],
    queryFn: () => apiFetch<Job>(`/jobs/${id}`),
    enabled: !!id,
  });
}

// --- W2: Matches ------------------------------------------------------------
// W6: resumeId selects which resume the matches are scored against.
// No resumeId = the backend's latest READY resume (unchanged behaviour).
export async function fetchMatches(resumeId?: string | null): Promise<Match[]> {
  const suffix = resumeId ? `?resumeId=${encodeURIComponent(resumeId)}` : "";
  return apiFetch<Match[]>(`/matches/me${suffix}`);
}

export function useMatches(resumeId?: string | null) {
  return useQuery({
    queryKey: ["matches", "me", resumeId ?? "latest"],
    queryFn: () => fetchMatches(resumeId),
  });
}

export function useMatch(jobId: string | null, resumeId?: string | null) {
  const suffix = resumeId ? `?resumeId=${encodeURIComponent(resumeId)}` : "";
  return useQuery({
    queryKey: ["matches", jobId, resumeId ?? "latest"],
    queryFn: () => apiFetch<Match>(`/matches/${jobId}${suffix}`),
    enabled: !!jobId,
  });
}

// --- W2: Tracker ------------------------------------------------------------

export function useTracker() {
  return useQuery({
    queryKey: ["tracker", "me"],
    queryFn: () => apiFetch<TrackerDto[]>("/tracker"),
  });
}

export async function fetchTracker(): Promise<TrackerDto[]> {
  return apiFetch<TrackerDto[]>("/tracker");
}

/** Save a job to the tracker (SAVED). A 409 means an entry already exists — return it. */
export async function saveJob(jobId: string, resumeId?: string | null): Promise<TrackerDto> {
  try {
    return await apiFetch<TrackerDto>("/tracker", {
      method: "POST",
      body: JSON.stringify(resumeId ? { jobId, resumeId } : { jobId }),
    });
  } catch (err) {
    if (err instanceof ApiError && err.status === 409) {
      const existing = (await fetchTracker()).find((t) => t.jobId === jobId);
      if (existing) return existing;
    }
    throw err;
  }
}

export function useSaveJob() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: { jobId: string; resumeId?: string | null }) =>
      saveJob(input.jobId, input.resumeId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["tracker", "me"] });
    },
  });
}

export async function updateTrackerEntry(
  id: string,
  patch: { status?: TrackerStatus; notes?: string },
): Promise<TrackerDto> {
  return apiFetch<TrackerDto>(`/tracker/${id}`, {
    method: "PUT",
    body: JSON.stringify(patch),
  });
}

export function useUpdateTracker() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: { id: string; patch: { status?: TrackerStatus; notes?: string } }) =>
      updateTrackerEntry(input.id, input.patch),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["tracker", "me"] });
    },
  });
}

export async function deleteTrackerEntry(id: string): Promise<void> {
  await apiFetch<void>(`/tracker/${id}`, { method: "DELETE" });
}

export function useDeleteTracker() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => deleteTrackerEntry(id),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["tracker", "me"] });
    },
  });
}

// --- W2: Apply flow (contract §6) ------------------------------------------
// 1. Ensure a tracker entry (SAVED) for the job, using the user's first
//    READY resume id when one exists. 2. Propose an APPLY_JOB approval
//    whose payload carries {jobId, resumeId, trackerId}. Nothing is sent
//    until the user approves it in the Approvals inbox; on approval the
//    approvals page flips the tracker entry to APPLIED.
export async function applyForJob(jobId: string, resumeId?: string | null): Promise<Approval> {
  // W6: an explicitly selected resume wins; otherwise the first READY one.
  let selectedResumeId = resumeId ?? null;
  if (!selectedResumeId) {
    const resumes = await apiFetch<ResumeItem[]>("/resumes/me");
    selectedResumeId = resumes.find((r) => r.status === "READY")?.id ?? null;
  }
  const entry = await saveJob(jobId, selectedResumeId);
  return apiFetch<Approval>("/approvals", {
    method: "POST",
    body: JSON.stringify({
      action: "APPLY_JOB",
      payload: JSON.stringify({ jobId, resumeId: selectedResumeId, trackerId: entry.id }),
    }),
  });
}

export function useApplyJob() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: string | { jobId: string; resumeId?: string | null }) =>
      typeof input === "string"
        ? applyForJob(input)
        : applyForJob(input.jobId, input.resumeId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["tracker", "me"] });
      void queryClient.invalidateQueries({ queryKey: ["approvals", "pending"] });
    },
  });
}

// --- W2: Mock interviews ----------------------------------------------------

export function useInterviewQuestions(jobId: string | null) {
  return useQuery({
    queryKey: ["interviews", "questions", jobId],
    queryFn: () => apiFetch<QuestionDto[]>(`/interviews/questions?jobId=${encodeURIComponent(jobId ?? "")}`),
    enabled: !!jobId,
  });
}

export async function createInterviewSession(jobId: string): Promise<SessionDto> {
  return apiFetch<SessionDto>("/interviews/sessions", {
    method: "POST",
    body: JSON.stringify({ jobId }),
  });
}

export function useCreateInterviewSession() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (jobId: string) => createInterviewSession(jobId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["interviews", "sessions"] });
    },
  });
}

export async function answerInterviewQuestion(
  sessionId: string,
  input: { questionId: string; answerText: string },
): Promise<AnswerDto> {
  return apiFetch<AnswerDto>(`/interviews/sessions/${sessionId}/answers`, {
    method: "POST",
    body: JSON.stringify(input),
  });
}

export function useAnswerInterviewQuestion(sessionId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: { questionId: string; answerText: string }) =>
      answerInterviewQuestion(sessionId, input),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["interviews", "sessions", sessionId] });
      void queryClient.invalidateQueries({ queryKey: ["interviews", "sessions"] });
    },
  });
}

export function useInterviewSessions() {
  return useQuery({
    queryKey: ["interviews", "sessions"],
    queryFn: () => apiFetch<SessionDto[]>("/interviews/sessions"),
  });
}

export function useInterviewSession(id: string | null) {
  return useQuery({
    queryKey: ["interviews", "sessions", id],
    queryFn: () => apiFetch<SessionDto>(`/interviews/sessions/${id}`),
    enabled: !!id,
  });
}

// --- Resume Builder --------------------------------------------------------
// Same-origin BFF calls through apiFetch, so the 401-refresh behaviour is
// identical to every other BFF call. The BFF proxies 1:1 to the backend
// /resume-builder/* paths (see src/app/api/resume-builder/**).

/**
 * GET /resume-builder/templates. Falls back to the built-in Modern /
 * Classic / Minimal templates when the backend list is unavailable, so
 * the gallery always renders — the fallback is content, not user data,
 * and a failed list must not block building a resume.
 */
export async function fetchBuilderTemplates(): Promise<BuilderTemplate[]> {
  try {
    const raw = await apiFetch<unknown>("/resume-builder/templates");
    const templates = normalizeBuilderTemplates(raw);
    return templates.length > 0 ? templates : BUILTIN_TEMPLATES;
  } catch {
    return BUILTIN_TEMPLATES;
  }
}

export function useBuilderTemplates() {
  return useQuery({
    queryKey: ["resume-builder", "templates"],
    queryFn: () => fetchBuilderTemplates(),
  });
}

/** POST multipart /resume-builder/parse — returns the parsed document. */
export async function parseBuilderResume(
  file: File,
): Promise<ResumeBuilderDocument> {
  const form = new FormData();
  form.append("file", file);
  const raw = await apiFetch<unknown>("/resume-builder/parse", {
    method: "POST",
    body: form,
  });
  return normalizeBuilderDocument(raw);
}

/** GET /resume-builder/documents — the user's documents, newest first. */
export async function fetchBuilderDocuments(): Promise<ResumeBuilderDocument[]> {
  const raw = await apiFetch<unknown>("/resume-builder/documents");
  return normalizeBuilderDocuments(raw);
}

export function useBuilderDocuments(options?: { enabled?: boolean }) {
  return useQuery({
    queryKey: ["resume-builder", "documents"],
    queryFn: () => fetchBuilderDocuments(),
    enabled: options?.enabled ?? true,
  });
}

/** POST /resume-builder/documents — create a document from state. */
export async function createBuilderDocument(input: {
  state: ResumeBuilderState;
  templateId: string;
  title?: string;
}): Promise<ResumeBuilderDocument> {
  const raw = await apiFetch<unknown>("/resume-builder/documents", {
    method: "POST",
    body: JSON.stringify(input),
  });
  return normalizeBuilderDocument(raw);
}

/** GET /resume-builder/documents/{id} — load a document (?documentId=). */
export async function fetchBuilderDocument(
  id: string,
): Promise<ResumeBuilderDocument> {
  const raw = await apiFetch<unknown>(
    `/resume-builder/documents/${encodeURIComponent(id)}`,
  );
  return normalizeBuilderDocument(raw);
}

/** PUT /resume-builder/documents/{id} — save state / template / title. */
export async function updateBuilderDocument(
  id: string,
  patch: {
    state?: ResumeBuilderState;
    templateId?: string;
    title?: string;
  },
): Promise<ResumeBuilderDocument> {
  const raw = await apiFetch<unknown>(
    `/resume-builder/documents/${encodeURIComponent(id)}`,
    { method: "PUT", body: JSON.stringify(patch) },
  );
  return normalizeBuilderDocument(raw);
}

/** POST /resume-builder/documents/{id}/copilot — 3–5 rewrite options. */
export async function requestCopilot(
  id: string,
  input: CopilotRequest,
): Promise<CopilotResponse> {
  const raw = await apiFetch<unknown>(
    `/resume-builder/documents/${encodeURIComponent(id)}/copilot`,
    { method: "POST", body: JSON.stringify(input) },
  );
  return normalizeCopilotResponse(raw);
}

// Blob variant of apiFetch: same Bearer + single 401-refresh retry, but
// the body is returned as a Blob (export endpoints return a file, not JSON).
async function apiFetchBlob(
  path: string,
  retried = false,
): Promise<{ blob: Blob; filename: string }> {
  const token = useAuthStore.getState().accessToken;
  const headers = new Headers();
  if (token) headers.set("Authorization", `Bearer ${token}`);
  const res = await fetch(`${BFF}${path}`, {
    headers,
    credentials: "same-origin",
  });
  if (res.status === 401 && !retried) {
    const ok = await refreshAccessToken();
    if (ok) return apiFetchBlob(path, true);
    useAuthStore.getState().logout();
  }
  if (!res.ok) {
    let message = `Request failed (${res.status})`;
    try {
      const body = (await res.json()) as { message?: string; error?: string };
      message = body.message ?? body.error ?? message;
    } catch {
      // keep the generic message when the body is not JSON
    }
    throw new ApiError(res.status, message);
  }
  const blob = await res.blob();
  return {
    blob,
    filename: filenameFromContentDisposition(
      res.headers.get("content-disposition"),
      `resume.${path.includes("format=docx") ? "docx" : "pdf"}`,
    ),
  };
}

/**
 * GET /resume-builder/documents/{id}/export?format=pdf|docx — the file
 * plus its server filename. The caller triggers the browser download.
 */
export async function exportBuilderDocument(
  id: string,
  format: "pdf" | "docx",
): Promise<{ blob: Blob; filename: string }> {
  return apiFetchBlob(
    `/resume-builder/documents/${encodeURIComponent(id)}/export?format=${format}`,
  );
}

/** POST .../save-to-profile {label} — returns the saved resume id. */
export async function saveBuilderDocumentToProfile(
  id: string,
  label: string,
): Promise<SaveToProfileResult> {
  const raw = await apiFetch<unknown>(
    `/resume-builder/documents/${encodeURIComponent(id)}/save-to-profile`,
    { method: "POST", body: JSON.stringify({ label }) },
  );
  return normalizeSaveToProfile(raw);
}
