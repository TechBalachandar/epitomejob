// BFF: GET /api/billing/plans — forwards to the backend (404 while the
// billing flag is off; the plans page shows hardcoded copy instead).
import type { NextRequest } from "next/server";
import { proxyJson } from "@/lib/server-api";

export async function GET(req: NextRequest): Promise<Response> {
  return proxyJson(req, "/billing/plans");
}
