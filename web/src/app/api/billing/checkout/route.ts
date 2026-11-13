// BFF: POST /api/billing/checkout — forwards {planCode} to the backend.
// The backend's status and body pass through verbatim (201 CREATED /
// PENDING_MANUAL, 404 unknown plan / flag off, 409 already entitled,
// 503 Stripe not configured).
import type { NextRequest } from "next/server";
import { proxyJson } from "@/lib/server-api";

export async function POST(req: NextRequest): Promise<Response> {
  return proxyJson(req, "/billing/checkout", {
    method: "POST",
    body: await req.text(),
  });
}
