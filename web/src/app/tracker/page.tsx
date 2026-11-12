"use client";

import { useState } from "react";
import { MOCK_TRACKER } from "@/lib/mock-data";
import type { TrackerCard, TrackerStatus } from "@/lib/types";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Select } from "@/components/ui/select";

const COLUMNS: { status: TrackerStatus; label: string }[] = [
  { status: "SAVED", label: "Saved" },
  { status: "APPLIED", label: "Applied" },
  { status: "INTERVIEW", label: "Interview" },
  { status: "OFFER", label: "Offer" },
  { status: "REJECTED", label: "Rejected" },
];

const STATUS_BADGE: Record<TrackerStatus, "secondary" | "default" | "success" | "destructive"> = {
  SAVED: "secondary",
  APPLIED: "default",
  INTERVIEW: "default",
  OFFER: "success",
  REJECTED: "destructive",
};

export default function TrackerPage() {
  const [cards, setCards] = useState<TrackerCard[]>(MOCK_TRACKER);

  function move(id: string, status: TrackerStatus) {
    setCards((cs) => cs.map((c) => (c.id === id ? { ...c, status } : c)));
  }

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-bold text-slate-900">Tracker</h1>
        <p className="mt-1 text-sm text-slate-500">
          Your applications across the pipeline. Move a card with its status
          menu — drag-and-drop arrives with the W2 tracker upgrade.
        </p>
      </div>

      <div className="grid gap-4 md:grid-cols-3 xl:grid-cols-5">
        {COLUMNS.map(({ status, label }) => {
          const columnCards = cards.filter((c) => c.status === status);
          return (
            <div key={status} className="flex flex-col gap-3 rounded-xl bg-slate-100 p-3">
              <div className="flex items-center justify-between px-1">
                <h2 className="text-sm font-semibold text-slate-700">{label}</h2>
                <Badge variant="secondary">{columnCards.length}</Badge>
              </div>
              {columnCards.length === 0 && (
                <p className="px-1 text-xs text-slate-400">No applications here.</p>
              )}
              {columnCards.map((card) => (
                <Card key={card.id}>
                  <CardHeader className="pb-2">
                    <CardTitle className="text-sm leading-snug">{card.title}</CardTitle>
                    <p className="text-xs text-slate-500">{card.company}</p>
                  </CardHeader>
                  <CardContent className="flex flex-col gap-2 pt-0">
                    <Badge variant={STATUS_BADGE[card.status]} className="w-fit">
                      {label}
                    </Badge>
                    <p className="text-xs text-slate-500">
                      Applied {card.appliedAt}
                    </p>
                    <p className="text-xs text-slate-600">{card.note}</p>
                    <label className="sr-only" htmlFor={`move-${card.id}`}>
                      Move {card.title} to
                    </label>
                    <Select
                      id={`move-${card.id}`}
                      value={card.status}
                      onChange={(e) => move(card.id, e.target.value as TrackerStatus)}
                      aria-label={`Move ${card.title} to another column`}
                    >
                      {COLUMNS.map((col) => (
                        <option key={col.status} value={col.status}>
                          Move to {col.label}
                        </option>
                      ))}
                    </Select>
                  </CardContent>
                </Card>
              ))}
            </div>
          );
        })}
      </div>
    </div>
  );
}
