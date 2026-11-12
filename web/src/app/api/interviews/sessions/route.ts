// BFF: GET /api/interviews/sessions → GET /interviews/sessions/me;
// POST /api/interviews/sessions → POST /interviews/sessions.
import type { NextRequest } from "next/server";
import { proxyJson } from "@/lib/server-api";

export async function GET(req: NextRequest): Promise<Response> {
  return proxyJson(req, "/interviews/sessions/me");
}

export async function POST(req: NextRequest): Promise<Response> {
  return proxyJson(req, "/interviews/sessions", {
    method: "POST",
    body: await req.text(),
  });
}
