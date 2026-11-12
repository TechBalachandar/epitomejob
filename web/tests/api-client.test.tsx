// API client tests. fetch is mocked at the BFF boundary (/api/*) — the
// real backend is never involved. Covers the W1 auth flow: Bearer header,
// single-flight refresh on 401 + one retry, logout on a dead refresh token,
// and no refresh attempt for auth-endpoint failures.
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { ReactNode } from "react";
import {
  ApiError,
  loginRequest,
  refreshAccessToken,
  registerRequest,
  signOut,
  useProfile,
} from "@/lib/api";
import { useAuthStore } from "@/lib/auth-store";

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

function mockFetch(impl: (url: string, init?: RequestInit) => Response) {
  return vi.fn((input: string | URL | Request, init?: RequestInit) => {
    const url = typeof input === "string" ? input : input.toString();
    return Promise.resolve(impl(url, init));
  });
}

function wrapper({ children }: { children: ReactNode }) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}

const PROFILE = {
  id: "profile-1",
  userId: "user-1",
  headline: "Senior Software Engineer",
  summary: "Java developer",
  location: "Reston, VA",
  workType: "HYBRID",
  salaryMin: 150000,
  salaryMax: 190000,
  currency: "USD",
  visibility: "PUBLIC",
  completeness: 80,
  createdAt: "2026-11-12T00:00:00.000Z",
  updatedAt: "2026-11-12T00:00:00.000Z",
};

beforeEach(() => {
  useAuthStore.getState().logout();
});

afterEach(() => {
  vi.unstubAllGlobals();
  useAuthStore.getState().logout();
});

describe("loginRequest", () => {
  it("POSTs to the BFF and returns the session without a refresh token", async () => {
    const fetchMock = mockFetch((url, init) => {
      expect(url).toBe("/api/auth/login");
      expect(init?.method).toBe("POST");
      expect(init?.body).toBe(
        JSON.stringify({ email: "dev@example.com", password: "DevPass123!" }),
      );
      return jsonResponse({
        accessToken: "a.b.c",
        expiresInMs: 900_000,
        userId: "user-1",
        email: "dev@example.com",
        role: "USER",
      });
    });
    vi.stubGlobal("fetch", fetchMock);

    const session = await loginRequest({
      email: "dev@example.com",
      password: "DevPass123!",
    });
    expect(session.accessToken).toBe("a.b.c");
    expect(session).not.toHaveProperty("refreshToken");
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it("a 401 for bad credentials throws ApiError and does NOT call refresh", async () => {
    const fetchMock = mockFetch(() =>
      jsonResponse({ message: "Invalid email or password" }, 401),
    );
    vi.stubGlobal("fetch", fetchMock);

    await expect(
      loginRequest({ email: "dev@example.com", password: "wrongpass" }),
    ).rejects.toMatchObject({ status: 401 });
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });
});

describe("401 refresh + retry", () => {
  it("refreshes once and retries the request with the new token", async () => {
    useAuthStore.getState().login({
      userId: "user-1",
      email: "dev@example.com",
      role: "USER",
      accessToken: "expired-token",
    });
    const calls: string[] = [];
    const fetchMock = mockFetch((url) => {
      calls.push(url);
      if (url === "/api/auth/refresh") {
        return jsonResponse({
          accessToken: "fresh-token",
          expiresInMs: 900_000,
          userId: "user-1",
          email: "dev@example.com",
          role: "USER",
        });
      }
      // First profile call: expired token. Retry: fresh token.
      if (calls.filter((u) => u === "/api/profiles/me").length === 1) {
        return jsonResponse({ message: "Unauthorized" }, 401);
      }
      return jsonResponse(PROFILE);
    });
    vi.stubGlobal("fetch", fetchMock);

    const { result } = renderHook(() => useProfile(), { wrapper });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data?.headline).toBe("Senior Software Engineer");
    expect(useAuthStore.getState().accessToken).toBe("fresh-token");
    expect(calls.filter((u) => u === "/api/auth/refresh")).toHaveLength(1);
    expect(calls.filter((u) => u === "/api/profiles/me")).toHaveLength(2);
  });

  it("a dead refresh token ends the session and the request fails", async () => {
    useAuthStore.getState().login({
      userId: "user-1",
      email: "dev@example.com",
      role: "USER",
      accessToken: "expired-token",
    });
    const fetchMock = mockFetch((url) => {
      if (url === "/api/auth/refresh") {
        return jsonResponse({ message: "Invalid refresh token" }, 401);
      }
      return jsonResponse({ message: "Unauthorized" }, 401);
    });
    vi.stubGlobal("fetch", fetchMock);

    const { result } = renderHook(() => useProfile(), { wrapper });
    await waitFor(() => expect(result.current.isError).toBe(true));
    expect(result.current.error).toMatchObject({ status: 401 });
    expect(useAuthStore.getState().isAuthenticated).toBe(false);
    expect(useAuthStore.getState().accessToken).toBeNull();
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  it("concurrent 401s share a single refresh call", async () => {
    useAuthStore.getState().login({
      userId: "user-1",
      email: "dev@example.com",
      role: "USER",
      accessToken: "expired-token",
    });
    let refreshCalls = 0;
    const fetchMock = mockFetch((url) => {
      if (url === "/api/auth/refresh") {
        refreshCalls += 1;
        return jsonResponse({
          accessToken: "fresh-token",
          expiresInMs: 900_000,
          userId: "user-1",
          email: "dev@example.com",
          role: "USER",
        });
      }
      return jsonResponse({ message: "Unauthorized" }, 401);
    });
    vi.stubGlobal("fetch", fetchMock);

    const [a, b] = await Promise.all([
      refreshAccessToken(),
      refreshAccessToken(),
    ]);
    expect(a).toBe(true);
    expect(b).toBe(true);
    expect(refreshCalls).toBe(1);
    expect(useAuthStore.getState().accessToken).toBe("fresh-token");
  });
});

describe("signOut", () => {
  it("calls the BFF logout and clears the local session", async () => {
    useAuthStore.getState().login({
      userId: "user-1",
      email: "dev@example.com",
      role: "USER",
      accessToken: "a.b.c",
    });
    const fetchMock = mockFetch((url, init) => {
      expect(url).toBe("/api/auth/logout");
      expect(init?.method).toBe("POST");
      return jsonResponse({ status: "logged out" });
    });
    vi.stubGlobal("fetch", fetchMock);

    await signOut();
    expect(useAuthStore.getState().isAuthenticated).toBe(false);
    expect(useAuthStore.getState().accessToken).toBeNull();
  });

  it("still clears the session when the backend logout fails", async () => {
    useAuthStore.getState().login({
      userId: "user-1",
      email: "dev@example.com",
      role: "USER",
      accessToken: "a.b.c",
    });
    vi.stubGlobal(
      "fetch",
      mockFetch(() => jsonResponse({ message: "Unauthorized" }, 401)),
    );

    await signOut();
    expect(useAuthStore.getState().isAuthenticated).toBe(false);
  });
});

describe("ApiError", () => {
  it("carries the backend message when the body is JSON", async () => {
    vi.stubGlobal(
      "fetch",
      mockFetch(() =>
        jsonResponse({ message: "Email already registered" }, 409),
      ),
    );
    let caught: unknown;
    try {
      await registerRequest({
        email: "dev@example.com",
        fullName: "Dev User",
        password: "DevPass123!",
      });
    } catch (err) {
      caught = err;
    }
    expect(caught).toBeInstanceOf(ApiError);
    expect((caught as ApiError).message).toBe("Email already registered");
    expect((caught as ApiError).status).toBe(409);
  });
});
