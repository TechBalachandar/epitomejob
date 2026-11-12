// BFF: GET /api/interviews/sessions/[id] — one session with its answers.
import type { NextRequest } from "next/server";
import { proxyJson } from "@/lib/server-api";

export async function GET(
  req: NextRequest,
  { params }: { params: Promise<{ id: string }> },
): Promise<Response> {
  const { id } = await params;
  return proxyJson(req, `/interviews/sessions/${encodeURIComponent(id)}`);
}
