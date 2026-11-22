// Feature 7 — Offer Comparison: pure logic plus the comparison table.
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { ReactNode } from "react";
import {
  compareOffers,
  emptyManualOfferFields,
  formatMoney,
  manualOfferFromFields,
  offerFromTracker,
  parseMoney,
  parseTrackerIdsParam,
  totalCash,
  validateManualOffer,
  type OfferInput,
} from "@/lib/offers";
import { OfferComparison } from "@/components/offers/offer-comparison";
import type { TrackerDto } from "@/lib/types";

function wrapper({ children }: { children: ReactNode }) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}

function offer(overrides: Partial<OfferInput> = {}): OfferInput {
  return {
    id: "offer-a",
    company: "Acme",
    title: "Senior Engineer",
    location: "Reston, VA",
    baseSalary: 150000,
    bonus: 10000,
    signingBonus: 5000,
    currency: "USD",
    visaSupport: "unknown",
    workType: "Hybrid",
    benefits: ["Health insurance", "401k match"],
    notes: "",
    source: "manual",
    ...overrides,
  };
}

function trackerItem(overrides: Partial<TrackerDto> = {}): TrackerDto {
  return {
    id: "trk-1",
    jobId: "job-1",
    title: "Senior Engineer",
    company: "Acme",
    resumeId: null,
    status: "OFFER",
    notes: "Recruiter call went well",
    createdAt: "2026-11-01T00:00:00Z",
    updatedAt: "2026-11-01T00:00:00Z",
    ...overrides,
  };
}

describe("offerFromTracker", () => {
  it("carries title/company and leaves figures empty for the user", () => {
    const o = offerFromTracker(trackerItem());
    expect(o.company).toBe("Acme");
    expect(o.title).toBe("Senior Engineer");
    expect(o.baseSalary).toBeNull();
    expect(o.bonus).toBeNull();
    expect(o.source).toBe("tracker");
    expect(o.trackerId).toBe("trk-1");
    expect(o.jobId).toBe("job-1");
    expect(o.notes).toBe("Recruiter call went well");
  });
});

describe("totalCash and formatMoney", () => {
  it("adds base + bonus, and null when no base is known", () => {
    expect(totalCash(offer())).toBe(160000);
    expect(totalCash(offer({ bonus: null }))).toBe(150000);
    expect(totalCash(offer({ baseSalary: null }))).toBeNull();
  });

  it("formats money and renders a dash for unknown figures", () => {
    expect(formatMoney(150000)).toContain("150,000");
    expect(formatMoney(null)).toBe("—");
  });

  it("B4: never renders NaN — a non-finite figure is unknown, not $NaN", () => {
    expect(formatMoney(Number.NaN)).toBe("—");
    expect(totalCash(offer({ baseSalary: Number.NaN }))).toBeNull();
    expect(totalCash(offer({ baseSalary: 150000, bonus: Number.NaN }))).toBe(150000);
  });
});

describe("parseMoney (shared by the manual form and inline editors)", () => {
  it("parses plain and formatted figures", () => {
    expect(parseMoney("150000")).toBe(150000);
    expect(parseMoney("150,000")).toBe(150000);
    expect(parseMoney("$120,000")).toBe(120000);
  });

  it("B4: returns null — never NaN — for blank, non-numeric or negative input", () => {
    expect(parseMoney("")).toBeNull();
    expect(parseMoney("   ")).toBeNull();
    expect(parseMoney("abc")).toBeNull();
    expect(parseMoney("12a")).toBeNull();
    expect(parseMoney("-5000")).toBeNull();
  });

  it("N1: symbols-only input is null, not $0 — stripping must leave a digit", () => {
    expect(parseMoney("$")).toBeNull();
    expect(parseMoney("$,,,")).toBeNull();
    expect(parseMoney("$$")).toBeNull();
    expect(parseMoney("0")).toBe(0);
  });
});

