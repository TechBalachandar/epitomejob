// BFF: POST /api/approvals/[id]/approve — idempotent on the backend:
// re-approving returns the same APPROVED record.
import type { NextRequest } from "next/server";
import { proxyJson } from "@/lib/server-api";

export async function POST(
  req: NextRequest,
  { params }: { params: Promise<{ id: string }> },
): Promise<Response> {
  const { id } = await params;
  return proxyJson(req, `/approvals/${id}/approve`, { method: "POST" });
}
