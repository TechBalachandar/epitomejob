// BFF: GET /api/interviews/questions?jobId= — 5 questions for the job's role.
import type { NextRequest } from "next/server";
import { proxyJson } from "@/lib/server-api";

export async function GET(req: NextRequest): Promise<Response> {
  return proxyJson(req, `/interviews/questions${req.nextUrl.search}`);
}
