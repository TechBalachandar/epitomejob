"use client";

import { useState } from "react";
import { ApiError, useDeleteTracker, useTracker, useUpdateTracker } from "@/lib/api";
import { toast } from "@/lib/toast-store";
import type { TrackerDto, TrackerStatus } from "@/lib/types";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Textarea } from "@/components/ui/textarea";
import { GripVertical, Trash2 } from "lucide-react";

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
  const [draggedId, setDraggedId] = useState<string | null>(null);
  const [dropTarget, setDropTarget] = useState<TrackerStatus | null>(null);

  async function move(card: TrackerDto, status: TrackerStatus) {
    if (status === card.status) return;
    try {
      await update.mutateAsync({ id: card.id, patch: { status } });
    } catch (err) {
      toast({ title: "Could not move", description: err instanceof ApiError ? err.message : "Try again.", variant: "error" });
    }
  }

  function handleDragStart(e: React.DragEvent, card: TrackerDto) {
    e.dataTransfer.setData("text/plain", card.id);
    e.dataTransfer.effectAllowed = "move";
    setDraggedId(card.id);
  }

  function handleDragEnd() {
    setDraggedId(null);
    setDropTarget(null);
  }

  function handleDrop(e: React.DragEvent, status: TrackerStatus) {
    e.preventDefault();
    const id = e.dataTransfer.getData("text/plain");
    setDropTarget(null);
    setDraggedId(null);
    const card = (cards ?? []).find((c) => c.id === id);
    if (card) void move(card, status);
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
          Your applications across the pipeline. Drag cards between columns
          to move them, edit their notes, or remove them.
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
          const isDropTarget = dropTarget === status;
          return (
            <div
              key={status}
              role="list"
              data-testid={`tracker-column-${status}`}
              aria-label={`${label} column, drop cards here to move them`}
              onDragOver={(e) => {
                e.preventDefault();
                e.dataTransfer.dropEffect = "move";
                setDropTarget(status);
              }}
              onDragLeave={(e) => {
                // Only clear when the pointer truly leaves the column, not
                // when it moves between children inside it.
                if (!e.currentTarget.contains(e.relatedTarget as Node | null)) {
                  setDropTarget((t) => (t === status ? null : t));
                }
              }}
              onDrop={(e) => handleDrop(e, status)}
              className={`flex flex-col gap-3 rounded-xl p-3 transition-colors ${
                isDropTarget ? "bg-indigo-50 ring-2 ring-indigo-500" : "bg-slate-100"
              }`}
            >
              <div className="flex items-center justify-between px-1">
                <h2 className="text-sm font-semibold text-slate-700">{label}</h2>
                <Badge variant="secondary">{columnCards.length}</Badge>
              </div>
              {columnCards.length === 0 && (
                <p className="px-1 text-xs text-slate-400">No applications here.</p>
              )}
              {columnCards.map((card) => {
                const isEditing = editing.has(card.id);
                const isDragging = draggedId === card.id;
                return (
                  <Card
                    key={card.id}
                    role="listitem"
                    data-testid={`tracker-card-${card.id}`}
                    draggable
                    aria-label={`${card.title} in ${label} column. Drag to move to another column.`}
                    onDragStart={(e) => handleDragStart(e, card)}
                    onDragEnd={handleDragEnd}
                    className={isDragging ? "cursor-grabbing opacity-50" : "cursor-grab"}
                  >
                    <CardHeader className="pb-2">
                      <div className="flex items-start gap-1">
                        <GripVertical
                          className="mt-0.5 h-4 w-4 shrink-0 text-slate-400"
                          aria-hidden
                        />
                        <div className="min-w-0">
                          <CardTitle className="text-sm leading-snug">{card.title}</CardTitle>
                          <p className="text-xs text-slate-500">{card.company}</p>
                        </div>
                      </div>
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
