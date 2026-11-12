"use client";

import { useState } from "react";
import { ApiError, useDeleteTracker, useTracker, useUpdateTracker } from "@/lib/api";
import { toast } from "@/lib/toast-store";
import type { TrackerDto, TrackerStatus } from "@/lib/types";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Select } from "@/components/ui/select";
import { Textarea } from "@/components/ui/textarea";
import { Trash2 } from "lucide-react";

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

function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString(undefined, {
    month: "short",
    day: "numeric",
    year: "numeric",
  });
}

export default function TrackerPage() {
  const { data: cards, isLoading, isError } = useTracker();
  const update = useUpdateTracker();
  const remove = useDeleteTracker();
  const [notesDraft, setNotesDraft] = useState<Record<string, string>>({});
  const [editing, setEditing] = useState<Set<string>>(new Set());

  async function move(card: TrackerDto, status: TrackerStatus) {
    if (status === card.status) return;
    try {
      await update.mutateAsync({ id: card.id, patch: { status } });
    } catch (err) {
      toast({ title: "Could not move", description: err instanceof ApiError ? err.message : "Try again.", variant: "error" });
    }
  }

  async function saveNotes(card: TrackerDto) {
    try {
      await update.mutateAsync({ id: card.id, patch: { notes: notesDraft[card.id] ?? "" } });
      setEditing((s) => {
        const next = new Set(s);
        next.delete(card.id);
        return next;
      });
      toast({ title: "Notes saved", description: card.title, variant: "success" });
    } catch (err) {
      toast({ title: "Could not save notes", description: err instanceof ApiError ? err.message : "Try again.", variant: "error" });
    }
  }

  async function deleteCard(card: TrackerDto) {
    try {
      await remove.mutateAsync(card.id);
      toast({ title: "Removed", description: `${card.title} removed from your tracker.`, variant: "success" });
    } catch (err) {
      toast({ title: "Could not remove", description: err instanceof ApiError ? err.message : "Try again.", variant: "error" });
    }
  }

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-bold text-slate-900">Tracker</h1>
        <p className="mt-1 text-sm text-slate-500">
          Your applications across the pipeline. Move a card with its status
          menu, edit its notes, or remove it.
        </p>
      </div>

      {isLoading && <p className="text-sm text-slate-500">Loading tracker…</p>}
      {isError && (
        <p role="alert" className="text-sm text-rose-600">
          Could not load your tracker. Make sure you are signed in and the backend is running.
        </p>
      )}

      <div className="grid gap-4 md:grid-cols-3 xl:grid-cols-5">
        {COLUMNS.map(({ status, label }) => {
          const columnCards = (cards ?? []).filter((c) => c.status === status);
          return (
            <div key={status} className="flex flex-col gap-3 rounded-xl bg-slate-100 p-3">
              <div className="flex items-center justify-between px-1">
                <h2 className="text-sm font-semibold text-slate-700">{label}</h2>
                <Badge variant="secondary">{columnCards.length}</Badge>
              </div>
              {columnCards.length === 0 && (
                <p className="px-1 text-xs text-slate-400">No applications here.</p>
              )}
              {columnCards.map((card) => {
                const isEditing = editing.has(card.id);
                return (
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
                        Saved {formatDate(card.createdAt)}
                      </p>
                      {isEditing ? (
                        <>
                          <Textarea
                            rows={3}
                            value={notesDraft[card.id] ?? card.notes ?? ""}
                            onChange={(e) =>
                              setNotesDraft((d) => ({ ...d, [card.id]: e.target.value }))
                            }
                            aria-label={`Notes for ${card.title}`}
                            placeholder="Add a note…"
                          />
                          <div className="flex gap-2">
                            <Button size="sm" disabled={update.isPending} onClick={() => void saveNotes(card)}>
                              Save notes
                            </Button>
                            <Button
                              size="sm"
                              variant="outline"
                              onClick={() =>
                                setEditing((s) => {
                                  const next = new Set(s);
                                  next.delete(card.id);
                                  return next;
                                })
                              }
                            >
                              Cancel
                            </Button>
                          </div>
                        </>
                      ) : (
                        <>
                          {card.notes && <p className="text-xs text-slate-600">{card.notes}</p>}
                          <Button
                            size="sm"
                            variant="outline"
                            onClick={() => {
                              setNotesDraft((d) => ({ ...d, [card.id]: card.notes ?? "" }));
                              setEditing((s) => new Set(s).add(card.id));
                            }}
                          >
                            {card.notes ? "Edit notes" : "Add notes"}
                          </Button>
                        </>
                      )}
                      <label className="sr-only" htmlFor={`move-${card.id}`}>
                        Move {card.title} to
                      </label>
                      <Select
                        id={`move-${card.id}`}
                        value={card.status}
                        disabled={update.isPending}
                        onChange={(e) => void move(card, e.target.value as TrackerStatus)}
                        aria-label={`Move ${card.title} to another column`}
                      >
                        {COLUMNS.map((col) => (
                          <option key={col.status} value={col.status}>
                            Move to {col.label}
                          </option>
                        ))}
                      </Select>
                      <Button
                        size="sm"
                        variant="outline"
                        disabled={remove.isPending}
                        onClick={() => void deleteCard(card)}
                      >
                        <Trash2 className="h-4 w-4" aria-hidden /> Delete
                      </Button>
                    </CardContent>
                  </Card>
                );
              })}
            </div>
          );
        })}
      </div>
    </div>
  );
}