describe("compareOffers", () => {
  const a = offer({ id: "a", baseSalary: 150000, bonus: 10000, visaSupport: "yes" });
  const b = offer({ id: "b", company: "Beta", baseSalary: 170000, bonus: 5000, visaSupport: "no" });

  it("names the higher base salary and total cash the winner", () => {
    const rows = compareOffers([a, b]);
    expect(rows.find((r) => r.key === "baseSalary")?.bestIds).toEqual(["b"]);
    expect(rows.find((r) => r.key === "totalCash")?.bestIds).toEqual(["b"]);
    expect(rows.find((r) => r.key === "bonus")?.bestIds).toEqual(["a"]);
  });

  it("names visa support yes the winner, and never location or benefits", () => {
    const rows = compareOffers([a, b]);
    expect(rows.find((r) => r.key === "visaSupport")?.bestIds).toEqual(["a"]);
    expect(rows.find((r) => r.key === "location")?.bestIds).toEqual([]);
    expect(rows.find((r) => r.key === "benefits")?.bestIds).toEqual([]);
    expect(rows.find((r) => r.key === "workType")?.bestIds).toEqual([]);
  });

  it("names no winner on a tie, or when a figure is missing everywhere", () => {
    const rows = compareOffers([
      offer({ id: "a", baseSalary: 150000, signingBonus: null }),
      offer({ id: "b", baseSalary: 150000, signingBonus: null }),
    ]);
    expect(rows.find((r) => r.key === "baseSalary")?.bestIds).toEqual([]);
    expect(rows.find((r) => r.key === "signingBonus")?.bestIds).toEqual([]);
  });

  it("does not invent a market row", () => {
    const rows = compareOffers([a, b]);
    expect(rows.map((r) => r.key)).not.toContain("marketRange");
    expect(rows.map((r) => r.label).join(" ")).not.toMatch(/market/i);
  });
});

describe("manual offer validation", () => {
  it("requires company, title and a numeric base salary", () => {
    expect(validateManualOffer(emptyManualOfferFields())).toEqual([
      "Company is required.",
      "Job title is required.",
      "Base salary is required to compare offers.",
    ]);
    expect(
      validateManualOffer({
        ...emptyManualOfferFields(),
        company: "Acme",
        title: "Engineer",
        baseSalary: "abc",
      }),
    ).toEqual(["Base salary must be a number (e.g. 150000)."]);
  });

  it("builds an offer from valid fields, splitting benefits", () => {
    const o = manualOfferFromFields({
      ...emptyManualOfferFields(),
      company: "Acme",
      title: "Engineer",
      baseSalary: "150,000",
      bonus: "10000",
      benefits: "Health insurance, 401k match",
      visaSupport: "yes",
    });
    expect(o.baseSalary).toBe(150000);
    expect(o.bonus).toBe(10000);
    expect(o.benefits).toEqual(["Health insurance", "401k match"]);
    expect(o.visaSupport).toBe("yes");
    expect(o.source).toBe("manual");
  });
});

describe("parseTrackerIdsParam", () => {
  it("splits comma-separated ids and ignores blanks", () => {
    expect(parseTrackerIdsParam("a,b,, c ")).toEqual(["a", "b", "c"]);
    expect(parseTrackerIdsParam(null)).toEqual([]);
    expect(parseTrackerIdsParam("")).toEqual([]);
  });
});

describe("OfferComparison", () => {
  it("highlights the better base salary and never the location row", () => {
    const a = offer({ id: "a", baseSalary: 150000, location: "Reston, VA" });
    const b = offer({ id: "b", company: "Beta", baseSalary: 170000, location: "Remote" });
    render(<OfferComparison offers={[a, b]} />, { wrapper });

    const baseA = screen.getByTestId("offer-cell-baseSalary-a");
    const baseB = screen.getByTestId("offer-cell-baseSalary-b");
    expect(baseB).toHaveTextContent("Best");
    expect(baseA).not.toHaveTextContent("Best");

    expect(screen.getByTestId("offer-cell-location-a")).not.toHaveTextContent("Best");
    expect(screen.getByTestId("offer-cell-location-b")).not.toHaveTextContent("Best");
    expect(screen.getByTestId("offer-cell-benefits-a")).toHaveTextContent("Health insurance");
  });

  it("states that no market salary data is shown", () => {
    render(<OfferComparison offers={[offer({ id: "a" }), offer({ id: "b" })]} />, { wrapper });
    expect(screen.getByText(/No market salary data is shown/)).toBeInTheDocument();
  });
});

