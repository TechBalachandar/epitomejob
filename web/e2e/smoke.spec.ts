import { test, expect, type Page } from "@playwright/test";

// W1 smoke test: the BFF (/api/*) is stubbed with page.route — the app
// talks to the stubs exactly as it would to the real backend through the
// BFF proxy, and no real backend (or MSW) is involved. Log in once, then
// visit every page through the sidebar (client-side navigation keeps the
// in-memory session alive) and assert each page renders with no console
// errors.
const PAGES = [
  { nav: "Dashboard", heading: "Good morning, Dev" },
  { nav: "Profile", heading: "Profile" },
  { nav: "Resumes", heading: "Resumes" },
  { nav: "Jobs", heading: "Jobs" },
  { nav: "Matches", heading: "Matches" },
  { nav: "Coach", heading: "Coach" },
  { nav: "Approvals", heading: "Approvals" },
  { nav: "Interviews", heading: "Interviews" },
  { nav: "Tracker", heading: "Tracker" },
  { nav: "Learning", heading: "Learning" },
];

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
  completeness: 80,
  createdAt: "2026-10-13T00:00:00.000Z",
  updatedAt: "2026-11-10T00:00:00.000Z",
};

interface StubApproval {
  id: string;
  userId: string;
  action: string;
  payload: string;
  status: string;
  expiresAt: string;
  decidedAt: string | null;
}

function stubApprovals(): StubApproval[] {
  const in20h = new Date(Date.now() + 20 * 3_600_000).toISOString();
  const in9h = new Date(Date.now() + 9 * 3_600_000).toISOString();
  return [
    {
      id: "appr-1",
      userId: "user-1",
      action: "apply_to_job",
      payload: JSON.stringify({ jobId: "job-2", resumeId: "resume-1" }),
      status: "PENDING",
      expiresAt: in20h,
      decidedAt: null,
    },
    {
      id: "appr-2",
      userId: "user-1",
      action: "apply_to_job",
      payload: JSON.stringify({ jobId: "job-1", resumeId: "resume-1" }),
      status: "PENDING",
      expiresAt: in9h,
      decidedAt: null,
    },
  ];
}

/** Stub the whole BFF surface the smoke tests touch. Stateful for approvals. */
async function stubApi(page: Page) {
  const approvals = stubApprovals();

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
  await page.route("**/api/approvals/pending", (route) =>
    route.fulfill({
      status: 200,
      json: approvals.filter((a) => a.status === "PENDING"),
    }),
  );
  await page.route("**/api/approvals/*/approve", (route) => {
    const id = route.request().url().split("/").at(-2) ?? "";
    const item = approvals.find((a) => a.id === id);
    if (!item) return route.fulfill({ status: 404, json: { message: "Not found" } });
    item.status = "APPROVED";
    item.decidedAt = new Date().toISOString();
    return route.fulfill({ status: 200, json: item });
  });
  await page.route("**/api/approvals/*/reject", (route) => {
    const id = route.request().url().split("/").at(-2) ?? "";
    const item = approvals.find((a) => a.id === id);
    if (!item) return route.fulfill({ status: 404, json: { message: "Not found" } });
    item.status = "REJECTED";
    item.decidedAt = new Date().toISOString();
    return route.fulfill({ status: 200, json: item });
  });
  await page.route("**/api/approvals/stream", (route) =>
    route.fulfill({
      status: 200,
      contentType: "text/event-stream",
      body: `event: pending\ndata: ${JSON.stringify({ count: approvals.filter((a) => a.status === "PENDING").length })}\n\n`,
    }),
  );
}

test.describe("W1 smoke", () => {
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
      page.getByRole("heading", { name: "Good morning, Dev" }),
    ).toBeVisible({ timeout: 15_000 });
  }

  test("every page renders with no console errors", async ({ page }) => {
    const consoleErrors: string[] = [];
    page.on("console", (msg) => {
      if (msg.type() === "error") consoleErrors.push(msg.text());
    });
    page.on("pageerror", (err) => consoleErrors.push(err.message));

    await login(page);

    for (const { nav, heading } of PAGES) {
      await gotoViaNav(page, nav);
      // Generous timeout: the dev server compiles each route on first
      // request, which is slow on first run (and on older machines).
      await expect(
        page.getByRole("heading", { name: heading, exact: true }).first(),
      ).toBeVisible({ timeout: 15_000 });
    }

    expect(consoleErrors).toEqual([]);
  });

  test("login rejects a short password", async ({ page }) => {
    await page.goto("/login");
    await page.getByLabel("Email").fill("dev@example.com");
    await page.getByLabel("Password").fill("short");
    await page.getByRole("button", { name: "Sign in" }).click();
    await expect(page.getByText("at least 8 characters")).toBeVisible();
  });

  test("approval flow: Approve removes the card", async ({ page }) => {
    await login(page);
    await gotoViaNav(page, "Approvals");

    const cards = page.locator("text=Apply To Job");
    await expect(cards.first()).toBeVisible();
    const before = await cards.count();
    await page.getByRole("button", { name: "Approve" }).first().click();
    await expect(page.getByText("Approved")).toBeVisible();
    await expect(cards).toHaveCount(before - 1);
  });

  test("coach chat returns a mock reply", async ({ page }) => {
    await login(page);
    await gotoViaNav(page, "Coach");

    await page
      .getByLabel("Message the Coach")
      .fill("How can I improve my resume?");
    await page.getByRole("button", { name: "Send message" }).click();
    await expect(page.getByText("Coach is thinking…")).toBeVisible();
    await expect(page.getByText(/resume is in good shape/)).toBeVisible({
      timeout: 10_000,
    });
  });

  test("mobile 390px: dashboard renders without horizontal overflow", async ({
    page,
  }) => {
    await page.setViewportSize({ width: 390, height: 844 });
    await login(page);
    const overflow = await page.evaluate(
      () =>
        document.documentElement.scrollWidth -
        document.documentElement.clientWidth,
    );
    expect(overflow).toBe(0);
  });
});
