// BFF: POST /api/auth/refresh — reads the refresh token from the httpOnly
// cookie (never from the request body), rotates it via the backend, and
// returns the new session with the new cookie. A dead cookie is cleared so
// the browser stops retrying it.
import type { NextRequest } from "next/server";
import {
  REFRESH_COOKIE,
  backendFetch,
  clearRefreshCookieHeader,
  forwardResponse,
  sessionResponse,
} from "@/lib/server-api";

export async function POST(req: NextRequest): Promise<Response> {
  const refresh = req.cookies.get(REFRESH_COOKIE)?.value;
  if (!refresh) {
    return Response.json({ message: "No refresh token" }, { status: 401 });
  }
  const upstream = await backendFetch("/auth/refresh", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ refreshToken: refresh }),
  });
  if (!upstream.ok) {
    const res = await forwardResponse(upstream);
    res.headers.set("Set-Cookie", clearRefreshCookieHeader());
    return res;
  }
  return sessionResponse(upstream);
}
