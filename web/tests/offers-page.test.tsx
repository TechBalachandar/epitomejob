// B4 — Offers page: the inline editor must use the validating parse,
// so non-numeric input can never store NaN and the comparison table
// can never show $NaN. Two tracker OFFER items seed two rows, so the
// comparison table renders and the total-cash row is observable.
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import type { ReactNode } from "react";
import OffersPage from "@/app/offers/page";
import type { TrackerDto } from "@/lib/types";

const mockTrackerItems = vi.hoisted((): TrackerDto[] => [
  {
    id: "trk-1",
    jobId: "job-1",
    title: "Senior Engineer",
    company: "Acme",
    resumeId: null,
    status: "OFFER",
    notes: "",
    createdAt: "2026-11-01T00:00:00Z",
    updatedAt: "2026-11-01T00:00:00Z",
  },
  {
    id: "trk-2",
    jobId: "job-2",
    title: "Engineer",
    company: "Beta",
    resumeId: null,
    status: "OFFER",
    notes: "",
    createdAt: "2026-11-01T00:00:00Z",
    updatedAt: "2026-11-01T00:00:00Z",
  },
]);

vi.mock("next/navigation", () => ({
  useSearchParams: () => new URLSearchParams(),
}));
vi.mock("@/lib/api", () => ({
  useTracker: () => ({
    data: mockTrackerItems,
    isLoading: false,
    isError: false,
  }),
  useJob: () => ({ data: undefined }),
}));

function wrapper({ children }: { children: ReactNode }) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}

describe("Offers page inline editor (B4)", () => {
  it("typing non-numeric text into an inline salary box never produces $NaN", async () => {
    const user = userEvent.setup();
    render(<OffersPage />, { wrapper });
    const base = await screen.findByLabelText("Base salary for Acme");
    await user.clear(base);
    await user.type(base, "abc");
    expect(document.body.textContent).not.toContain("NaN");
    expect(base).toHaveValue("");
    expect(screen.getByTestId("offer-cell-totalCash-tracker-trk-1")).toHaveTextContent("—");
  });

  it("typing a valid figure still stores it and the total cash updates", async () => {
    const user = userEvent.setup();
    render(<OffersPage />, { wrapper });
    const base = await screen.findByLabelText("Base salary for Beta");
    await user.clear(base);
    await user.type(base, "150000");
    expect(base).toHaveValue("150000");
    expect(screen.getByTestId("offer-cell-totalCash-tracker-trk-2")).toHaveTextContent("$150,000");
    expect(document.body.textContent).not.toContain("NaN");
  });
});
