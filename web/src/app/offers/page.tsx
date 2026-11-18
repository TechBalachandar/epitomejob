// Feature 7 — Offers page.
// Enter offers manually, or pick Tracker items in the OFFER stage to
// compare. Tracker ids passed as ?trackerIds=a,b are pre-picked;
// without the param, every OFFER-stage item is pre-picked. Offers are
// compared in the user's own figures only — no market salary data is
// shown (no market source is connected).
"use client";

import { Suspense, useEffect, useState } from "react";
import { useSearchParams } from "next/navigation";
import { useTracker } from "@/lib/api";
import {
  emptyManualOfferFields,
  manualOfferFromFields,
  offerFromTracker,
  parseMoney,
  parseTrackerIdsParam,
  validateManualOffer,
  type ManualOfferFields,
  type OfferInput,
} from "@/lib/offers";
import { OfferComparison } from "@/components/offers/offer-comparison";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select } from "@/components/ui/select";
import { Textarea } from "@/components/ui/textarea";
import { X } from "lucide-react";

function OffersPageInner() {
  const searchParams = useSearchParams();
  const { data: cards, isLoading, isError } = useTracker();
  const [offers, setOffers] = useState<OfferInput[]>([]);
  const [seeded, setSeeded] = useState(false);
  const [fields, setFields] = useState<ManualOfferFields>(emptyManualOfferFields());
  const [formErrors, setFormErrors] = useState<string[]>([]);
  const [selectedTrackerIds, setSelectedTrackerIds] = useState<Set<string>>(
    () => new Set(parseTrackerIdsParam(searchParams.get("trackerIds"))),
  );

  const offerItems = (cards ?? []).filter((c) => c.status === "OFFER");

  // Seed the comparison once the tracker loads: the ?trackerIds picks,
  // or every OFFER-stage item when no param was given.
  useEffect(() => {
    if (seeded || !cards) return;
    const wanted = selectedTrackerIds.size > 0
      ? offerItems.filter((c) => selectedTrackerIds.has(c.id))
      : offerItems;
    if (wanted.length > 0) setOffers(wanted.map(offerFromTracker));
    setSeeded(true);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [cards, seeded]);

  function toggleTrackerItem(id: string) {
    setSelectedTrackerIds((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
    setOffers((prev) => {
      const trackerKey = `tracker-${id}`;
      if (prev.some((o) => o.id === trackerKey)) {
        return prev.filter((o) => o.id !== trackerKey);
      }
      const item = offerItems.find((c) => c.id === id);
      return item ? [...prev, offerFromTracker(item)] : prev;
    });
  }

  function updateOffer(id: string, patch: Partial<OfferInput>) {
    setOffers((prev) =>
      prev.map((o) => (o.id === id ? { ...o, ...patch } : o)),
    );
  }

  function removeOffer(id: string) {
    setOffers((prev) => prev.filter((o) => o.id !== id));
    if (id.startsWith("tracker-")) {
      setSelectedTrackerIds((prev) => {
        const next = new Set(prev);
        next.delete(id.replace("tracker-", ""));
        return next;
      });
    }
  }

  function addManualOffer() {
    const errors = validateManualOffer(fields);
    setFormErrors(errors);
    if (errors.length > 0) return;
    setOffers((prev) => [...prev, manualOfferFromFields(fields)]);
    setFields(emptyManualOfferFields());
  }

  const set = (key: keyof ManualOfferFields) => (
    e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement>,
  ) => setFields((f) => ({ ...f, [key]: e.target.value }));

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-bold text-slate-900">Compare offers</h1>
        <p className="mt-1 text-sm text-slate-500">
          Pick offers from your tracker or enter them by hand. The better
          value is highlighted only where the numbers decide it. No market
          salary data is shown — no market source is connected.
        </p>
      </div>

      {isLoading && <p className="text-sm text-slate-500">Loading tracker…</p>}
      {isError && (
        <p role="alert" className="text-sm text-rose-600">
          Could not load your tracker. You can still enter offers manually below.
        </p>
      )}

      {offerItems.length > 0 && (
        <Card>
          <CardHeader>
            <CardTitle>Offers in your tracker</CardTitle>
          </CardHeader>
          <CardContent className="flex flex-col gap-2">
            {offerItems.map((item) => {
              const inComparison = offers.some(
                (o) => o.id === `tracker-${item.id}`,
              );
              return (
                <label
                  key={item.id}
                  className="flex cursor-pointer items-center gap-2 text-sm text-slate-700"
                >
                  <input
                    type="checkbox"
                    checked={inComparison}
                    onChange={() => toggleTrackerItem(item.id)}
                    className="h-4 w-4 accent-indigo-600"
                  />
                  {item.title} — {item.company}
                  <Badge variant="success">Offer</Badge>
                </label>
              );
            })}
            <p className="text-xs text-slate-500">
              Tracker offers carry only the job title and company — fill in
              the salary and benefits below.
            </p>
          </CardContent>
        </Card>
      )}

      <Card>
        <CardHeader>
          <CardTitle>Add an offer manually</CardTitle>
        </CardHeader>
        <CardContent className="flex flex-col gap-3">
          {formErrors.length > 0 && (
            <div role="alert" className="text-sm text-rose-600">
              {formErrors.map((e) => (
                <p key={e}>{e}</p>
              ))}
            </div>
          )}
          <div className="grid gap-3 sm:grid-cols-2">
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="offer-company">Company *</Label>
              <Input id="offer-company" value={fields.company} onChange={set("company")} />
            </div>
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="offer-title">Job title *</Label>
              <Input id="offer-title" value={fields.title} onChange={set("title")} />
            </div>
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="offer-base">Base salary (per year) *</Label>
              <Input id="offer-base" inputMode="numeric" placeholder="150000" value={fields.baseSalary} onChange={set("baseSalary")} />
            </div>
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="offer-bonus">Annual bonus</Label>
              <Input id="offer-bonus" inputMode="numeric" placeholder="15000" value={fields.bonus} onChange={set("bonus")} />
            </div>
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="offer-signing">Signing bonus</Label>
              <Input id="offer-signing" inputMode="numeric" placeholder="10000" value={fields.signingBonus} onChange={set("signingBonus")} />
            </div>
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="offer-location">Location</Label>
              <Input id="offer-location" value={fields.location} onChange={set("location")} />
            </div>
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="offer-worktype">Work type</Label>
              <Select id="offer-worktype" value={fields.workType} onChange={set("workType")}>
                <option value="">Not stated</option>
                <option value="Remote">Remote</option>
                <option value="Hybrid">Hybrid</option>
                <option value="Onsite">Onsite</option>
              </Select>
            </div>
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="offer-visa">Visa support</Label>
              <Select id="offer-visa" value={fields.visaSupport} onChange={set("visaSupport")}>
                <option value="unknown">Unknown</option>
                <option value="yes">Yes</option>
                <option value="no">No</option>
              </Select>
            </div>
          </div>
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="offer-benefits">Benefits (comma-separated)</Label>
            <Input
              id="offer-benefits"
              placeholder="Health insurance, 401k match, 20 days PTO"
              value={fields.benefits}
              onChange={set("benefits")}
            />
          </div>
          <div>
            <Button onClick={addManualOffer}>Add offer</Button>
          </div>
        </CardContent>
      </Card>

      {offers.length > 0 && (
        <Card>
          <CardHeader>
            <CardTitle>Offers being compared ({offers.length})</CardTitle>
          </CardHeader>
          <CardContent className="flex flex-col gap-3">
            {offers.map((offer) => (
              <div
                key={offer.id}
                className="flex flex-col gap-2 rounded-lg border border-slate-200 p-3 sm:flex-row sm:items-end sm:gap-3"
              >
                <div className="min-w-0 flex-1">
                  <p className="text-sm font-semibold text-slate-900">
                    {offer.title} — {offer.company}
                  </p>
                  <p className="text-xs text-slate-500">
                    {offer.source === "tracker"
                      ? "From tracker — fill in the figures"
                      : "Entered manually"}
                  </p>
                </div>
                <div className="flex flex-wrap gap-3">
                  <label className="flex flex-col gap-1 text-xs text-slate-600">
                    Base salary
                    <Input
                      inputMode="numeric"
                      className="w-32"
                      aria-label={`Base salary for ${offer.company}`}
                      value={offer.baseSalary ?? ""}
                      onChange={(e) =>
                        updateOffer(offer.id, {
                          baseSalary: parseMoney(e.target.value),
                        })
                      }
                    />
                  </label>
                  <label className="flex flex-col gap-1 text-xs text-slate-600">
                    Bonus
                    <Input
                      inputMode="numeric"
                      className="w-28"
                      aria-label={`Bonus for ${offer.company}`}
                      value={offer.bonus ?? ""}
                      onChange={(e) =>
                        updateOffer(offer.id, {
                          bonus: parseMoney(e.target.value),
                        })
                      }
                    />
                  </label>
                  <label className="flex flex-col gap-1 text-xs text-slate-600">
                    Signing bonus
                    <Input
                      inputMode="numeric"
                      className="w-28"
                      aria-label={`Signing bonus for ${offer.company}`}
                      value={offer.signingBonus ?? ""}
                      onChange={(e) =>
                        updateOffer(offer.id, {
                          signingBonus: parseMoney(e.target.value),
                        })
                      }
                    />
                  </label>
                  <label className="flex flex-col gap-1 text-xs text-slate-600">
                    Location
                    <Input
                      className="w-32"
                      aria-label={`Location for ${offer.company}`}
                      value={offer.location}
                      onChange={(e) =>
                        updateOffer(offer.id, { location: e.target.value })
                      }
                    />
                  </label>
                  <label className="flex flex-col gap-1 text-xs text-slate-600">
                    Visa support
                    <Select
                      aria-label={`Visa support for ${offer.company}`}
                      value={offer.visaSupport}
                      onChange={(e) =>
                        updateOffer(offer.id, {
                          visaSupport: e.target.value as OfferInput["visaSupport"],
                        })
                      }
                    >
                      <option value="unknown">Unknown</option>
                      <option value="yes">Yes</option>
                      <option value="no">No</option>
                    </Select>
                  </label>
                  <label className="flex flex-col gap-1 text-xs text-slate-600">
                    Benefits
                    <Input
                      className="w-48"
                      aria-label={`Benefits for ${offer.company}`}
                      placeholder="Comma-separated"
                      value={offer.benefits.join(", ")}
                      onChange={(e) =>
                        updateOffer(offer.id, {
                          benefits: e.target.value
                            .split(",")
                            .map((b) => b.trim())
                            .filter(Boolean),
                        })
                      }
                    />
                  </label>
                </div>
                <Button
                  size="sm"
                  variant="outline"
                  aria-label={`Remove ${offer.company} offer`}
                  onClick={() => removeOffer(offer.id)}
                >
                  <X className="h-4 w-4" aria-hidden /> Remove
                </Button>
              </div>
            ))}
          </CardContent>
        </Card>
      )}

      {offers.length >= 2 ? (
        <OfferComparison offers={offers} />
      ) : (
        <p className="text-sm text-slate-500">
          Add at least two offers to see the side-by-side comparison.
        </p>
      )}
    </div>
  );
}

export default function OffersPage() {
  return (
    <Suspense fallback={<p className="text-sm text-slate-500">Loading offers…</p>}>
      <OffersPageInner />
    </Suspense>
  );
}
