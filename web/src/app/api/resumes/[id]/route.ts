// BFF: PUT /api/resumes/[id] — rename a resume's label (W6).
// Forwards the browser's access token and the JSON body {label} to the
// backend, returns the backend status + body.
import type { NextRequest } from "next/server";
import { proxyJson } from "@/lib/server-api";

export async function PUT(
  req: NextRequest,
  { params }: { params: Promise<{ id: string }> },
): Promise<Response> {
  const { id } = await params;
  return proxyJson(req, `/resumes/${encodeURIComponent(id)}`, {
    method: "PUT",
    body: await req.text(),
  });
}
