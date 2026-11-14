// BFF: /api/resume-builder/documents/[id] — GET loads a document
// (?documentId= refresh), PUT saves {state, templateId, title}.
import type { NextRequest } from "next/server";
import { proxyJson } from "@/lib/server-api";

export async function GET(
  req: NextRequest,
  { params }: { params: Promise<{ id: string }> },
): Promise<Response> {
  const { id } = await params;
  return proxyJson(req, `/resume-builder/documents/${encodeURIComponent(id)}`);
}

export async function PUT(
  req: NextRequest,
  { params }: { params: Promise<{ id: string }> },
): Promise<Response> {
  const { id } = await params;
  return proxyJson(req, `/resume-builder/documents/${encodeURIComponent(id)}`, {
    method: "PUT",
    body: await req.text(),
  });
}
