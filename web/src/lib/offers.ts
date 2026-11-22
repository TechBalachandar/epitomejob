// Feature 7 — Offer Comparison: pure logic.
//
// Offers come from two real sources only: the user types them in, or
// they are picked from Tracker items in the OFFER stage (which carry
// title/company only — every figure is filled in by the user).
//
// Deliberately absent: market salary data. No market source is
// connected, and the job listings' salaryMin/salaryMax describe the
// posting, not the market — so no market range is shown anywhere.
// "Better" is highlighted only where the numbers make it unambiguous
// (a higher figure, or visa support = yes vs no/unknown). Location,
// work type and benefits are shown without a winner: they depend on
// the user's preference, not on a number.
import type { TrackerDto } from "./types";

export type VisaSupport = "yes" | "no" | "unknown";

export type OfferSource = "manual" | "tracker";

export interface OfferInput {
  id: string;
  company: string;
  title: string;
  location: string;
  baseSalary: number | null;
  bonus: number | null;
  signingBonus: number | null;
  currency: string;
  visaSupport: VisaSupport;
  workType: string | null;
  benefits: string[];
  notes: string;
  source: OfferSource;
  trackerId?: string;
  jobId?: string;
}

export function newOfferId(): string {
  return `offer-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 8)}`;
}

/** A Tracker OFFER item becomes a comparison row with its real
 * title/company — salary and benefits start empty for the user to fill. */
export function offerFromTracker(item: TrackerDto): OfferInput {
  return {
    id: `tracker-${item.id}`,
    company: item.company,
    title: item.title,
    location: "",
    baseSalary: null,
    bonus: null,
    signingBonus: null,
    currency: "USD",
    visaSupport: "unknown",
    workType: null,
    benefits: [],
    notes: item.notes ?? "",
    source: "tracker",
    trackerId: item.id,
    jobId: item.jobId,
  };
}

/** Base + bonus, only when a base salary is known. Missing bonus counts
 * as 0 only in the sense that nothing was stated — the row is labelled
 * so the reader can see that. Never NaN: a non-finite figure is
 * treated as unknown. */
export function totalCash(offer: OfferInput): number | null {
  if (offer.baseSalary === null || !Number.isFinite(offer.baseSalary)) return null;
  const bonus = offer.bonus ?? 0;
  return Number.isFinite(bonus) ? offer.baseSalary + bonus : offer.baseSalary;
}

export function formatMoney(amount: number | null, currency = "USD"): string {
  if (amount === null || !Number.isFinite(amount)) return "—";
  return new Intl.NumberFormat(undefined, {
    style: "currency",
    currency,
    maximumFractionDigits: 0,
  }).format(amount);
}

// --- Comparison rows ---------------------------------------------------------

export type RowKind = "money" | "visa" | "text" | "list";

export interface OfferRow {
  key: string;
  label: string;
  kind: RowKind;
  /** One display value per offer, in offer order. */
  values: string[];
  /** Ids of the offer(s) that win this row — empty when no winner. */
  bestIds: string[];
  /** Shown under the table when a row needs a caveat. */
  note?: string;
}

function moneyValue(offer: OfferInput, get: (o: OfferInput) => number | null): string {
  return formatMoney(get(offer), offer.currency);
}

/** Winners for a numeric row: the highest stated figure. Ties share the
 * win; a row with no figures, or where every figure is equal, has no
 * winner. */
function bestMoneyIds(
  offers: OfferInput[],
  get: (o: OfferInput) => number | null,
): string[] {
  const stated = offers
    .map((o) => ({ id: o.id, value: get(o) }))
    .filter((x): x is { id: string; value: number } => x.value !== null && Number.isFinite(x.value));
  if (stated.length < 2) return [];
  const max = Math.max(...stated.map((x) => x.value));
  const winners = stated.filter((x) => x.value === max).map((x) => x.id);
  return winners.length === offers.length ? [] : winners;
}

/** Visa winner: "yes" beats "no"/"unknown". No winner when all agree. */
function bestVisaIds(offers: OfferInput[]): string[] {
  const yes = offers.filter((o) => o.visaSupport === "yes").map((o) => o.id);
  if (yes.length === 0 || yes.length === offers.length) return [];
  return yes;
}

export function visaSupportLabel(value: VisaSupport): string {
  return value === "yes" ? "Yes" : value === "no" ? "No" : "Unknown";
}

/** Side-by-side rows for two or more offers. Only money and visa rows
 * ever name a winner. */
