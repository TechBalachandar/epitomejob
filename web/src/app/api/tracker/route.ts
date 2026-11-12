// BFF: GET /api/tracker → GET /tracker/me; POST /api/tracker → POST /tracker.
import type { NextRequest } from "next/server";
import { proxyJson } from "@/lib/server-api";

export async function GET(req: NextRequest): Promise<Response> {
  return proxyJson(req, "/tracker/me");
}

export async function POST(req: NextRequest): Promise<Response> {
  return proxyJson(req, "/tracker", {
    method: "POST",
    body: await req.text(),
  });
}
