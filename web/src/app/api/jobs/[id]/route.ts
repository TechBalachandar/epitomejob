// BFF: GET /api/jobs/[id] — one job from the backend.
import type { NextRequest } from "next/server";
import { proxyJson } from "@/lib/server-api";

export async function GET(
  req: NextRequest,
  { params }: { params: Promise<{ id: string }> },
): Promise<Response> {
  const { id } = await params;
  return proxyJson(req, `/jobs/${encodeURIComponent(id)}`);
}
