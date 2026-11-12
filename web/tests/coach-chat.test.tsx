// W3 Coach flow tests. fetch is mocked at the BFF boundary (/api/*) —
// the agent-brain / real backend is never involved. Covers sendCoachMessage
// (contract §2 response shape) and the Coach page rendering (reply +
// citations + "Worked with:" line, "Working…" status, rotation after 3s).
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen, waitFor, act } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import CoachPage from "@/app/coach/page";
import { sendCoachMessage } from "@/lib/api";

const COACH_REPLY = {
  reply:
    "Your resume is in good shape — add one quantified result to your Navy Federal bullet.",
  citations: [
    {
      docId: "resume:10000000-0000-4000-8000-000000000001",
      chunk:
        "Principal Mobile Architect at Navy Federal, leading 10+ engineers on banking apps.",
      score: 0.87,
      source: "resume",
    },
  ],
  actions: [],
  specialistsUsed: ["resume", "matching"],
};

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

function mockFetch(
  impl: (url: string, init?: RequestInit) => Response | Promise<Response>,
) {
  return vi.fn((input: string | URL | Request, init?: RequestInit) => {
    const url = typeof input === "string" ? input : input.toString();
    return Promise.resolve(impl(url, init));
  });
}

beforeEach(() => {
  vi.useRealTimers();
});

afterEach(() => {
  vi.unstubAllGlobals();
  vi.useRealTimers();
});

describe("sendCoachMessage (contract §2)", () => {
  it("POSTs {message} to /api/coach/chat and returns the agent reply", async () => {
    const fetchMock = mockFetch((url, init) => {
      expect(url).toBe("/api/coach/chat");
      expect(init?.method).toBe("POST");
      expect(init?.body).toBe(
        JSON.stringify({ message: "How can I improve my resume?" }),
      );
      return jsonResponse(COACH_REPLY);
    });
    vi.stubGlobal("fetch", fetchMock);

    const res = await sendCoachMessage("How can I improve my resume?");
    expect(res.reply).toMatch(/resume is in good shape/);
    expect(res.citations).toHaveLength(1);
    expect(res.citations[0].docId).toBe(
      "resume:10000000-0000-4000-8000-000000000001",
    );
    expect(res.citations[0].score).toBe(0.87);
    expect(res.specialistsUsed).toEqual(["resume", "matching"]);
  });

  it("throws on a 502 when the agent is unreachable", async () => {
    const fetchMock = mockFetch(() =>
      jsonResponse({ code: "AGENT_UNAVAILABLE", message: "Coach agent is not reachable" }, 502),
    );
    vi.stubGlobal("fetch", fetchMock);

    await expect(sendCoachMessage("hello")).rejects.toMatchObject({
      status: 502,
    });
  });
});

describe("CoachPage (W3, real agent call)", () => {
  it("sends a message and renders the reply with citations and Worked with line", async () => {
    const user = userEvent.setup();
    // Deferred fetch so the "Working…" state is observable before the reply.
    let resolveFetch!: (res: Response) => void;
    const fetchMock = mockFetch((url) => {
      if (url === "/api/coach/chat") {
        return new Promise<Response>((resolve) => {
          resolveFetch = resolve;
        });
      }
      return jsonResponse({ message: "not found" }, 404);
    });
    vi.stubGlobal("fetch", fetchMock);

    render(<CoachPage />);

    await user.type(
      screen.getByLabelText("Message the Coach"),
      "How can I improve my resume?",
    );
    await user.click(screen.getByRole("button", { name: "Send message" }));

    expect(screen.getByText("Working…")).toBeInTheDocument();
    resolveFetch(jsonResponse(COACH_REPLY));
    await waitFor(() =>
      expect(
        screen.getByText(/resume is in good shape/),
      ).toBeInTheDocument(),
    );
    expect(
      screen.getByText(/Principal Mobile Architect at Navy Federal/),
    ).toBeInTheDocument();
    expect(screen.getByText(/resume:10000000/)).toBeInTheDocument();
    expect(screen.getByText(/score 0.87/)).toBeInTheDocument();
    expect(
      screen.getByText("Worked with: resume, matching"),
    ).toBeInTheDocument();
    expect(screen.queryByText("Working…")).not.toBeInTheDocument();
  });

  it("shows an honest error message when the Coach cannot be reached", async () => {
    const user = userEvent.setup();
    const fetchMock = mockFetch(() =>
      jsonResponse({ message: "Coach agent is not reachable" }, 502),
    );
    vi.stubGlobal("fetch", fetchMock);

    render(<CoachPage />);

    await user.type(screen.getByLabelText("Message the Coach"), "hello");
    await user.click(screen.getByRole("button", { name: "Send message" }));

    await waitFor(() =>
      expect(
        screen.getByText(/could not reach the Coach/),
      ).toBeInTheDocument(),
    );
  });

  it("rotates generic status lines after 3s while waiting", async () => {
    vi.useFakeTimers();
    // A fetch that never resolves, so the page stays in the waiting state.
    vi.stubGlobal("fetch", vi.fn(() => new Promise<Response>(() => {})));

    render(<CoachPage />);

    fireEvent.change(screen.getByLabelText("Message the Coach"), {
      target: { value: "Which jobs match me best?" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Send message" }));

    expect(screen.getByText("Working…")).toBeInTheDocument();
    act(() => {
      vi.advanceTimersByTime(3000);
    });
    expect(screen.getByText("Checking your resume…")).toBeInTheDocument();
    act(() => {
      vi.advanceTimersByTime(2500);
    });
    expect(screen.getByText("Scoring matches…")).toBeInTheDocument();
    act(() => {
      vi.advanceTimersByTime(2500);
    });
    expect(screen.getByText("Gathering citations…")).toBeInTheDocument();
  });
});
