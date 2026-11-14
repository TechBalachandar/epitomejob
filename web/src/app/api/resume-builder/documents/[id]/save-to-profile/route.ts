// BFF: POST /api/resume-builder/documents/[id]/save-to-profile —
// forwards {label}; returns the saved resume ({resumeId} or ResumeItem).
import type { NextRequest } from "next/server";
import { proxyJson } from "@/lib/server-api";

export async function POST(
  req: NextRequest,
  { params }: { params: Promise<{ id: string }> },
): Promise<Response> {
  const { id } = await params;
  return proxyJson(
    req,
    `/resume-builder/documents/${encodeURIComponent(id)}/save-to-profile`,
    { method: "POST", body: await req.text() },
  );
}
