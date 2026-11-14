// Resume Builder BFF route tests: the Next.js route handlers under
// src/app/api/resume-builder/** are thin proxies to the builder service.
// These tests pin the contract at that boundary — the upstream path, the
// method/body forwarded, the bearer token, and the response passthrough —
// with the network layer (@/lib/server-api) mocked. No real backend.
// NOTE: mocked upstream bodies are STRINGS, never new Blob(...) — a
// Response built from a jsdom Blob breaks under Node 22 (CI #34).
import { NextRequest } from "next/server";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { GET as templatesGET } from "../src/app/api/resume-builder/templates/route";
import { POST as parsePOST } from "../src/app/api/resume-builder/parse/route";
import {
  GET as documentsGET,
  POST as documentsPOST,
} from "../src/app/api/resume-builder/documents/route";
import {
  GET as documentGET,
  PUT as documentPUT,
} from "../src/app/api/resume-builder/documents/[id]/route";
import { POST as exportPOST } from "../src/app/api/resume-builder/documents/[id]/export/route";
import { GET as exportGET } from "../src/app/api/resume-builder/documents/[id]/export/route";
import { POST as savePOST } from "../src/app/api/resume-builder/documents/[id]/save-to-profile/route";
import { POST as copilotPOST } from "../src/app/api/resume-builder/documents/[id]/copilot/route";

const mocks = vi.hoisted(() => ({
  proxyJson: vi.fn(),
  backendFetch: vi.fn(),
  forwardResponse: vi.fn(),
}));
const mockProxyJson = mocks.proxyJson;
const mockBackendFetch = mocks.backendFetch;
const mockForwardResponse = mocks.forwardResponse;

vi.mock("@/lib/server-api", async (importOriginal) => {
  const actual =
    await importOriginal<typeof import("@/lib/server-api")>();
  return {
    ...actual,
    proxyJson: mocks.proxyJson,
    backendFetch: mocks.backendFetch,
    forwardResponse: mocks.forwardResponse,
  };
});

const DOC_PARAMS = { params: Promise.resolve({ id: "doc-1" }) };

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

function req(
  url: string,
  init?: { method?: string; headers?: Record<string, string>; body?: string },
): NextRequest {
  return new NextRequest(url, {
    method: init?.method ?? "GET",
    headers: init?.headers,
    body: init?.body,
  });
}

function authReq(
  url: string,
  init?: { method?: string; headers?: Record<string, string>; body?: string },
): NextRequest {
  return req(url, {
    ...init,
    headers: { Authorization: "Bearer test-token", ...(init?.headers ?? {}) },
  });
}

beforeEach(() => {
  vi.clearAllMocks();
  mockProxyJson.mockResolvedValue(jsonResponse({ ok: true }));
  mockForwardResponse.mockImplementation(async (upstream: Response) => upstream);
});

describe("templates GET", () => {
  it("proxies to /resume-builder/templates and returns the response verbatim", async () => {
    const upstream = jsonResponse({ templates: [{ id: "modern" }] });
    mockProxyJson.mockResolvedValue(upstream);
    const res = await templatesGET(authReq("http://localhost:3000/api/resume-builder/templates"));
    expect(mockProxyJson).toHaveBeenCalledTimes(1);
    expect(mockProxyJson.mock.calls[0][1]).toBe("/resume-builder/templates");
    expect(res).toBe(upstream);
  });

  it("forwards an upstream error status and body unchanged", async () => {
    mockProxyJson.mockResolvedValue(jsonResponse({ error: "unavailable" }, 503));
    const res = await templatesGET(authReq("http://localhost:3000/api/resume-builder/templates"));
    expect(res.status).toBe(503);
    expect(await res.json()).toEqual({ error: "unavailable" });
  });

  it("propagates a proxyJson rejection (backend unreachable)", async () => {
    mockProxyJson.mockRejectedValue(new Error("fetch failed"));
    await expect(
      templatesGET(authReq("http://localhost:3000/api/resume-builder/templates")),
    ).rejects.toThrow("fetch failed");
  });
});

