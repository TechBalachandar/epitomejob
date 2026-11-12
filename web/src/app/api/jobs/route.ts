// BFF: GET /api/jobs — query string passes through to the backend verbatim.
import type { NextRequest } from "next/server";
import { proxyJson } from "@/lib/server-api";

export async function GET(req: NextRequest): Promise<Response> {
  return proxyJson(req, `/jobs${req.nextUrl.search}`);
}
