// BFF: GET /api/resumes/[id]/text — the resume's parsed text.
// Forwards to the backend GET /resumes/{id}/text (owner-only; 404 when
// the resume is missing or belongs to someone else, 400 when the text
// is not ready yet). The browser needs this passthrough because it
// never talks to the backend directly.
import type { NextRequest } from "next/server";
import { proxyJson } from "@/lib/server-api";

export async function GET(
  req: NextRequest,
  { params }: { params: Promise<{ id: string }> },
): Promise<Response> {
  const { id } = await params;
  return proxyJson(req, `/resumes/${encodeURIComponent(id)}/text`);
}
