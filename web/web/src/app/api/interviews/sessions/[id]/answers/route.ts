// BFF: POST /api/interviews/sessions/[id]/answers — answer one question;
// the backend scores it with the deterministic rubric (contract §5).
import type { NextRequest } from "next/server";
import { proxyJson } from "@/lib/server-api";

export async function POST(
  req: NextRequest,
  { params }: { params: Promise<{ id: string }> },
): Promise<Response> {
  const { id } = await params;
  return proxyJson(req, `/interviews/sessions/${encodeURIComponent(id)}/answers`, {
    method: "POST",
    body: await req.text(),
  });
}
