// BFF: GET /api/notifications/me?page=&size= — the query string MUST be
// forwarded to the backend (page/size are 0-based, size clamped server-side).
import type { NextRequest } from "next/server";
import { proxyJson } from "@/lib/server-api";

export async function GET(req: NextRequest): Promise<Response> {
  return proxyJson(req, "/notifications/me" + req.nextUrl.search);
}
