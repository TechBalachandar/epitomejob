// BFF: GET /api/resumes/me — the user's resume list from the backend.
import type { NextRequest } from "next/server";
import { proxyJson } from "@/lib/server-api";

export async function GET(req: NextRequest): Promise<Response> {
  return proxyJson(req, "/resumes/me");
}
