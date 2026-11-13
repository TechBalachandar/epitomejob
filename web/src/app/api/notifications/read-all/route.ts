// BFF: POST /api/notifications/read-all — marks every stored unread
// notification read. Derived clock warnings are unaffected.
import type { NextRequest } from "next/server";
import { proxyJson } from "@/lib/server-api";

export async function POST(req: NextRequest): Promise<Response> {
  return proxyJson(req, "/notifications/read-all", { method: "POST" });
}
