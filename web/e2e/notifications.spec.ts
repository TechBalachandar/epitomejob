import { test, expect, type Page } from "@playwright/test";

// M13 notifications (contract §2f): every BFF response is stubbed with
// page.route — the app talks to the stubs exactly as it would to the real
// backend through the BFF proxy, and no real backend is involved.
const SESSION = {
  accessToken: "e2e-access-token",
  expiresInMs: 900_000,
  userId: "user-1",
  email: "dev@example.com",
  role: "USER",
};

const isoHoursAgo = (hours: number) =>
  new Date(Date.now() - hours * 3_600_000).toISOString();

interface StubNotification {
  id: string;
  type: string;
  title: string;
  body: string;
  link: string;
  isRead: boolean;
  derived: boolean;
  createdAt: string;
}

function baseItems(linkOverride?: string): StubNotification[] {
  return [
    {
      id: "notif-1",
      type: "APPROVAL_PROPOSED",
      title: "Approval proposed: Apply for a job",
      body: "Review it in your approvals inbox to approve or reject it.",
      link: linkOverride ?? "/approvals",
      isRead: false,
      derived: false,
      createdAt: isoHoursAgo(2),
    },
    {
      id: "notif-2",
      type: "TRACKER_CHANGED",
      title: "Application updated",
      body: "Agentic AI Engineer moved to Interview.",
      link: "/tracker",
      isRead: false,
      derived: false,
      createdAt: isoHoursAgo(5),
    },
    {
      id: "clock-14",
      type: "CLOCK_WARNING",
      title: "H-1B clock: 14 days left",
      body: "Your 60-day grace period ends on 2026-11-27.",
      link: "/profile",
      isRead: false,
      derived: true,
      createdAt: isoHoursAgo(1),
    },
  ];
}

const PROFILE = {
  id: "profile-1",
  userId: "user-1",
  headline: "Senior Software Engineer — Java, Spring Boot, Agentic AI",
  summary: "Principal Mobile Architect turned AI engineer.",
  location: "Reston, VA",
  workType: "HYBRID",
  salaryMin: 150000,
  salaryMax: 190000,
  currency: "USD",
  visibility: "PUBLIC",
  skills: ["Java", "LangGraph", "Spring Boot"],
  h1bClockStartDate: null,
  completeness: 80,
  createdAt: "2026-10-13T00:00:00.000Z",
  updatedAt: "2026-11-10T00:00:00.000Z",
};

/** Stateful notifications stubs: read / read-all flip rows, the unread
 * count follows (derived clock warnings are never marked read). */
async function stubNotifications(
  page: Page,
  items: StubNotification[],
  unreadStart?: number,
) {
  let unread =
    unreadStart ?? items.filter((n) => !n.isRead).length;
  await page.route("**/api/notifications/me**", (route) =>
    route.fulfill({
      status: 200,
      json: {
        content: items,
        page: 0,
        size: 20,
        totalElements: items.length,
        totalPages: 1,
      },
    }),
  );
  await page.route("**/api/notifications/unread-count", (route) =>
    route.fulfill({ status: 200, json: { unreadCount: unread } }),
  );
  await page.route("**/api/notifications/*/read", async (route) => {
    const id = route.request().url().split("/").at(-2) ?? "";
    const item = items.find((n) => n.id === id);
    if (!item || item.derived) {
      return route.fulfill({ status: 404, json: { message: "Not found" } });
    }
    if (!item.isRead) {
      item.isRead = true;
      unread -= 1;
    }
    return route.fulfill({ status: 200, json: item });
  });
  await page.route("**/api/notifications/read-all", (route) => {
    let readCount = 0;
    for (const item of items) {
      if (!item.derived && !item.isRead) {
        item.isRead = true;
        readCount += 1;
      }
    }
    unread = items.filter((n) => !n.isRead).length;
    return route.fulfill({ status: 200, json: { readCount } });
  });
}

async function stubDashboardData(page: Page) {
  await page.route("**/api/auth/login", (route) =>
    route.fulfill({ status: 200, json: SESSION }),
  );
  await page.route("**/api/auth/refresh", (route) =>
    route.fulfill({ status: 401, json: { message: "No refresh token" } }),
  );
  await page.route("**/api/profiles/me", (route) =>
    route.fulfill({ status: 200, json: PROFILE }),
  );
  await page.route("**/api/resumes/me", (route) =>
    route.fulfill({ status: 200, json: [] }),
  );
  await page.route("**/api/matches/me", (route) =>
    route.fulfill({ status: 200, json: [] }),
  );
  await page.route("**/api/tracker", (route) =>
    route.fulfill({ status: 200, json: [] }),
  );
  await page.route("**/api/approvals/pending", (route) =>
    route.fulfill({ status: 200, json: [] }),
  );
  await page.route("**/api/interviews/sessions", (route) =>
    route.fulfill({ status: 200, json: [] }),
  );
}