describe("totalCash and formatMoney — edge cases", () => {
  it("a zero base salary totals to the bonus, not null", () => {
    expect(totalCash(offer({ baseSalary: 0, bonus: 5000 }))).toBe(5000);
    expect(totalCash(offer({ baseSalary: 0, bonus: null }))).toBe(0);
  });

  it("formats zero and large figures", () => {
    expect(formatMoney(0)).toContain("0");
    expect(formatMoney(1000000)).toContain("1,000,000");
  });

  it("formats a non-USD currency when given one", () => {
    const eur = formatMoney(150000, "EUR");
    expect(eur).toContain("150");
    expect(eur).not.toBe(formatMoney(150000, "USD"));
  });

  it("a negative figure formats with a minus sign, never as a dash", () => {
    expect(formatMoney(-5000)).toContain("-");
    expect(formatMoney(-5000)).not.toBe("—");
  });
});

describe("compareOffers — edge cases", () => {
  it("returns eight rows with no winners for zero offers", () => {
    const rows = compareOffers([]);
    expect(rows).toHaveLength(8);
    expect(rows.every((r) => r.values.length === 0)).toBe(true);
    expect(rows.every((r) => r.bestIds.length === 0)).toBe(true);
  });

  it("names no winner for a single offer", () => {
    const rows = compareOffers([offer({ id: "only" })]);
    expect(rows.every((r) => r.bestIds.length === 0)).toBe(true);
    expect(rows.find((r) => r.key === "totalCash")?.values).toEqual(["$160,000"]);
  });

  it("shares the win between tied top offers, and clears it when all three tie", () => {
    const rows = compareOffers([
      offer({ id: "a", baseSalary: 170000, bonus: 0 }),
      offer({ id: "b", company: "Beta", baseSalary: 170000, bonus: 0 }),
      offer({ id: "c", company: "Gamma", baseSalary: 150000, bonus: 0 }),
    ]);
    expect(rows.find((r) => r.key === "baseSalary")?.bestIds).toEqual(["a", "b"]);

    const tied = compareOffers([
      offer({ id: "a", baseSalary: 150000 }),
      offer({ id: "b", baseSalary: 150000 }),
      offer({ id: "c", baseSalary: 150000 }),
    ]);
    expect(tied.find((r) => r.key === "baseSalary")?.bestIds).toEqual([]);
  });

  it("names no winner when only one offer states the figure", () => {
    const rows = compareOffers([
      offer({ id: "a", signingBonus: 10000 }),
      offer({ id: "b", signingBonus: null }),
    ]);
    expect(rows.find((r) => r.key === "signingBonus")?.bestIds).toEqual([]);
    expect(rows.find((r) => r.key === "signingBonus")?.values).toEqual(["$10,000", "—"]);
  });

  it("total cash can crown a different winner than base salary", () => {
    const rows = compareOffers([
      offer({ id: "a", baseSalary: 170000, bonus: 0 }),
      offer({ id: "b", company: "Beta", baseSalary: 150000, bonus: 40000 }),
    ]);
    expect(rows.find((r) => r.key === "baseSalary")?.bestIds).toEqual(["a"]);
    expect(rows.find((r) => r.key === "totalCash")?.bestIds).toEqual(["b"]);
    expect(rows.find((r) => r.key === "totalCash")?.note).toContain("Bonus counts only where stated");
  });

  it("visa: two yes offers share the win; all yes or all unknown name none", () => {
    const twoYes = compareOffers([
      offer({ id: "a", visaSupport: "yes" }),
      offer({ id: "b", visaSupport: "yes" }),
      offer({ id: "c", visaSupport: "no" }),
    ]);
    expect(twoYes.find((r) => r.key === "visaSupport")?.bestIds).toEqual(["a", "b"]);

    const allYes = compareOffers([
      offer({ id: "a", visaSupport: "yes" }),
      offer({ id: "b", visaSupport: "yes" }),
    ]);
    expect(allYes.find((r) => r.key === "visaSupport")?.bestIds).toEqual([]);

    const allUnknown = compareOffers([
      offer({ id: "a", visaSupport: "unknown" }),
      offer({ id: "b", visaSupport: "unknown" }),
    ]);
    expect(allUnknown.find((r) => r.key === "visaSupport")?.bestIds).toEqual([]);
    expect(allUnknown.find((r) => r.key === "visaSupport")?.values).toEqual(["Unknown", "Unknown"]);
  });

  it("visa 'no' never wins, even against 'unknown'", () => {
    const rows = compareOffers([
      offer({ id: "a", visaSupport: "no" }),
      offer({ id: "b", visaSupport: "unknown" }),
    ]);
    expect(rows.find((r) => r.key === "visaSupport")?.bestIds).toEqual([]);
  });

  it("renders dashes for missing location, work type and benefits", () => {
    const rows = compareOffers([
      offer({ id: "a", location: "", workType: null, benefits: [] }),
      offer({ id: "b", location: "", workType: null, benefits: [] }),
    ]);
    expect(rows.find((r) => r.key === "location")?.values).toEqual(["—", "—"]);
    expect(rows.find((r) => r.key === "workType")?.values).toEqual(["—", "—"]);
    expect(rows.find((r) => r.key === "benefits")?.values).toEqual(["—", "—"]);
  });

  it("joins multiple benefits with commas", () => {
    const rows = compareOffers([
      offer({ id: "a", benefits: ["Health", "401k", "Remote stipend"] }),
      offer({ id: "b", benefits: ["Health"] }),
    ]);
    expect(rows.find((r) => r.key === "benefits")?.values[0]).toBe("Health, 401k, Remote stipend");
  });

  it("row order is stable: money, total cash, visa, then preference rows", () => {
    const rows = compareOffers([offer({ id: "a" }), offer({ id: "b" })]);
    expect(rows.map((r) => r.key)).toEqual([
      "baseSalary",
      "bonus",
      "signingBonus",
      "totalCash",
      "visaSupport",
      "location",
      "workType",
      "benefits",
    ]);
  });
});

