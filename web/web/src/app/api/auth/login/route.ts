// BFF: POST /api/auth/login — forwards to the backend, sets the refresh
// token as an httpOnly cookie, returns only the session (no refresh token).
import type { NextRequest } from "next/server";
import { backendFetch, sessionResponse } from "@/lib/server-api";

export async function POST(req: NextRequest): Promise<Response> {
  const upstream = await backendFetch("/auth/login", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: await req.text(),
  });
  return sessionResponse(upstream);
}
