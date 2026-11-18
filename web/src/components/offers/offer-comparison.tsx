// Feature 7 — Offer comparison table.
// Two or more offers side by side. The better value per row is
// highlighted ONLY where the numbers make it unambiguous (higher money
// figure, or visa support yes vs no/unknown). Location, work type and
// benefits never name a winner. No market data is shown: no market
// source is connected. For tracker-sourced offers the job posting's own
// listed salary range is shown, labelled as the listing — never as a
// market range.
"use client";

import { useJob } from "@/lib/api";
import { compareOffers, type OfferInput } from "@/lib/offers";
import { formatSalary } from "@/lib/jobs";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";

/** The listing's own salary range for a tracker-sourced offer — real
 * data from the job posting, labelled as such. A posting with no
 * listed salary says so instead of showing a fabricated $0k range. */
function ListedRange({ jobId }: { jobId: string }) {
  const { data: job } = useJob(jobId);
  if (!job) return null;
  const range = formatSalary(job.salaryMin, job.salaryMax);
  if (!range) {
    return <p className="text-xs text-slate-500">Listed range: Not listed</p>;
  }
  return (
    <p className="text-xs text-slate-500">
      Listed range: {range} {job.currency}
    </p>
  );
}

export function OfferComparison({ offers }: { offers: OfferInput[] }) {
  const rows = compareOffers(offers);
  const notes = rows.map((r) => r.note).filter(Boolean);

  return (
    <Card>
      <CardHeader>
        <CardTitle>Offer comparison</CardTitle>
        <p className="text-sm text-slate-500">
          Figures are the ones you entered. A highlighted cell is better
          only where the numbers decide it — higher pay, or visa support.
          No market salary data is shown: no market source is connected.
        </p>
      </CardHeader>
      <CardContent className="overflow-x-auto">
        <table className="w-full min-w-[560px] border-collapse text-sm">
          <thead>
            <tr>
              <th className="w-44 p-2 text-left font-semibold text-slate-700">
                <span className="sr-only">Field</span>
              </th>
              {offers.map((offer) => (
                <th
                  key={offer.id}
                  className="p-2 text-left align-top font-semibold text-slate-900"
                >
                  {offer.title}
                  <span className="block text-xs font-normal text-slate-500">
                    {offer.company}
                  </span>
                  {offer.jobId && <ListedRange jobId={offer.jobId} />}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {rows.map((row) => (
              <tr key={row.key} className="border-t border-slate-200">
                <th className="p-2 text-left font-medium text-slate-700">
                  {row.label}
                </th>
                {offers.map((offer, i) => {
                  const isBest = row.bestIds.includes(offer.id);
                  return (
                    <td
                      key={offer.id}
                      data-testid={`offer-cell-${row.key}-${offer.id}`}
                      className={`p-2 align-top ${
                        isBest ? "bg-emerald-50 font-semibold text-emerald-900" : "text-slate-700"
                      }`}
                    >
                      {row.values[i]}
                      {isBest && (
                        <Badge variant="success" className="ml-2">
                          Best
                        </Badge>
                      )}
                    </td>
                  );
                })}
              </tr>
            ))}
          </tbody>
        </table>
        {notes.length > 0 && (
          <p className="mt-3 text-xs text-slate-500">{notes.join(" ")}</p>
        )}
        <p className="mt-1 text-xs text-slate-400">
          Total cash = base salary + bonus where stated. Equity, stock
          and benefits are not converted to a cash figure — vesting and
          value vary too much to compare without inventing numbers.
        </p>
      </CardContent>
    </Card>
  );
}
