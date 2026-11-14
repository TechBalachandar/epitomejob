// BFF: GET /api/resume-builder/templates — the template gallery list.
// Proxies to the backend builder service; the client falls back to its
// built-in templates when this is unavailable.
import type { NextRequest } from "next/server";
import { proxyJson } from "@/lib/server-api";

export async function GET(req: NextRequest): Promise<Response> {
  return proxyJson(req, "/resume-builder/templates");
}
