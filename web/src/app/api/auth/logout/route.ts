// BFF: POST /api/auth/logout — revokes the refresh token at the backend
// (best-effort) and always clears the cookie, so logout is idempotent from
// the browser's point of view.
import type { NextRequest } from "next/server";
import {
  REFRESH_COOKIE,
  backendFetch,
  clearRefreshCookieHeader,
} from "@/lib/server-api";

export async function POST(req: NextRequest): Promise<Response> {
  const refresh = req.cookies.get(REFRESH_COOKIE)?.value;
  if (refresh) {
    try {
      await backendFetch("/auth/logout", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ refreshToken: refresh }),
      });
    } catch {
      // Logout still succeeds locally: the cookie is cleared either way.
    }
  }
  return Response.json(
    { status: "logged out" },
    { headers: { "Set-Cookie": clearRefreshCookieHeader() } },
  );
}
