// M13 notifications helpers (contract §2e). One place only: the relative
// time formatter and the per-type icon map live here, next to each other.
import {
  Briefcase,
  CalendarCheck,
  Clock,
  Inbox,
  type LucideIcon,
} from "lucide-react";
import type { Notification } from "./types";

/** Icon per notification type (contract §2e). */
export const NOTIFICATION_TYPE_ICONS: Record<Notification["type"], LucideIcon> = {
  APPROVAL_PROPOSED: Inbox,
  INTERVIEW_CREATED: CalendarCheck,
  TRACKER_CHANGED: Briefcase,
  CLOCK_WARNING: Clock,
};

const MINUTE_MS = 60 * 1000;
const HOUR_MS = 60 * MINUTE_MS;
const DAY_MS = 24 * HOUR_MS;

/**
 * Relative time for a notification's createdAt ISO instant.
 * <60s → "just now", <60m → "Nm ago", <24h → "Nh ago", <7d → "Nd ago",
 * else a short date ("Mar 4, 2026", UTC so it never shifts by timezone).
 * A future timestamp reads as "just now"; an unparseable one as
 * "Unknown date".
 */
export function formatRelativeTime(iso: string, now: Date = new Date()): string {
  const at = new Date(iso);
  if (Number.isNaN(at.getTime())) return "Unknown date";
  const diffMs = now.getTime() - at.getTime();
  if (diffMs < MINUTE_MS) return "just now";
  if (diffMs < HOUR_MS) return `${Math.floor(diffMs / MINUTE_MS)}m ago`;
  if (diffMs < DAY_MS) return `${Math.floor(diffMs / HOUR_MS)}h ago`;
  if (diffMs < 7 * DAY_MS) return `${Math.floor(diffMs / DAY_MS)}d ago`;
  return at.toLocaleDateString("en-US", {
    month: "short",
    day: "numeric",
    year: "numeric",
    timeZone: "UTC",
  });
}
