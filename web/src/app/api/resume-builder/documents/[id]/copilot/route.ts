// BFF: POST /api/resume-builder/documents/[id]/copilot — forwards
// {active_section, user_context, target_jd, resume_state} to the builder
// service's Co-Pilot; returns the 3–5 rewrite options verbatim.
import type { NextRequest } from "next/server";
import { proxyJson } from "@/lib/server-api";

export async function POST(
  req: NextRequest,
  { params }: { params: Promise<{ id: string }> },
): Promise<Response> {
  const { id } = await params;
  return proxyJson(
    req,
    `/resume-builder/documents/${encodeURIComponent(id)}/copilot`,
    { method: "POST", body: await req.text() },
  );
}