describe("parse POST", () => {
  it("forwards the raw bytes with the content type and bearer token", async () => {
    const upstream = jsonResponse({ id: "doc-9", templateId: "modern" }, 201);
    mockBackendFetch.mockResolvedValue(upstream);
    const body = "--boundary\r\nContent-Disposition: form-data; name=\"file\"";
    const res = await parsePOST(
      authReq("http://localhost:3000/api/resume-builder/parse", {
        method: "POST",
        headers: { "Content-Type": "multipart/form-data; boundary=boundary" },
        body,
      }),
    );
    expect(mockBackendFetch).toHaveBeenCalledTimes(1);
    const [path, init] = mockBackendFetch.mock.calls[0] as [string, RequestInit];
    expect(path).toBe("/resume-builder/parse");
    expect(init.method).toBe("POST");
    const headers = init.headers as Headers;
    expect(headers.get("content-type")).toBe("multipart/form-data; boundary=boundary");
    expect(headers.get("authorization")).toBe("Bearer test-token");
    expect(new TextDecoder().decode(init.body as ArrayBuffer)).toBe(body);
    expect(mockForwardResponse).toHaveBeenCalledWith(upstream);
    expect(res).toBe(upstream);
  });

  it("omits the content type when the request has none", async () => {
    // A string body gets an automatic text/plain content type from the
    // platform, so use raw bytes to exercise the no-header path.
    mockBackendFetch.mockResolvedValue(jsonResponse({ id: "doc-9" }, 201));
    await parsePOST(
      new NextRequest("http://localhost:3000/api/resume-builder/parse", {
        method: "POST",
        headers: { Authorization: "Bearer test-token" },
        body: new Uint8Array([1, 2, 3]).buffer,
      }),
    );
    const [, init] = mockBackendFetch.mock.calls[0] as [string, RequestInit];
    const headers = init.headers as Headers;
    expect(headers.get("content-type")).toBeNull();
    expect(headers.get("authorization")).toBe("Bearer test-token");
  });
});

describe("documents", () => {
  it("POST creates via proxyJson with method + body", async () => {
    const upstream = jsonResponse({ id: "doc-2" }, 201);
    mockProxyJson.mockResolvedValue(upstream);
    const body = JSON.stringify({ templateId: "modern", title: "Android resume" });
    const res = await documentsPOST(
      authReq("http://localhost:3000/api/resume-builder/documents", {
        method: "POST",
        body,
      }),
    );
    expect(mockProxyJson).toHaveBeenCalledWith(
      expect.anything(),
      "/resume-builder/documents",
      { method: "POST", body },
    );
    expect(res).toBe(upstream);
  });

  it("GET lists via proxyJson with no method override", async () => {
    const upstream = jsonResponse({ documents: [] });
    mockProxyJson.mockResolvedValue(upstream);
    const res = await documentsGET(authReq("http://localhost:3000/api/resume-builder/documents"));
    expect(mockProxyJson).toHaveBeenCalledWith(
      expect.anything(),
      "/resume-builder/documents",
    );
    expect(res).toBe(upstream);
  });
});

describe("documents/[id]", () => {
  it("GET loads the document by id", async () => {
    const upstream = jsonResponse({ id: "doc-1" });
    mockProxyJson.mockResolvedValue(upstream);
    const res = await documentGET(
      authReq("http://localhost:3000/api/resume-builder/documents/doc-1"),
      DOC_PARAMS,
    );
    expect(mockProxyJson).toHaveBeenCalledWith(
      expect.anything(),
      "/resume-builder/documents/doc-1",
    );
    expect(res).toBe(upstream);
  });

  it("GET URL-encodes an id with special characters", async () => {
    await documentGET(
      authReq("http://localhost:3000/api/resume-builder/documents/doc%201"),
      { params: Promise.resolve({ id: "doc 1/x" }) },
    );
    expect(mockProxyJson.mock.calls[0][1]).toBe(
      "/resume-builder/documents/doc%201%2Fx",
    );
  });

  it("PUT saves via proxyJson with method + body", async () => {
    const upstream = jsonResponse({ id: "doc-1" });
    mockProxyJson.mockResolvedValue(upstream);
    const body = JSON.stringify({ templateId: "classic", title: "Renamed" });
    const res = await documentPUT(
      authReq("http://localhost:3000/api/resume-builder/documents/doc-1", {
        method: "PUT",
        body,
      }),
      DOC_PARAMS,
    );
    expect(mockProxyJson).toHaveBeenCalledWith(
      expect.anything(),
      "/resume-builder/documents/doc-1",
      { method: "PUT", body },
    );
    expect(res).toBe(upstream);
  });
});