async function login(page: Page, items: StubNotification[], unreadStart?: number) {
  await stubDashboardData(page);
  await stubNotifications(page, items, unreadStart);
  await page.goto("/login");
  await page.getByLabel("Email").fill("dev@example.com");
  await page.getByLabel("Password").fill("DevPass123!");
  await page.getByRole("button", { name: "Sign in" }).click();
  await expect(
    page.getByRole("heading", { name: /Good (morning|afternoon|evening)/ }),
  ).toBeVisible({ timeout: 15_000 });
}

async function gotoNotifications(page: Page) {
  await page.getByRole("link", { name: /^Notifications/ }).click();
  await expect(
    page.getByRole("heading", { name: "Notifications", exact: true }),
  ).toBeVisible({ timeout: 15_000 });
}

test.describe("M13 notifications", () => {
  test("bell badge shows the mocked count, capped at 9+", async ({ page }) => {
    await login(page, baseItems(), 12);
    const bell = page.getByRole("link", { name: "Notifications, 12 unread" });
    await expect(bell).toBeVisible();
    await expect(bell).toContainText("9+");
  });

  test("page lists items with icons, relative time and Reminder badge", async ({
    page,
  }) => {
    await login(page, baseItems());
    await gotoNotifications(page);

    await expect(
      page.getByText("Approval proposed: Apply for a job"),
    ).toBeVisible();
    await expect(page.getByText("Application updated")).toBeVisible();
    await expect(page.getByText("H-1B clock: 14 days left")).toBeVisible();
    // Relative times from the fixture: 2h / 5h / 1h ago.
    await expect(page.getByText("2h ago")).toBeVisible();
    await expect(page.getByText("5h ago")).toBeVisible();
    // Derived clock warning carries the Reminder badge and stays unread.
    await expect(page.getByText("Reminder", { exact: true })).toBeVisible();
    // All three rows start unread.
    await expect(page.getByLabel("Unread", { exact: true })).toHaveCount(3);
    // Rows link to their targets.
    await expect(
      page.getByRole("link", { name: /Application updated/ }),
    ).toHaveAttribute("href", "/tracker");
  });

  test("clicking an unread row marks it read (badge drops) then navigates", async ({
    page,
  }) => {
    // Self-link keeps the test on the page so the re-read list is visible.
    await login(page, baseItems("/notifications"));
    await gotoNotifications(page);

    const readRequest = page.waitForRequest(
      (req) =>
        req.url().includes("/api/notifications/notif-1/read") &&
        req.method() === "POST",
    );
    await page
      .getByRole("link", { name: /Approval proposed: Apply for a job/ })
      .click();
    await readRequest;
    // Unread count drops 3 → 2 (bell + list refetch from the stub).
    await expect(
      page.getByRole("link", { name: "Notifications, 2 unread" }),
    ).toBeVisible();
    await expect(page.getByLabel("Unread", { exact: true })).toHaveCount(2);
    // Still on the notifications page (the row's stubbed link is self).
    await expect(
      page.getByRole("heading", { name: "Notifications", exact: true }),
    ).toBeVisible();
  });

  test("mark-all-read marks stored rows only; derived row stays unread", async ({
    page,
  }) => {
    await login(page, baseItems());
    await gotoNotifications(page);

    const readAllRequest = page.waitForRequest(
      (req) =>
        req.url().includes("/api/notifications/read-all") &&
        req.method() === "POST",
    );
    await page.getByRole("button", { name: "Mark all read" }).click();
    await readAllRequest;
    await expect(page.getByText("All caught up")).toBeVisible();
    // Only the derived clock warning is still unread.
    await expect(page.getByLabel("Unread", { exact: true })).toHaveCount(1);
    await expect(
      page.getByRole("link", { name: "Notifications, 1 unread" }),
    ).toBeVisible();
  });

  test("empty state renders and Mark all read is disabled", async ({ page }) => {
    await login(page, [], 0);
    await expect(
      page.getByRole("link", { name: "Notifications", exact: true }),
    ).toBeVisible();
    await expect(page.getByText("9+")).toHaveCount(0);
    await gotoNotifications(page);

    await expect(
      page.getByText("You're all caught up — no notifications yet."),
    ).toBeVisible();
    await expect(
      page.getByRole("button", { name: "Mark all read" }),
    ).toBeDisabled();
  });

  test("390px: notifications page renders without horizontal overflow", async ({
    page,
  }) => {
    await page.setViewportSize({ width: 390, height: 844 });
    await login(page, baseItems());
    await gotoNotifications(page);
    const overflow = await page.evaluate(
      () =>
        document.documentElement.scrollWidth -
        document.documentElement.clientWidth,
    );
    expect(overflow).toBe(0);
  });
});
