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

function columnLabel(status: TrackerStatus): string {
  return COLUMNS.find((c) => c.status === status)?.label ?? status;
}

function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString(undefined, {
    month: "short",
    day: "numeric",
    year: "numeric",
  });
}

type DragState = {
  card: TrackerDto;
  pointerId: number;
  x: number;
  y: number;
};

export default function TrackerPage() {
  const { data: cards, isLoading, isError } = useTracker();
  const update = useUpdateTracker();
  const remove = useDeleteTracker();
  const [notesDraft, setNotesDraft] = useState<Record<string, string>>({});
  const [editing, setEditing] = useState<Set<string>>(new Set());
  // Pointer Events drag (mouse AND touch): the drag starts only from a
  // card's grip handle, so touching the card body still scrolls the page.
  // No HTML5 drag-and-drop here — mobile browsers never fire it from a
  // finger, and there is no keyboard drag in this slice.
  const [drag, setDrag] = useState<DragState | null>(null);
  const [dropTarget, setDropTarget] = useState<TrackerStatus | null>(null);

  async function move(card: TrackerDto, status: TrackerStatus) {
    if (status === card.status) return;
    try {
      await update.mutateAsync({ id: card.id, patch: { status } });
    } catch (err) {
      toast({ title: "Could not move", description: err instanceof ApiError ? err.message : "Try again.", variant: "error" });
    }
  }

  function handleGripPointerDown(e: React.PointerEvent, card: TrackerDto) {
    // touch-action: none on the grip (see className) stops the browser
    // taking the gesture over for scrolling/zooming; preventDefault stops
    // text selection and the native drag. Works for mouse and touch alike —
    // the handlers never branch on pointerType.
    e.preventDefault();
    e.currentTarget.setPointerCapture(e.pointerId);
    setDrag({ card, pointerId: e.pointerId, x: e.clientX, y: e.clientY });
  }

  function handleGripPointerMove(e: React.PointerEvent) {
    if (!drag || e.pointerId !== drag.pointerId) return;
    const el = document.elementFromPoint(e.clientX, e.clientY)?.closest("[data-column]");
    const status = el?.getAttribute("data-column") as TrackerStatus | null;
    setDrag({ ...drag, x: e.clientX, y: e.clientY });
    setDropTarget(status ?? null);
  }

  function endDrag(e: React.PointerEvent) {
    if (!drag || e.pointerId !== drag.pointerId) return;
    // Recompute the target from the pointer position instead of trusting
    // state: the last pointermove may not have flushed before pointerup.
    const el = document.elementFromPoint(e.clientX, e.clientY)?.closest("[data-column]");
    const target = el?.getAttribute("data-column") as TrackerStatus | null;
    const card = drag.card;
    setDrag(null);
    setDropTarget(null);
    if (target && target !== card.status) void move(card, target);
  }

  function cancelDrag(e: React.PointerEvent) {
    if (!drag || e.pointerId !== drag.pointerId) return;
    setDrag(null);
    setDropTarget(null);
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
          Your applications across the pipeline. Drag cards by their handle
          between columns to move them, edit their notes, or remove them.
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
              data-column={status}
              data-testid={`tracker-column-${status}`}
              aria-label={`${label} column, drop cards here to move them`}
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
                const isDragging = drag?.card.id === card.id;
                return (
                  <Card
                    key={card.id}
                    role="listitem"
                    data-testid={`tracker-card-${card.id}`}
                    aria-label={`${card.title} in ${label} column. Drag using the handle to move to another column.`}
                    className={isDragging ? "opacity-50" : undefined}
                  >
                    <CardHeader className="pb-2">
                      <div className="flex items-start gap-1">
                        <span
                          data-testid={`tracker-grip-${card.id}`}
                          title="Drag to move"
                          onPointerDown={(e) => handleGripPointerDown(e, card)}
                          onPointerMove={handleGripPointerMove}
                          onPointerUp={endDrag}
                          onPointerCancel={cancelDrag}
                          onLostPointerCapture={cancelDrag}
                          className="mt-0.5 inline-flex shrink-0 cursor-grab touch-none rounded p-0.5 text-slate-400 hover:bg-slate-100 hover:text-slate-600 active:cursor-grabbing"
                        >
                          <GripVertical className="h-4 w-4" aria-hidden />
                        </span>
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

      {drag && (
        <div
          aria-hidden
          className="pointer-events-none fixed z-50 w-64 rounded-lg border border-indigo-200 bg-white p-3 shadow-xl"
          style={{
            left: drag.x,
            top: drag.y,
            transform: "translate(-50%, calc(-100% - 12px))",
          }}
        >
          <p className="text-sm font-semibold leading-snug text-slate-900">{drag.card.title}</p>
          <p className="text-xs text-slate-500">{drag.card.company}</p>
          {dropTarget && dropTarget !== drag.card.status && (
            <p className="mt-1 text-xs font-medium text-indigo-700">
              Move to {columnLabel(dropTarget)}
            </p>
          )}
        </div>
      )}
    </div>
  );
}