describe("export", () => {
  const pdf = () =>
    new Response("%PDF-fake", {
      status: 200,
      headers: {
        "content-type": "application/pdf",
        "content-disposition": 'attachment; filename="alex-morgan.pdf"',
        "content-length": "9",
      },
    });

  it("GET forwards the query string and bearer, and passes the file headers through", async () => {
    mockBackendFetch.mockResolvedValue(pdf());
    const res = await exportGET(
      authReq("http://localhost:3000/api/resume-builder/documents/doc-1/export?format=pdf"),
      DOC_PARAMS,
    );
    expect(mockBackendFetch).toHaveBeenCalledTimes(1);
    const [path, init] = mockBackendFetch.mock.calls[0] as [string, RequestInit];
    expect(path).toBe("/resume-builder/documents/doc-1/export?format=pdf");
    expect(init.method).toBe("GET");
    expect((init.headers as Headers).get("authorization")).toBe("Bearer test-token");
    expect(res.status).toBe(200);
    expect(await res.text()).toBe("%PDF-fake");
    expect(res.headers.get("content-type")).toBe("application/pdf");
    expect(res.headers.get("content-disposition")).toBe(
      'attachment; filename="alex-morgan.pdf"',
    );
    expect(res.headers.get("content-length")).toBe("9");
  });

  it("GET sends no Authorization header when the browser sent no token", async () => {
    mockBackendFetch.mockResolvedValue(pdf());
    await exportGET(
      req("http://localhost:3000/api/resume-builder/documents/doc-1/export?format=docx"),
      DOC_PARAMS,
    );
    const [, init] = mockBackendFetch.mock.calls[0] as [string, RequestInit];
    expect((init.headers as Headers).get("authorization")).toBeNull();
  });

  it("GET forwards an upstream error status with its body", async () => {
    mockBackendFetch.mockResolvedValue(
      new Response("not found", { status: 404 }),
    );
    const res = await exportGET(
      authReq("http://localhost:3000/api/resume-builder/documents/doc-1/export?format=pdf"),
      DOC_PARAMS,
    );
    expect(res.status).toBe(404);
    expect(await res.text()).toBe("not found");
  });

  it("POST forwards the body as JSON and keeps the query string", async () => {
    mockBackendFetch.mockResolvedValue(pdf());
    const body = JSON.stringify({ templateId: "minimal" });
    const res = await exportPOST(
      authReq("http://localhost:3000/api/resume-builder/documents/doc-1/export?format=docx", {
        method: "POST",
        body,
      }),
      DOC_PARAMS,
    );
    const [path, init] = mockBackendFetch.mock.calls[0] as [string, RequestInit];
    expect(path).toBe("/resume-builder/documents/doc-1/export?format=docx");
    expect(init.method).toBe("POST");
    expect((init.headers as Headers).get("content-type")).toBe("application/json");
    expect(init.body).toBe(body);
    expect(res.status).toBe(200);
  });

  it("POST with an empty body sends no content type and no body", async () => {
    mockBackendFetch.mockResolvedValue(pdf());
    await exportPOST(
      authReq("http://localhost:3000/api/resume-builder/documents/doc-1/export?format=pdf", {
        method: "POST",
      }),
      DOC_PARAMS,
    );
    const [, init] = mockBackendFetch.mock.calls[0] as [string, RequestInit];
    expect((init.headers as Headers).get("content-type")).toBeNull();
    expect(init.body).toBeUndefined();
  });
});

describe("save-to-profile POST", () => {
  it("forwards the label and returns the saved resume id", async () => {
    const upstream = jsonResponse({ resumeId: "resume-7", label: "Modern resume" });
    mockProxyJson.mockResolvedValue(upstream);
    const body = JSON.stringify({ label: "Modern resume" });
    const res = await savePOST(
      authReq(
        "http://localhost:3000/api/resume-builder/documents/doc-1/save-to-profile",
        { method: "POST", body },
      ),
      DOC_PARAMS,
    );
    expect(mockProxyJson).toHaveBeenCalledWith(
      expect.anything(),
      "/resume-builder/documents/doc-1/save-to-profile",
      { method: "POST", body },
    );
    expect(res).toBe(upstream);
  });
});

describe("copilot POST", () => {
  it("forwards the Co-Pilot request and returns the options verbatim", async () => {
    const upstream = jsonResponse({
      options: [{ option_id: "1", angle_tag: "Impact-led", content: ["Led X"] }],
    });
    mockProxyJson.mockResolvedValue(upstream);
    const body = JSON.stringify({
      active_section: "experience",
      user_context: "Led a team",
      resume_state: {},
    });
    const res = await copilotPOST(
      authReq(
        "http://localhost:3000/api/resume-builder/documents/doc-1/copilot",
        { method: "POST", body },
      ),
      DOC_PARAMS,
    );
    expect(mockProxyJson).toHaveBeenCalledWith(
      expect.anything(),
      "/resume-builder/documents/doc-1/copilot",
      { method: "POST", body },
    );
    expect(res).toBe(upstream);
  });
});
