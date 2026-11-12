// BFF: POST /api/coach/chat — forwards the message to the backend's
// Coach endpoint (contract §9 / §3), which forwards to the agent-brain.
import type { NextRequest } from "next/server";
import { proxyJson } from "@/lib/server-api";

export async function POST(req: NextRequest): Promise<Response> {
  return proxyJson(req, "/coach/chat", {
    method: "POST",
    body: await req.text(),
  });
}
