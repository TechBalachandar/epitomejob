// BFF: GET /api/notifications/unread-count — feeds the header bell badge.
import type { NextRequest } from "next/server";
import { proxyJson } from "@/lib/server-api";

export async function GET(req: NextRequest): Promise<Response> {
  return proxyJson(req, "/notifications/unread-count");
}
