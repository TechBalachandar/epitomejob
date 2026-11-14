// server-api unit tests: the BFF helpers every route handler relies on.
// Pure functions are tested directly; backendFetch/proxyJson are tested
// with the global fetch mocked. No real backend.
import { NextRequest } from "next/server";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import {
  BACKEND_URL,
  bearerToken,
  backendFetch,
  clearRefreshCookieHeader,
  forwardResponse,
  proxyJson,
  refreshCookieHeader,
  resolveAccessToken,
  sessionResponse,
} from "@/lib/server-api";

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

function req(
  url: string,
  init?: { method?: string; headers?: Record<string, string>; body?: string },
): NextRequest {
  return new NextRequest(url, {
    method: init?.method ?? "GET",
    headers: init?.headers,
    body: init?.body,
  });
}

const fetchMock = vi.fn();

beforeEach(() => {
  vi.stubGlobal("fetch", fetchMock);
  fetchMock.mockReset();
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("bearerToken", () => {
  it("returns the token after 'Bearer '", () => {
    expect(bearerToken(req("http://localhost:3000/api/x", { headers: { Authorization: "Bearer abc-123" } }))).toBe(
      "abc-123",
    );
  });

  it("returns null when there is no Authorization header", () => {
    expect(bearerToken(req("http://localhost:3000/api/x"))).toBeNull();
  });

  it("returns null for a non-Bearer scheme", () => {
    expect(
      bearerToken(req("http://localhost:3000/api/x", { headers: { Authorization: "Basic abc" } })),
    ).toBeNull();
  });

  it("returns null for a lowercase 'bearer' prefix", () => {
    expect(
      bearerToken(req("http://localhost:3000/api/x", { headers: { Authorization: "bearer abc" } })),
    ).toBeNull();
  });
});

describe("backendFetch", () => {
  it("calls the backend base URL + path", async () => {
    fetchMock.mockResolvedValue(jsonResponse({ ok: true }));
    await backendFetch("/resume-builder/templates");
    expect(fetchMock).toHaveBeenCalledWith(`${BACKEND_URL}/resume-builder/templates`, {});
  });

  it("passes the init through and returns the response", async () => {
    const upstream = jsonResponse({ ok: true }, 201);
    fetchMock.mockResolvedValue(upstream);
    const res = await backendFetch("/x", { method: "POST", body: "{}" });
    expect(res).toBe(upstream);
    expect(fetchMock.mock.calls[0][1]).toMatchObject({ method: "POST", body: "{}" });
  });
});

describe("forwardResponse", () => {
  it("passes status, body and content type through", async () => {
    const upstream = jsonResponse({ a: 1 }, 201);
    const res = await forwardResponse(upstream);
    expect(res.status).toBe(201);
    expect(await res.json()).toEqual({ a: 1 });
    expect(res.headers.get("content-type")).toContain("application/json");
  });

  it("passes file headers through and omits absent ones", async () => {
    const upstream = new Response("%PDF-fake", {
      status: 200,
      headers: {
        "content-type": "application/pdf",
        "content-disposition": 'attachment; filename="a.pdf"',
        "content-length": "9",
      },
    });
    const res = await forwardResponse(upstream);
    expect(await res.text()).toBe("%PDF-fake");
    expect(res.headers.get("content-type")).toBe("application/pdf");
    expect(res.headers.get("content-disposition")).toBe('attachment; filename="a.pdf"');
    expect(res.headers.get("content-length")).toBe("9");
  });

  it("sets no content type when the upstream has none (binary body)", async () => {
    // A string body gets an automatic text/plain content type from the
    // platform, so use raw bytes to exercise the no-header path.
    const upstream = new Response(new Uint8Array([1, 2, 3]).buffer, { status: 200 });
    const res = await forwardResponse(upstream);
    expect(res.headers.get("content-type")).toBeNull();
    expect(new Uint8Array(await res.arrayBuffer())).toEqual(new Uint8Array([1, 2, 3]));
  });
});

describe("proxyJson", () => {
  it("GET forwards the bearer token and no content type without a body", async () => {
    fetchMock.mockResolvedValue(jsonResponse({ ok: true }));
    await proxyJson(
      req("http://localhost:3000/api/x", { headers: { Authorization: "Bearer t-1" } }),
      "/profiles/me",
    );
    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toBe(`${BACKEND_URL}/profiles/me`);
    expect(init.method).toBe("GET");
    expect((init.headers as Headers).get("authorization")).toBe("Bearer t-1");
    expect((init.headers as Headers).get("content-type")).toBeNull();
  });

  it("POST sets JSON content type and forwards the body", async () => {
    fetchMock.mockResolvedValue(jsonResponse({ id: "1" }, 201));
    const body = JSON.stringify({ title: "x" });
    const res = await proxyJson(
      req("http://localhost:3000/api/x", {
        method: "POST",
        headers: { Authorization: "Bearer t-1" },
        body,
      }),
      "/resume-builder/documents",
      { method: "POST", body },
    );
    const [, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(init.method).toBe("POST");
    expect((init.headers as Headers).get("content-type")).toBe("application/json");
    expect(init.body).toBe(body);
    expect(res.status).toBe(201);
  });

  it("sends no Authorization header when the browser sent no token", async () => {
    fetchMock.mockResolvedValue(jsonResponse({ ok: true }));
    await proxyJson(req("http://localhost:3000/api/x"), "/jobs");
    const [, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect((init.headers as Headers).get("authorization")).toBeNull();
  });

  it("forwards an upstream 401 verbatim", async () => {
    fetchMock.mockResolvedValue(jsonResponse({ error: "unauthorized" }, 401));
    const res = await proxyJson(req("http://localhost:3000/api/x"), "/profiles/me");
    expect(res.status).toBe(401);
    expect(await res.json()).toEqual({ error: "unauthorized" });
  });
});

describe("sessionResponse", () => {
  it("strips the refresh token from the body and sets the httpOnly cookie", async () => {
    const upstream = jsonResponse({
      accessToken: "at-1",
      refreshToken: "rt-1",
      expiresInMs: 900000,
      userId: "u-1",
      email: "dev@example.com",
      role: "USER",
    });
    const res = await sessionResponse(upstream);
    const body = await res.json();
    expect(body).not.toHaveProperty("refreshToken");
    expect(body.accessToken).toBe("at-1");
    const cookie = res.headers.get("set-cookie") ?? "";
    expect(cookie).toContain("epitomejob_refresh=rt-1");
    expect(cookie).toContain("HttpOnly");
    expect(cookie).toContain("SameSite=Lax");
  });

  it("forwards an error response verbatim with no cookie", async () => {
    const res = await sessionResponse(jsonResponse({ error: "bad login" }, 401));
    expect(res.status).toBe(401);
    expect(res.headers.get("set-cookie")).toBeNull();
    expect(await res.json()).toEqual({ error: "bad login" });
  });
});

describe("refreshCookieHeader / clearRefreshCookieHeader", () => {
  it("refresh cookie is httpOnly, Lax, 30-day, path /", () => {
    const header = refreshCookieHeader("rt-9");
    expect(header).toBe(
      "epitomejob_refresh=rt-9; HttpOnly; Path=/; SameSite=Lax; Max-Age=2592000",
    );
  });

  it("clear cookie expires immediately", () => {
    expect(clearRefreshCookieHeader()).toBe(
      "epitomejob_refresh=; HttpOnly; Path=/; SameSite=Lax; Max-Age=0",
    );
  });
});

describe("resolveAccessToken", () => {
  it("prefers the Authorization header and never calls the backend", async () => {
    const out = await resolveAccessToken(
      req("http://localhost:3000/api/x", { headers: { Authorization: "Bearer h-1" } }),
    );
    expect(out).toEqual({ token: "h-1" });
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("returns null with no header and no refresh cookie", async () => {
    const out = await resolveAccessToken(req("http://localhost:3000/api/x"));
    expect(out).toEqual({ token: null });
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("refreshes from the cookie and returns the new token + cookie", async () => {
    fetchMock.mockResolvedValue(
      jsonResponse({ accessToken: "at-2", refreshToken: "rt-2" }),
    );
    const out = await resolveAccessToken(
      req("http://localhost:3000/api/x", {
        headers: { Cookie: "epitomejob_refresh=rt-1" },
      }),
    );
    expect(out.token).toBe("at-2");
    expect(out.setCookie).toContain("epitomejob_refresh=rt-2");
    expect(fetchMock).toHaveBeenCalledWith(
      `${BACKEND_URL}/auth/refresh`,
      expect.objectContaining({ method: "POST" }),
    );
  });

  it("returns null when the refresh fails", async () => {
    fetchMock.mockResolvedValue(jsonResponse({ error: "expired" }, 401));
    const out = await resolveAccessToken(
      req("http://localhost:3000/api/x", {
        headers: { Cookie: "epitomejob_refresh=rt-1" },
      }),
    );
    expect(out).toEqual({ token: null });
  });
});
