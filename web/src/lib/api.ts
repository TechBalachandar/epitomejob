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
  Profile,
  ProfileUpdate,
  ResumeItem,
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
      useAuthStore.getState().setAccessToken(data.accessToken);
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
  return apiFetch<AuthResponse>("/auth/login", {
    method: "POST",
    body: JSON.stringify(input),
  });
}

export async function registerRequest(input: {
  email: string;
  fullName: string;
  password: string;
}): Promise<AuthResponse> {
  return apiFetch<AuthResponse>("/auth/register", {
    method: "POST",
    body: JSON.stringify(input),
  });
}

/** Revoke the refresh token at the backend and clear the local session. */
export async function signOut(): Promise<void> {
  try {
    await apiFetch("/auth/logout", { method: "POST" });
  } catch {
    // Logout is best-effort: the local session is cleared either way.
  }
  useAuthStore.getState().logout();
}

// --- Profile ---------------------------------------------------------------

export function useProfile() {
  return useQuery({
    queryKey: ["profile", "me"],
    queryFn: () => apiFetch<Profile>("/profiles/me"),
  });
}

export function useUpdateProfile() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (patch: ProfileUpdate) =>
      apiFetch<Profile>("/profiles/me", {
        method: "PUT",
        body: JSON.stringify(patch),
      }),
    onSuccess: (profile) => {
      queryClient.setQueryData(["profile", "me"], profile);
    },
  });
}

// --- Resumes ---------------------------------------------------------------

export function useResumes(enabled = true) {
  return useQuery({
    queryKey: ["resumes", "me"],
    queryFn: () => apiFetch<ResumeItem[]>("/resumes/me"),
    enabled,
  });
}

export function useUploadResume() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (file: File) => {
      const form = new FormData();
      form.append("file", file);
      return apiFetch<ResumeItem>("/resumes", { method: "POST", body: form });
    },
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
