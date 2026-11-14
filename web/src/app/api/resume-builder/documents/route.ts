// BFF: /api/resume-builder/documents — POST creates a document from
// {state, templateId, title}; GET lists the user's documents.
import type { NextRequest } from "next/server";
import { proxyJson } from "@/lib/server-api";

export async function POST(req: NextRequest): Promise<Response> {
  return proxyJson(req, "/resume-builder/documents", {
    method: "POST",
    body: await req.text(),
  });
}

export async function GET(req: NextRequest): Promise<Response> {
  return proxyJson(req, "/resume-builder/documents");
}
