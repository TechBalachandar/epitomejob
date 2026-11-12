// BFF: GET /api/approvals/stream — SSE passthrough. The backend sends the
// pending list once ("pending" event) and completes; this handler pipes the
// response body through untouched. EventSource cannot send an Authorization
// header, so when the browser sends none, the handler refreshes via the
// httpOnly cookie instead (see resolveAccessToken).
import type { NextRequest } from "next/server";
import { backendFetch, forwardResponse, resolveAccessToken } from "@/lib/server-api";

export const dynamic = "force-dynamic";

export async function GET(req: NextRequest): Promise<Response> {
  const { token, setCookie } = await resolveAccessToken(req);
  if (!token) {
    return Response.json({ message: "Unauthorized" }, { status: 401 });
  }
  const upstream = await backendFetch("/approvals/stream", {
    headers: {
      Authorization: `Bearer ${token}`,
      Accept: "text/event-stream",
    },
  });
  if (!upstream.ok || !upstream.body) return forwardResponse(upstream);
  const headers = new Headers({
    "Content-Type": "text/event-stream",
    "Cache-Control": "no-cache",
    Connection: "keep-alive",
  });
  if (setCookie) headers.set("Set-Cookie", setCookie);
  return new Response(upstream.body, { status: upstream.status, headers });
}
