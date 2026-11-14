import { test, expect, type Page } from "@playwright/test";

// Tracker drag-and-drop: cards move between kanban columns with native
// HTML5 drag-and-drop (Jira / Azure Boards style), no status dropdown.
// The BFF (/api/*) is stubbed with page.route — the app talks to the stubs
// exactly as it would to the real backend through the BFF proxy.

const SESSION = {
  accessToken: "e2e-access-token",
  expiresInMs: 900_000,
  userId: "user-1",
  email: "dev@example.com",
  role: "USER",
};

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

/** Minimal BFF stubs for the Tracker page: login, profile, tracker. */
async function stubApi(page: Page) {
  const tracker: Record<string, unknown>[] = [
    {
      id: "trk-1",
      jobId: "job-2",
      title: "Agentic AI Engineer",
      company: "Meridian Care Technologies",
      resumeId: "resume-1",
      status: "SAVED",
      notes: "Strong match",
      createdAt: "2026-11-11T00:00:00Z",
      updatedAt: "2026-11-11T00:00:00Z",
    },
    {
      id: "trk-2",
      jobId: "job-1",
      title: "Senior Software Engineer, Backend (Java)",
      company: "Northwind Health Systems",
      resumeId: "resume-1",
      status: "APPLIED",
      notes: null,
      createdAt: "2026-11-10T00:00:00Z",
      updatedAt: "2026-11-10T00:00:00Z",
    },
  ];

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
  // The header bell polls the unread count on every authenticated page.
  await page.route("**/api/notifications/unread-count", (route) =>
    route.fulfill({ status: 200, json: { unreadCount: 0 } }),
  );
  await page.route("**/api/billing/status", (route) =>
    route.fulfill({ status: 200, json: { enabled: false, provider: "stripe" } }),
  );
  await page.route("**/api/tracker**", async (route) => {
    const req = route.request();
    const url = new URL(req.url());
    const parts = url.pathname.split("/").filter(Boolean);
    const id = parts.length > 2 ? parts.at(-1) : null;
    if (req.method() === "GET" && !id) return route.fulfill({ status: 200, json: tracker });
    const item = tracker.find((t) => t.id === id);
    if (!item) return route.fulfill({ status: 404, json: { message: "Not found" } });
    if (req.method() === "PUT") {
      const body = req.postDataJSON() as { status?: string; notes?: string };
      Object.assign(item, body, { updatedAt: new Date().toISOString() });
      return route.fulfill({ status: 200, json: item });
    }
    if (req.method() === "DELETE") {
      tracker.splice(tracker.indexOf(item), 1);
      return route.fulfill({ status: 204, body: "" });
    }
    return route.fulfill({ status: 405, json: { message: "Method not allowed" } });
  });
}

test.describe("Tracker drag and drop", () => {
  /** Navigate through the sidebar; opens the mobile drawer when needed. */
  async function gotoViaNav(page: Page, name: string) {
    const menuButton = page.getByRole("button", { name: "Open menu" });
    if (await menuButton.isVisible()) await menuButton.click();
    await page.getByRole("link", { name, exact: true }).first().click();
  }

  async function login(page: Page) {
    await stubApi(page);
    await page.goto("/login");
    await expect(
      page.getByRole("heading", { name: "Sign in to EpitomeJob" }),
    ).toBeVisible();
    await page.getByLabel("Email").fill("dev@example.com");
    await page.getByLabel("Password").fill("DevPass123!");
    await page.getByRole("button", { name: "Sign in" }).click();
    await expect(
      page.getByRole("heading", { name: /Good (morning|afternoon|evening)/ }),
    ).toBeVisible({ timeout: 15_000 });
  }

  function column(page: Page, name: string) {
    return page.locator("div.rounded-xl", {
      has: page.getByRole("heading", { name, exact: true }),
    });
  }

  test("drag a card from Saved to Applied moves it and fires the update", async ({
    page,
  }) => {
    await login(page);
    await gotoViaNav(page, "Tracker");
    // Generous timeout: the dev server compiles the route on first request.
    await expect(column(page, "Saved").getByRole("heading", { name: "Agentic AI Engineer" })).toBeVisible({ timeout: 15_000 });
    // No status dropdown anymore — the card moves by dragging only.
    await expect(page.getByLabel(/Move .* to another column/)).toHaveCount(0);

    const putPromise = page.waitForRequest(
      (req) => req.url().includes("/api/tracker/trk-1") && req.method() === "PUT",
    );
    await page.dragAndDrop(
      '[data-testid="tracker-card-trk-1"]',
      '[data-testid="tracker-column-APPLIED"]',
    );
    const put = await putPromise;
    expect(put.postDataJSON()).toEqual({ status: "APPLIED" });

    await expect(column(page, "Applied").getByRole("heading", { name: "Agentic AI Engineer" })).toBeVisible();
    await expect(column(page, "Saved").getByRole("heading", { name: "Agentic AI Engineer" })).toHaveCount(0);
  });

  test("dropping a card on its own column is a no-op", async ({ page }) => {
    await login(page);
    await gotoViaNav(page, "Tracker");
    // Generous timeout: the dev server compiles the route on first request.
    await expect(column(page, "Saved").getByRole("heading", { name: "Agentic AI Engineer" })).toBeVisible({ timeout: 15_000 });

    let putCount = 0;
    page.on("request", (req) => {
      if (req.url().includes("/api/tracker/trk-1") && req.method() === "PUT") putCount += 1;
    });
    await page.dragAndDrop(
      '[data-testid="tracker-card-trk-1"]',
      '[data-testid="tracker-column-SAVED"]',
    );
    await page.waitForTimeout(500);
    expect(putCount).toBe(0);
    await expect(column(page, "Saved").getByRole("heading", { name: "Agentic AI Engineer" })).toBeVisible();
  });

  test("390px: tracker renders without horizontal overflow", async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 });
    await login(page);
    await gotoViaNav(page, "Tracker");
    await expect(page.getByTestId("tracker-column-SAVED")).toBeVisible({ timeout: 15_000 });
    const overflow = await page.evaluate(
      () =>
        document.documentElement.scrollWidth -
        document.documentElement.clientWidth,
    );
    expect(overflow).toBe(0);
  });
});
