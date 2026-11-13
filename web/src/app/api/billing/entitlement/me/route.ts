// BFF: GET /api/billing/entitlement/me — forwards to the backend
// (404 while the billing flag is off).
import type { NextRequest } from "next/server";
import { proxyJson } from "@/lib/server-api";

export async function GET(req: NextRequest): Promise<Response> {
  return proxyJson(req, "/billing/entitlement/me");
}
