// BFF: /api/resume-builder/documents/[id]/export — binary passthrough.
// ?format=pdf|docx selects the file; the upstream Content-Type and
// Content-Disposition (server filename) are forwarded so the browser
// downloads the file as named. GET and POST both forward the query string;
// a POST body (export options) is forwarded as JSON when present.
import type { NextRequest } from "next/server";
import { backendFetch, bearerToken } from "@/lib/server-api";

export async function GET(
  req: NextRequest,
  { params }: { params: Promise<{ id: string }> },
): Promise<Response> {
  const { id } = await params;
  const headers = new Headers();
  const token = bearerToken(req);
  if (token) headers.set("Authorization", `Bearer ${token}`);
  const upstream = await backendFetch(
    `/resume-builder/documents/${encodeURIComponent(id)}/export${req.nextUrl.search}`,
    { method: "GET", headers },
  );
  return forwardBinary(upstream);
}

export async function POST(
  req: NextRequest,
  { params }: { params: Promise<{ id: string }> },
): Promise<Response> {
  const { id } = await params;
  const headers = new Headers();
  const token = bearerToken(req);
  if (token) headers.set("Authorization", `Bearer ${token}`);
  const body = await req.text();
  if (body) headers.set("Content-Type", "application/json");
  const upstream = await backendFetch(
    `/resume-builder/documents/${encodeURIComponent(id)}/export${req.nextUrl.search}`,
    { method: "POST", headers, body: body || undefined },
  );
  return forwardBinary(upstream);
}

/** Forward a binary upstream response: status, bytes, file headers. */
async function forwardBinary(upstream: Response): Promise<Response> {
  const body = await upstream.arrayBuffer();
  const headers = new Headers();
  for (const name of ["content-type", "content-disposition", "content-length"]) {
    const value = upstream.headers.get(name);
    if (value) headers.set(name, value);
  }
  return new Response(body, { status: upstream.status, headers });
}