describe("offerFromTracker — edge cases", () => {
  it("carries a null notes value as an empty string", () => {
    const o = offerFromTracker(trackerItem({ notes: null }));
    expect(o.notes).toBe("");
  });

  it("keeps the tracker id stable across repeated conversions", () => {
    const item = trackerItem();
    expect(offerFromTracker(item).id).toBe(offerFromTracker(item).id);
    expect(offerFromTracker(item).id).toBe("tracker-trk-1");
  });

  it("a tracker offer with no job id still converts", () => {
    const o = offerFromTracker(trackerItem({ jobId: null as unknown as string }));
    expect(o.jobId).toBeNull();
    expect(o.source).toBe("tracker");
  });
});

describe("manual offer validation — adversarial", () => {
  it("rejects negative money and non-numeric bonus", () => {
    expect(
      validateManualOffer({
        ...emptyManualOfferFields(),
        company: "Acme",
        title: "Engineer",
        baseSalary: "-1000",
        bonus: "abc",
        signingBonus: "-50",
      }),
    ).toEqual([
      "Base salary must be a number (e.g. 150000).",
      "Bonus must be a number.",
      "Signing bonus must be a number.",
    ]);
  });

  it("rejects whitespace-only company and title", () => {
    expect(
      validateManualOffer({
        ...emptyManualOfferFields(),
        company: "   ",
        title: "  ",
        baseSalary: "150000",
      }),
    ).toEqual(["Company is required.", "Job title is required."]);
  });

  it("accepts $ and comma formatting and decimals", () => {
    expect(
      validateManualOffer({
        ...emptyManualOfferFields(),
        company: "Acme",
        title: "Engineer",
        baseSalary: "$150,000.50",
      }),
    ).toEqual([]);
  });

  it("accepts a zero base salary (a stated figure, even if unusual)", () => {
    expect(
      validateManualOffer({
        ...emptyManualOfferFields(),
        company: "Acme",
        title: "Engineer",
        baseSalary: "0",
      }),
    ).toEqual([]);
  });

  it("collects every error at once instead of stopping at the first", () => {
    expect(validateManualOffer(emptyManualOfferFields())).toHaveLength(3);
  });
});

