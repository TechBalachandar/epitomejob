"use client";

// M13 notifications inbox (contract §2e). Rows link to notification.link.
// Clicking an UNREAD stored row marks it read first, then navigates;
// derived (clock-warning) rows only navigate — they are never marked read.
import { useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import {
  ApiError,
  useMarkAllNotificationsRead,
  useMarkNotificationRead,
  useNotifications,
  useUnreadCount,
} from "@/lib/api";
import { formatRelativeTime, NOTIFICATION_TYPE_ICONS } from "@/lib/notifications";
import type { Notification } from "@/lib/types";
import { toast } from "@/lib/toast-store";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { cn } from "@/lib/utils";
import { Bell, ChevronLeft, ChevronRight } from "lucide-react";

const PAGE_SIZE = 20;

export default function NotificationsPage() {
  const router = useRouter();
  const [page, setPage] = useState(0);
  const { data, isLoading, isError } = useNotifications(page, PAGE_SIZE);
  const { data: unread } = useUnreadCount();
  const markRead = useMarkNotificationRead();
  const markAllRead = useMarkAllNotificationsRead();

  const notifications = data?.content ?? [];
  const unreadCount = unread?.unreadCount ?? 0;
  const totalPages = data?.totalPages ?? 0;

  async function handleRowClick(
    e: React.MouseEvent,
    notification: Notification,
  ) {
    // Derived rows are never marked read; unread stored rows are marked
    // read first, then navigation follows. Read rows navigate directly.
    if (notification.derived || notification.isRead) return;
    e.preventDefault();
    try {
      await markRead.mutateAsync(notification.id);
    } catch (err) {
      toast({
        title: "Could not mark as read",
        description: err instanceof ApiError ? err.message : "Try again.",
        variant: "error",
      });
    }
    router.push(notification.link);
  }

  async function handleMarkAllRead() {
    try {
      const res = await markAllRead.mutateAsync();
      toast({
        title: "All caught up",
        description:
          res.readCount === 1
            ? "1 notification marked as read."
            : `${res.readCount} notifications marked as read.`,
        variant: "success",
      });
    } catch (err) {
      toast({
        title: "Could not mark all as read",
        description: err instanceof ApiError ? err.message : "Try again.",
        variant: "error",
      });
    }
  }

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="text-2xl font-bold text-slate-900">Notifications</h1>
          <p className="mt-1 text-sm text-slate-500">
            Approvals, interviews, tracker updates and H-1B clock reminders —
            newest unread first.
          </p>
        </div>
        <Button
          variant="outline"
          onClick={() => void handleMarkAllRead()}
          disabled={unreadCount === 0 || markAllRead.isPending}
        >
          Mark all read
        </Button>
      </div>

      {isLoading && <p className="text-sm text-slate-500">Loading notifications…</p>}
      {isError && (
        <p role="alert" className="text-sm text-rose-600">
          Could not load notifications. Make sure you are signed in.
        </p>
      )}

      {!isLoading && !isError && notifications.length === 0 && (
        <Card>
          <CardContent className="flex flex-col items-center gap-3 py-10 text-center">
            <Bell className="h-10 w-10 text-slate-300" aria-hidden />
            <p className="text-sm font-medium text-slate-900">
              {"You're all caught up — no notifications yet."}
            </p>
            <p className="max-w-sm text-sm text-slate-500">
              When something needs your attention — an approval, an interview
              or a tracker update — it will appear here first.
            </p>
          </CardContent>
        </Card>
      )}

      <div className="flex flex-col gap-2">
        {notifications.map((n) => {
          const Icon = NOTIFICATION_TYPE_ICONS[n.type];
          return (
            <Link
              key={n.id}
              href={n.link}
              onClick={(e) => void handleRowClick(e, n)}
              className={cn(
                "flex items-start gap-3 rounded-xl border bg-white p-4 shadow-sm transition-colors hover:bg-slate-50",
                n.isRead ? "border-slate-200" : "border-indigo-200 bg-indigo-50/40",
              )}
            >
              <span
                aria-hidden
                className={cn(
                  "mt-0.5 flex h-9 w-9 shrink-0 items-center justify-center rounded-full",
                  n.isRead ? "bg-slate-100 text-slate-500" : "bg-indigo-100 text-indigo-700",
                )}
              >
                <Icon className="h-4 w-4" />
              </span>
              <span className="min-w-0 flex-1">
                <span className="flex flex-wrap items-center gap-2">
                  <span
                    className={cn(
                      "truncate text-sm text-slate-900",
                      n.isRead ? "font-medium" : "font-bold",
                    )}
                  >
                    {n.title}
                  </span>
                  {!n.isRead && (
                    <span
                      aria-label="Unread"
                      className="h-2 w-2 shrink-0 rounded-full bg-indigo-600"
                    />
                  )}
                  {n.derived && (
                    <Badge variant="secondary">Reminder</Badge>
                  )}
                </span>
                <span className="mt-0.5 block text-sm text-slate-500">{n.body}</span>
                <span className="mt-1 block text-xs text-slate-400">
                  {formatRelativeTime(n.createdAt)}
                </span>
              </span>
            </Link>
          );
        })}
      </div>

      {totalPages > 1 && (
        <div className="flex items-center justify-between gap-3">
          <Button
            variant="outline"
            size="sm"
            disabled={page === 0}
            onClick={() => setPage((p) => Math.max(0, p - 1))}
          >
            <ChevronLeft className="h-4 w-4" aria-hidden /> Previous
          </Button>
          <p className="text-sm text-slate-500">
            Page {page + 1} of {totalPages}
          </p>
          <Button
            variant="outline"
            size="sm"
            disabled={page >= totalPages - 1}
            onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
          >
            Next <ChevronRight className="h-4 w-4" aria-hidden />
          </Button>
        </div>
      )}
    </div>
  );
}
