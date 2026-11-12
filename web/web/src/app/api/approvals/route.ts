// BFF: POST /api/approvals — propose an approval. The payload stays a
// JSON string (the backend stores it as TEXT); it is forwarded untouched.
import type { NextRequest } from "next/server";
import { proxyJson } from "@/lib/server-api";

export async function POST(req: NextRequest): Promise<Response> {
  return proxyJson(req, "/approvals", {
    method: "POST",
    body: await req.text(),
  });
}
