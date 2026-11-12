// BFF: GET /api/approvals/pending — the user's pending approval inbox.
import type { NextRequest } from "next/server";
import { proxyJson } from "@/lib/server-api";

export async function GET(req: NextRequest): Promise<Response> {
  return proxyJson(req, "/approvals/pending");
}
