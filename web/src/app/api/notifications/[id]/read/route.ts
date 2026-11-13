// BFF: POST /api/notifications/[id]/read — marks one stored notification
// read. Derived (clock-*) ids are 404 on the backend, never marked read.
import type { NextRequest } from "next/server";
import { proxyJson } from "@/lib/server-api";

export async function POST(
  req: NextRequest,
  { params }: { params: Promise<{ id: string }> },
): Promise<Response> {
  const { id } = await params;
  return proxyJson(req, `/notifications/${id}/read`, { method: "POST" });
}
