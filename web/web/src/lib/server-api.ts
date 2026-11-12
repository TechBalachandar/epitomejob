// Server-only helpers for the BFF proxy (Next.js route handlers under
// src/app/api). The browser never talks to the Spring Boot backend directly
// (it has no CORS config for the browser); every call goes through /api,
// which forwards to the backend and returns the backend status + body.
// Only import this module from route handlers — never from client code.
import type { NextRequest } from "next/server";

/** Backend base URL. API_URL is server-only; NEXT_PUBLIC_API_URL is the fallback. */
export const BACKEND_URL =
  process.env.API_URL ??
  process.env.NEXT_PUBLIC_API_URL ??
  "http://localhost:8080";

/** Name of the httpOnly cookie that carries the refresh token. */
export const REFRESH_COOKIE = "epitomejob_refresh";

/** What the backend returns from /auth/login, /auth/register, /auth/refresh. */
interface BackendSession {
  accessToken: string;
  refreshToken: string;
  expiresInMs: number;
  userId: string;
  email: string;
  role: string;
}

/** Access token the browser sent as `Authorization: Bearer ...`, if any. */
export function bearerToken(req: NextRequest): string | null {
  const header = req.headers.get("authorization");
  return header?.startsWith("Bearer ") ? header.slice("Bearer ".length) : null;
}

/** Plain fetch against the backend. Throws only on network failure. */
export async function backendFetch(
  path: string,
  init: RequestInit = {},
): Promise<Response> {
  return fetch(`${BACKEND_URL}${path}`, init);
}

/** Forward an upstream response verbatim: status, body, and content type. */
export async function forwardResponse(upstream: Response): Promise<Response> {
  const body = await upstream.arrayBuffer();
  const headers = new Headers();
  const contentType = upstream.headers.get("content-type");
  if (contentType) headers.set("content-type", contentType);
  return new Response(body, { status: upstream.status, headers });
}

/** Set-Cookie value for the refresh token: httpOnly, SameSite=Lax, path=/ . */
export function refreshCookieHeader(token: string): string {
  const parts = [
    `${REFRESH_COOKIE}=${token}`,
    "HttpOnly",
    "Path=/",
    "SameSite=Lax",
    `Max-Age=${30 * 24 * 3600}`,
  ];
  if (process.env.NODE_ENV === "production") parts.push("Secure");
  return parts.join("; ");
}

/** Set-Cookie value that clears the refresh-token cookie. */
export function clearRefreshCookieHeader(): string {
  return `${REFRESH_COOKIE}=; HttpOnly; Path=/; SameSite=Lax; Max-Age=0`;
}

/**
 * Turn a backend /auth/* response into the BFF response: the refresh token
 * is stripped out of the JSON body and set as the httpOnly cookie instead.
 * Error responses are forwarded verbatim (no cookie is set).
 */
export async function sessionResponse(upstream: Response): Promise<Response> {
  if (!upstream.ok) return forwardResponse(upstream);
  const data = (await upstream.json()) as BackendSession;
  const { refreshToken, ...session } = data;
  return Response.json(session, {
    status: upstream.status,
    headers: { "Set-Cookie": refreshCookieHeader(refreshToken) },
  });
}

/**
 * Forward a JSON request to the backend with the browser's access token.
 * No local auth checks: the backend is the one source of truth for 401s.
 */
export async function proxyJson(
  req: NextRequest,
  path: string,
  init: { method?: string; body?: string } = {},
): Promise<Response> {
  const headers = new Headers();
  const token = bearerToken(req);
  if (token) headers.set("Authorization", `Bearer ${token}`);
  if (init.body !== undefined) headers.set("Content-Type", "application/json");
  const upstream = await backendFetch(path, {
    method: init.method ?? "GET",
    headers,
    body: init.body,
  });
  return forwardResponse(upstream);
}

/**
 * Resolve an access token for a backend call. Prefers the Authorization
 * header the browser sent. For EventSource (which cannot set headers) it
 * falls back to the httpOnly refresh cookie: it refreshes server-side and
 * returns the new cookie to set, so the rotation stays transparent.
 */
export async function resolveAccessToken(
  req: NextRequest,
): Promise<{ token: string | null; setCookie?: string }> {
  const header = bearerToken(req);
  if (header) return { token: header };
  const refresh = req.cookies.get(REFRESH_COOKIE)?.value;
  if (!refresh) return { token: null };
  const res = await backendFetch("/auth/refresh", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ refreshToken: refresh }),
  });
  if (!res.ok) return { token: null };
  const data = (await res.json()) as BackendSession;
  return {
    token: data.accessToken,
    setCookie: refreshCookieHeader(data.refreshToken),
  };
}
