// BFF: POST /api/auth/register — same shape as login (201 + session).
import type { NextRequest } from "next/server";
import { backendFetch, sessionResponse } from "@/lib/server-api";

export async function POST(req: NextRequest): Promise<Response> {
  const upstream = await backendFetch("/auth/register", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: await req.text(),
  });
  return sessionResponse(upstream);
}
