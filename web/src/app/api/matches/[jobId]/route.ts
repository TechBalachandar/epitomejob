// BFF: GET /api/matches/[jobId] — the match for one job.
import type { NextRequest } from "next/server";
import { proxyJson } from "@/lib/server-api";

export async function GET(
  req: NextRequest,
  { params }: { params: Promise<{ jobId: string }> },
): Promise<Response> {
  const { jobId } = await params;
  // W6: ?resumeId= passes through so the match is scored per resume.
  return proxyJson(req, `/matches/${encodeURIComponent(jobId)}${req.nextUrl.search}`);
}