describe("manualOfferFromFields — edge cases", () => {
  it("defaults a blank currency to USD and a blank work type to null", () => {
    const o = manualOfferFromFields({
      ...emptyManualOfferFields(),
      company: "Acme",
      title: "Engineer",
      baseSalary: "150000",
      currency: "   ",
      workType: "  ",
    });
    expect(o.currency).toBe("USD");
    expect(o.workType).toBeNull();
  });

  it("drops empty benefit entries from trailing commas and blanks", () => {
    const o = manualOfferFromFields({
      ...emptyManualOfferFields(),
      company: "Acme",
      title: "Engineer",
      baseSalary: "150000",
      benefits: "Health, , 401k,",
    });
    expect(o.benefits).toEqual(["Health", "401k"]);
  });

  it("trims company, title, location and notes", () => {
    const o = manualOfferFromFields({
      ...emptyManualOfferFields(),
      company: "  Acme  ",
      title: "  Engineer ",
      location: " Reston ",
      baseSalary: " 150000 ",
      notes: "  Great team  ",
    });
    expect(o.company).toBe("Acme");
    expect(o.title).toBe("Engineer");
    expect(o.location).toBe("Reston");
    expect(o.baseSalary).toBe(150000);
    expect(o.notes).toBe("Great team");
  });

  it("parses $ and comma money for bonus and signing bonus", () => {
    const o = manualOfferFromFields({
      ...emptyManualOfferFields(),
      company: "Acme",
      title: "Engineer",
      baseSalary: "$150,000",
      bonus: "$10,000",
      signingBonus: "$5,000",
    });
    expect(o.bonus).toBe(10000);
    expect(o.signingBonus).toBe(5000);
  });

  it("generates a unique id per offer", async () => {
    const { newOfferId } = await import("@/lib/offers");
    const ids = new Set(Array.from({ length: 20 }, () => newOfferId()));
    expect(ids.size).toBe(20);
    expect([...ids][0]).toMatch(/^offer-/);
  });
});

describe("parseTrackerIdsParam — edge cases", () => {
  it("handles a single id and surrounding whitespace", () => {
    expect(parseTrackerIdsParam("  trk-1  ")).toEqual(["trk-1"]);
  });

  it("returns empty for an all-commas value", () => {
    expect(parseTrackerIdsParam(",,,")).toEqual([]);
  });
});

