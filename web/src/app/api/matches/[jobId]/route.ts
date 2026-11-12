// BFF: GET /api/matches/[jobId] — the match for one job.
import type { NextRequest } from "next/server";
import { proxyJson } from "@/lib/server-api";

export async function GET(
  req: NextRequest,
  { params }: { params: Promise<{ jobId: string }> },
): Promise<Response> {
  const { jobId } = await params;
  return proxyJson(req, `/matches/${encodeURIComponent(jobId)}`);
}
