// BFF: GET /api/matches/me — the user's top matches, score desc.
import type { NextRequest } from "next/server";
import { proxyJson } from "@/lib/server-api";

export async function GET(req: NextRequest): Promise<Response> {
  // W6: ?resumeId= passes through so matches are scored per resume.
  return proxyJson(req, `/matches/me${req.nextUrl.search}`);
}
