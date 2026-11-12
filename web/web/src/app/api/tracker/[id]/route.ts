// BFF: PUT / DELETE /api/tracker/[id] — update status/notes, or delete.
import type { NextRequest } from "next/server";
import { proxyJson } from "@/lib/server-api";

export async function PUT(
  req: NextRequest,
  { params }: { params: Promise<{ id: string }> },
): Promise<Response> {
  const { id } = await params;
  return proxyJson(req, `/tracker/${encodeURIComponent(id)}`, {
    method: "PUT",
    body: await req.text(),
  });
}

export async function DELETE(
  req: NextRequest,
  { params }: { params: Promise<{ id: string }> },
): Promise<Response> {
  const { id } = await params;
  return proxyJson(req, `/tracker/${encodeURIComponent(id)}`, {
    method: "DELETE",
  });
}
