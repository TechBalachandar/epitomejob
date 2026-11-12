// BFF: POST /api/resumes — multipart passthrough. The raw request bytes
// (including the multipart boundary in Content-Type) are forwarded to the
// backend unchanged, so the file arrives exactly as the browser sent it.
import type { NextRequest } from "next/server";
import { backendFetch, bearerToken, forwardResponse } from "@/lib/server-api";

export async function POST(req: NextRequest): Promise<Response> {
  const headers = new Headers();
  const contentType = req.headers.get("content-type");
  if (contentType) headers.set("Content-Type", contentType);
  const token = bearerToken(req);
  if (token) headers.set("Authorization", `Bearer ${token}`);
  const upstream = await backendFetch("/resumes", {
    method: "POST",
    headers,
    body: await req.arrayBuffer(),
  });
  return forwardResponse(upstream);
}
