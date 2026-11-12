// BFF: POST /api/approvals/[id]/reject.
import type { NextRequest } from "next/server";
import { proxyJson } from "@/lib/server-api";

export async function POST(
  req: NextRequest,
  { params }: { params: Promise<{ id: string }> },
): Promise<Response> {
  const { id } = await params;
  return proxyJson(req, `/approvals/${id}/reject`, { method: "POST" });
}
