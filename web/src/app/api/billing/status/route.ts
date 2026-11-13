// BFF: GET /api/billing/status — forwards to the backend. Always 200,
// even when the billing flag is off; it is how the web learns the flag state.
import type { NextRequest } from "next/server";
import { proxyJson } from "@/lib/server-api";

export async function GET(req: NextRequest): Promise<Response> {
  return proxyJson(req, "/billing/status");
}