export function compareOffers(offers: OfferInput[]): OfferRow[] {
  return [
    {
      key: "baseSalary",
      label: "Base salary",
      kind: "money",
      values: offers.map((o) => moneyValue(o, (x) => x.baseSalary)),
      bestIds: bestMoneyIds(offers, (x) => x.baseSalary),
    },
    {
      key: "bonus",
      label: "Annual bonus",
      kind: "money",
      values: offers.map((o) => moneyValue(o, (x) => x.bonus)),
      bestIds: bestMoneyIds(offers, (x) => x.bonus),
    },
    {
      key: "signingBonus",
      label: "Signing bonus",
      kind: "money",
      values: offers.map((o) => moneyValue(o, (x) => x.signingBonus)),
      bestIds: bestMoneyIds(offers, (x) => x.signingBonus),
    },
    {
      key: "totalCash",
      label: "Total cash (base + bonus)",
      kind: "money",
      values: offers.map((o) => moneyValue(o, totalCash)),
      bestIds: bestMoneyIds(offers, totalCash),
      note: "Bonus counts only where stated; an unstated bonus is treated as 0 here.",
    },
    {
      key: "visaSupport",
      label: "Visa support",
      kind: "visa",
      values: offers.map((o) => visaSupportLabel(o.visaSupport)),
      bestIds: bestVisaIds(offers),
    },
    {
      key: "location",
      label: "Location",
      kind: "text",
      values: offers.map((o) => o.location || "—"),
      bestIds: [],
    },
    {
      key: "workType",
      label: "Work type",
      kind: "text",
      values: offers.map((o) => o.workType || "—"),
      bestIds: [],
    },
    {
      key: "benefits",
      label: "Benefits",
      kind: "list",
      values: offers.map((o) =>
        o.benefits.length > 0 ? o.benefits.join(", ") : "—",
      ),
      bestIds: [],
    },
  ];
}

// --- Manual entry --------------------------------------------------------------

export interface ManualOfferFields {
  company: string;
  title: string;
  location: string;
  baseSalary: string;
  bonus: string;
  signingBonus: string;
  currency: string;
  visaSupport: VisaSupport;
  workType: string;
  benefits: string;
  notes: string;
}

export function emptyManualOfferFields(): ManualOfferFields {
  return {
    company: "",
    title: "",
    location: "",
    baseSalary: "",
    bonus: "",
    signingBonus: "",
    currency: "USD",
    visaSupport: "unknown",
    workType: "",
    benefits: "",
    notes: "",
  };
}

/** Validates a money input: a finite, non-negative number, or null
 * for blank / non-numeric input. Shared by the manual form and the
 * inline editors so invalid input can never store NaN. */
export function parseMoney(raw: string): number | null {
  const trimmed = raw.trim();
  if (!trimmed) return null;
  const stripped = trimmed.replace(/[$,]/g, "");
  if (!stripped) return null;
  const n = Number(stripped);
  return Number.isFinite(n) && n >= 0 ? n : null;
}

/** Validation errors for a manual offer. A company and a base salary
 * are required — without a base salary there is nothing to compare. */
export function validateManualOffer(fields: ManualOfferFields): string[] {
  const errors: string[] = [];
  if (!fields.company.trim()) errors.push("Company is required.");
  if (!fields.title.trim()) errors.push("Job title is required.");
  if (!fields.baseSalary.trim()) {
    errors.push("Base salary is required to compare offers.");
  } else if (parseMoney(fields.baseSalary) === null) {
    errors.push("Base salary must be a number (e.g. 150000).");
  }
  if (fields.bonus.trim() && parseMoney(fields.bonus) === null) {
    errors.push("Bonus must be a number.");
  }
  if (fields.signingBonus.trim() && parseMoney(fields.signingBonus) === null) {
    errors.push("Signing bonus must be a number.");
  }
  return errors;
}

export function manualOfferFromFields(fields: ManualOfferFields): OfferInput {
  return {
    id: newOfferId(),
    company: fields.company.trim(),
    title: fields.title.trim(),
    location: fields.location.trim(),
    baseSalary: parseMoney(fields.baseSalary),
    bonus: parseMoney(fields.bonus),
    signingBonus: parseMoney(fields.signingBonus),
    currency: fields.currency.trim() || "USD",
    visaSupport: fields.visaSupport,
    workType: fields.workType.trim() || null,
    benefits: fields.benefits
      .split(",")
      .map((b) => b.trim())
      .filter(Boolean),
    notes: fields.notes.trim(),
    source: "manual",
  };
}

/** Tracker ids from ?trackerIds=a,b — used to pre-pick OFFER items. */
export function parseTrackerIdsParam(value: string | null): string[] {
  if (!value) return [];
  return value
    .split(",")
    .map((s) => s.trim())
    .filter(Boolean);
}
