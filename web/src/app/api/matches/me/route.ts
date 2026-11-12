// BFF: GET /api/matches/me — the user's top matches, score desc.
import type { NextRequest } from "next/server";
import { proxyJson } from "@/lib/server-api";

export async function GET(req: NextRequest): Promise<Response> {
  return proxyJson(req, "/matches/me");
}
