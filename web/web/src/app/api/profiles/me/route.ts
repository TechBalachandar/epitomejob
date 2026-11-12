// BFF: GET / PUT /api/profiles/me — forwards the browser's access token
// and the JSON body to the backend, returns the backend status + body.
import type { NextRequest } from "next/server";
import { proxyJson } from "@/lib/server-api";

export async function GET(req: NextRequest): Promise<Response> {
  return proxyJson(req, "/profiles/me");
}

export async function PUT(req: NextRequest): Promise<Response> {
  return proxyJson(req, "/profiles/me", {
    method: "PUT",
    body: await req.text(),
  });
}
