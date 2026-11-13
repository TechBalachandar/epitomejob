// M13 notifications (contract §2f). fetch is mocked at the BFF boundary
// (/api/*) — the real backend is never involved.
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { ReactNode } from "react";
import {
  useMarkAllNotificationsRead,
  useMarkNotificationRead,
  useNotifications,
  useUnreadCount,
} from "@/lib/api";
import { formatRelativeTime, NOTIFICATION_TYPE_ICONS } from "@/lib/notifications";
import { useAuthStore } from "@/lib/auth-store";

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

function mockFetch(impl: (url: string, init?: RequestInit) => Response | Promise<Response>) {
  return vi.fn((input: string | URL | Request, init?: RequestInit) => {
    const url = typeof input === "string" ? input : input.toString();
    return Promise.resolve(impl(url, init));
  });
}

function wrapper({ children }: { children: ReactNode }) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}

beforeEach(() => {
  useAuthStore.getState().logout();
  useAuthStore.getState().login({
    userId: "user-1",
    email: "dev@example.com",
    role: "USER",
    accessToken: "a.b.c",
  });
});

afterEach(() => {
  vi.unstubAllGlobals();
  useAuthStore.getState().logout();
});

// "now" is fixed at 2026-11-13T12:00:00Z so every case is exact.
const NOW = new Date("2026-11-13T12:00:00.000Z");

describe("formatRelativeTime", () => {
  it("just now under 60s, including future timestamps", () => {
    expect(formatRelativeTime("2026-11-13T11:59:30.000Z", NOW)).toBe("just now");
    expect(formatRelativeTime("2026-11-13T12:00:00.000Z", NOW)).toBe("just now");
    expect(formatRelativeTime("2026-11-13T12:00:30.000Z", NOW)).toBe("just now");
  });

  it("minutes under 60m", () => {
    expect(formatRelativeTime("2026-11-13T11:59:00.000Z", NOW)).toBe("1m ago");
    expect(formatRelativeTime("2026-11-13T11:15:00.000Z", NOW)).toBe("45m ago");
  });

  it("hours under 24h", () => {
    expect(formatRelativeTime("2026-11-13T11:00:00.000Z", NOW)).toBe("1h ago");
    expect(formatRelativeTime("2026-11-12T13:00:00.000Z", NOW)).toBe("23h ago");
  });

  it("days under 7d", () => {
    expect(formatRelativeTime("2026-11-12T12:00:00.000Z", NOW)).toBe("1d ago");
    expect(formatRelativeTime("2026-11-07T12:00:00.000Z", NOW)).toBe("6d ago");
  });

  it("short date at 7d and older (UTC, never timezone-shifted)", () => {
    expect(formatRelativeTime("2026-11-06T12:00:00.000Z", NOW)).toBe("Nov 6, 2026");
    expect(formatRelativeTime("2026-03-04T09:00:00.000Z", NOW)).toBe("Mar 4, 2026");
  });

  it("unparseable input", () => {
    expect(formatRelativeTime("not-a-date", NOW)).toBe("Unknown date");
    expect(formatRelativeTime("", NOW)).toBe("Unknown date");
  });
});

describe("notification type icons", () => {
  it("maps every type to an icon", () => {
    expect(Object.keys(NOTIFICATION_TYPE_ICONS).sort()).toEqual([
      "APPROVAL_PROPOSED",
      "CLOCK_WARNING",
      "INTERVIEW_CREATED",
      "TRACKER_CHANGED",
    ]);
    for (const icon of Object.values(NOTIFICATION_TYPE_ICONS)) {
      expect(icon).toBeDefined();
    }
  });
});

const PAGE = {
  content: [
    {
      id: "notif-1",
      type: "APPROVAL_PROPOSED",
      title: "Approval proposed: Apply for a job",
      body: "Review it in your approvals inbox to approve or reject it.",
      link: "/approvals",
      isRead: false,
      derived: false,
      createdAt: "2026-11-13T10:00:00.000Z",
    },
    {
      id: "clock-14",
      type: "CLOCK_WARNING",
      title: "H-1B clock: 14 days left",
      body: "Your 60-day grace period ends on 2026-11-27.",
      link: "/profile",
      isRead: false,
      derived: true,
      createdAt: "2026-11-13T11:00:00.000Z",
    },
  ],
  page: 0,
  size: 20,
  totalElements: 2,
  totalPages: 1,
};

describe("useNotifications / useUnreadCount", () => {
  it("GETs the page with page & size", async () => {
    const fetchMock = mockFetch((url) => {
      if (url.startsWith("/api/notifications/me")) {
        expect(url).toBe("/api/notifications/me?page=0&size=20");
        return jsonResponse(PAGE);
      }
      return jsonResponse({ message: "not found" }, 404);
    });
    vi.stubGlobal("fetch", fetchMock);

    const { result } = renderHook(() => useNotifications(0, 20), { wrapper });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data?.content).toHaveLength(2);
    expect(result.current.data?.content[0].type).toBe("APPROVAL_PROPOSED");
    expect(result.current.data?.content[1].derived).toBe(true);
    expect(result.current.data?.totalElements).toBe(2);
  });

  it("GETs the unread count", async () => {
    const fetchMock = mockFetch((url) => {
      if (url === "/api/notifications/unread-count") {
        return jsonResponse({ unreadCount: 3 });
      }
      return jsonResponse({ message: "not found" }, 404);
    });
    vi.stubGlobal("fetch", fetchMock);

    const { result } = renderHook(() => useUnreadCount(), { wrapper });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data?.unreadCount).toBe(3);
  });
});

describe("useMarkNotificationRead / useMarkAllNotificationsRead", () => {
  it("POSTs /notifications/:id/read and returns the updated row", async () => {
    const fetchMock = mockFetch((url, init) => {
      expect(url).toBe("/api/notifications/notif-1/read");
      expect(init?.method).toBe("POST");
      return jsonResponse({ ...PAGE.content[0], isRead: true });
    });
    vi.stubGlobal("fetch", fetchMock);

    const { result } = renderHook(() => useMarkNotificationRead(), { wrapper });
    const updated = await result.current.mutateAsync("notif-1");
    expect(updated.isRead).toBe(true);
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it("POSTs /notifications/read-all and returns the read count", async () => {
    const fetchMock = mockFetch((url, init) => {
      expect(url).toBe("/api/notifications/read-all");
      expect(init?.method).toBe("POST");
      return jsonResponse({ readCount: 2 });
    });
    vi.stubGlobal("fetch", fetchMock);

    const { result } = renderHook(() => useMarkAllNotificationsRead(), { wrapper });
    const res = await result.current.mutateAsync();
    expect(res.readCount).toBe(2);
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });
});