describe("OfferComparison — edge cases", () => {
  const fetchMock = vi.fn();

  beforeEach(() => {
    fetchMock.mockReset();
    vi.stubGlobal("fetch", fetchMock);
  });
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  function json(body: unknown, status = 200): Response {
    return new Response(JSON.stringify(body), {
      status,
      headers: { "Content-Type": "application/json" },
    });
  }

  it("renders with zero offers without crashing and names no winner", () => {
    render(<OfferComparison offers={[]} />, { wrapper });
    expect(screen.getByText("Offer comparison")).toBeInTheDocument();
    expect(screen.queryByText("Best")).not.toBeInTheDocument();
  });

  it("names no winner for a single offer", () => {
    render(<OfferComparison offers={[offer({ id: "only" })]} />, { wrapper });
    expect(screen.queryByText("Best")).not.toBeInTheDocument();
    expect(screen.getByTestId("offer-cell-totalCash-only")).toHaveTextContent("$160,000");
  });

  it("shares Best between tied top offers in the base row", () => {
    render(
      <OfferComparison
        offers={[
          offer({ id: "a", baseSalary: 170000, bonus: 0 }),
          offer({ id: "b", company: "Beta", baseSalary: 170000, bonus: 0 }),
          offer({ id: "c", company: "Gamma", baseSalary: 150000, bonus: 0 }),
        ]}
      />,
      { wrapper },
    );
    expect(screen.getByTestId("offer-cell-baseSalary-a")).toHaveTextContent("Best");
    expect(screen.getByTestId("offer-cell-baseSalary-b")).toHaveTextContent("Best");
    expect(screen.getByTestId("offer-cell-baseSalary-c")).not.toHaveTextContent("Best");
  });

  it("highlights the visa row winner and shows the total-cash caveat", () => {
    render(
      <OfferComparison
        offers={[
          offer({ id: "a", visaSupport: "yes" }),
          offer({ id: "b", company: "Beta", visaSupport: "no" }),
        ]}
      />,
      { wrapper },
    );
    expect(screen.getByTestId("offer-cell-visaSupport-a")).toHaveTextContent("Best");
    expect(screen.getByTestId("offer-cell-visaSupport-b")).not.toHaveTextContent("Best");
    expect(screen.getByText(/Bonus counts only where stated/)).toBeInTheDocument();
    expect(screen.getByText(/Equity, stock and benefits are not converted/)).toBeInTheDocument();
  });

  it("shows dashes for missing figures and missing text fields", () => {
    render(
      <OfferComparison
        offers={[
          offer({ id: "a", baseSalary: null, bonus: null, signingBonus: null, location: "", workType: null, benefits: [] }),
          offer({ id: "b", baseSalary: null, bonus: null, signingBonus: null, location: "", workType: null, benefits: [] }),
        ]}
      />,
      { wrapper },
    );
    for (const key of ["baseSalary", "bonus", "signingBonus", "totalCash", "location", "workType", "benefits"]) {
      expect(screen.getByTestId(`offer-cell-${key}-a`)).toHaveTextContent("—");
      expect(screen.getByTestId(`offer-cell-${key}-b`)).toHaveTextContent("—");
    }
    expect(screen.queryByText("Best")).not.toBeInTheDocument();
  });

  it("renders special characters in company and title safely", () => {
    render(
      <OfferComparison
        offers={[
          offer({ id: "a", company: "A&B <Corp>", title: "C++ Engineer (R&D)" }),
          offer({ id: "b", company: "Beta", title: "Engineer" }),
        ]}
      />,
      { wrapper },
    );
    expect(screen.getByText("A&B <Corp>")).toBeInTheDocument();
    expect(screen.getByText("C++ Engineer (R&D)")).toBeInTheDocument();
  });

  it("shows the posting's listed range for a tracker-sourced offer", async () => {
    fetchMock.mockImplementation((input: string | URL | Request) => {
      if (input.toString() === "/api/jobs/job-1") {
        return Promise.resolve(
          json({
            id: "job-1",
            title: "Senior Engineer",
            company: "Acme",
            location: "Reston, VA",
            workType: "Hybrid",
            salaryMin: 120000,
            salaryMax: 150000,
            currency: "USD",
            sponsorsH1b: true,
            skills: ["Java"],
            summary: "Build services.",
            role: "Backend",
            postedAt: "2026-11-10T00:00:00Z",
            source: "SEED",
            sourceUrl: null,
          }),
        );
      }
      throw new Error(`unexpected fetch ${input}`);
    });
    render(
      <OfferComparison
        offers={[offerFromTracker(trackerItem()), offer({ id: "b", company: "Beta" })]}
      />,
      { wrapper },
    );
    await waitFor(() =>
      expect(screen.getByText(/Listed range: \$120k – \$150k USD/)).toBeInTheDocument(),
    );
  });

  it("B5: a salary-less posting shows 'Not listed', never a fake $0k range", async () => {
    fetchMock.mockImplementation((input: string | URL | Request) => {
      if (input.toString() === "/api/jobs/job-1") {
        return Promise.resolve(
          json({
            id: "job-1",
            title: "Senior Engineer",
            company: "Acme",
            location: "Reston, VA",
            workType: "Hybrid",
            salaryMin: null,
            salaryMax: null,
            currency: "USD",
            sponsorsH1b: true,
            skills: ["Java"],
            summary: "Build services.",
            role: "Backend",
            postedAt: "2026-11-10T00:00:00Z",
            source: "SEED",
            sourceUrl: null,
          }),
        );
      }
      throw new Error(`unexpected fetch ${input}`);
    });
    render(
      <OfferComparison
        offers={[offerFromTracker(trackerItem()), offer({ id: "b", company: "Beta" })]}
      />,
      { wrapper },
    );
    await waitFor(() =>
      expect(screen.getByText(/Listed range: Not listed/)).toBeInTheDocument(),
    );
    expect(screen.queryByText(/\$0k/)).not.toBeInTheDocument();
  });

  it("shows no listed range when the tracker job cannot be loaded", async () => {
    fetchMock.mockImplementation(() => Promise.resolve(json({ message: "Not found" }, 404)));
    render(
      <OfferComparison
        offers={[offerFromTracker(trackerItem()), offer({ id: "b", company: "Beta" })]}
      />,
      { wrapper },
    );
    // The table itself still renders; the failed lookup adds no range line.
    expect(screen.getByText("Offer comparison")).toBeInTheDocument();
    await waitFor(() =>
      expect(screen.getByTestId("offer-cell-baseSalary-tracker-trk-1")).toBeInTheDocument(),
    );
    expect(screen.queryByText(/Listed range/)).not.toBeInTheDocument();
  });
});
