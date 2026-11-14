import { test, expect, type Page } from "@playwright/test";

// W6: one profile, many resumes + job sources. The BFF (/api/*) is stubbed
// with page.route — the app talks to the stubs exactly as it would to the
// real backend through the BFF proxy. Two READY resumes with different
// skills produce different top matches; jobs carry a source + apply link.
const SESSION = {
  accessToken: "e2e-access-token",
  expiresInMs: 900_000,
  userId: "user-1",
  email: "dev@example.com",
  role: "USER",
};

const isoDaysAgo = (days: number) =>
  new Date(Date.now() - days * 24 * 3_600_000).toISOString();

const dateDaysAgo = (days: number) => isoDaysAgo(days).slice(0, 10);

const JOBS = [
  {
    id: "job-1",
    title: "Senior Android Engineer",
    company: "Northwind Health Systems",
    location: "Reston, VA",
    workType: "Hybrid",
    salaryMin: 145000,
    salaryMax: 175000,
    currency: "USD",
    sponsorsH1b: true,
    skills: ["Kotlin", "Jetpack Compose", "Android"],
    summary: "Build Android banking apps.",
    role: "Android",
    postedAt: "2026-11-10T09:00:00Z",
    source: "LINKEDIN",
    sourceUrl: "https://www.linkedin.com/jobs/search/?keywords=Android%20Engineer",
  },
  {
    id: "job-2",
    title: "Senior Software Engineer, Backend (Java)",
    company: "Meridian Care Technologies",
    location: "Remote (US)",
    workType: "Remote",
    salaryMin: 160000,
    salaryMax: 195000,
    currency: "USD",
    sponsorsH1b: true,
    skills: ["Java 21", "Spring Boot", "Kafka"],
    summary: "Build Spring Boot microservices.",
    role: "Backend",
    postedAt: "2026-11-11T09:00:00Z",
    source: "INDEED",
    sourceUrl: null,
  },
];

const MATCHES_BY_RESUME: Record<string, unknown[]> = {
  "resume-1": [
    {
      job: JOBS[0],
      score: 88,
      reasons: [{ label: "Skills: Kotlin, Compose (+38)", points: 38 }],
      gaps: ["Missing skill: Hilt"],
      source: "RESUME",
    },
  ],
  "resume-2": [
    {
      job: JOBS[1],
      score: 91,
      reasons: [{ label: "Skills: Java 21, Spring Boot (+38)", points: 38 }],
      gaps: ["Missing skill: Azure"],
      source: "RESUME",
    },
  ],
};

const RESUMES = [
  {
    id: "resume-1",
    label: "Android Developer",
    fileName: "android.pdf",
    fileType: "application/pdf",
    fileSize: 1234,
    status: "READY",
    createdAt: isoDaysAgo(9),
  },
  {
    id: "resume-2",
    label: "Java Backend",
    fileName: "java.pdf",
    fileType: "application/pdf",
    fileSize: 1234,
    status: "READY",
    createdAt: isoDaysAgo(2),
  },
];

let profile = {
  id: "profile-1",
  userId: "user-1",
  headline: "Senior Software Engineer",
  summary: "Principal Mobile Architect turned AI engineer.",
  location: "Reston, VA",
  workType: "HYBRID",
  salaryMin: 150000,
  salaryMax: 190000,
  currency: "USD",
  visibility: "PRIVATE",
  skills: ["Java", "Kotlin", "Spring Boot"],
  h1bClockStartDate: dateDaysAgo(30),
  linkedinUrl: "https://www.linkedin.com/in/dev-example",
  workAuthorization: "H1B",
  completeness: 100,
  createdAt: "2026-10-13T00:00:00.000Z",
  updatedAt: "2026-11-10T00:00:00.000Z",
};

async function stubW6(page: Page, opts: { profilePutStatus?: number } = {}) {
  const resumes = RESUMES.map((r) => ({ ...r }));
  let lastProfilePut: Record<string, unknown> | null = null;

  await page.route("**/api/auth/login", (route) =>
    route.fulfill({ status: 200, json: SESSION }),
  );
  await page.route("**/api/auth/refresh", (route) =>
    route.fulfill({ status: 401, json: { message: "No refresh token" } }),
  );
  await page.route("**/api/profiles/me", async (route) => {
    if (route.request().method() === "PUT") {
      const body = route.request().postDataJSON() as Record<string, unknown>;
      lastProfilePut = body;
      if (opts.profilePutStatus === 400) {
        return route.fulfill({ status: 400, json: { message: "INVALID_LINKEDIN_URL" } });
      }
      profile = { ...profile, ...body } as typeof profile;
      return route.fulfill({ status: 200, json: profile });
    }
    return route.fulfill({ status: 200, json: profile });
  });
  // NOTE: register the wildcard BEFORE /resumes/me — Playwright gives
  // the most recently registered matching route precedence, so /me must
  // come last or the wildcard swallows the list call with a 405.
  await page.route("**/api/resumes/*", async (route) => {
    if (route.request().method() !== "PUT") {
      return route.fulfill({ status: 405, json: { message: "Method not allowed" } });
    }
    const id = route.request().url().split("/").at(-1) ?? "";
    const body = route.request().postDataJSON() as { label: string };
    const item = resumes.find((r) => r.id === id);
    if (!item) return route.fulfill({ status: 404, json: { message: "Not found" } });
    item.label = body.label;
    return route.fulfill({ status: 200, json: item });
  });
  await page.route("**/api/resumes/me", (route) =>
    route.fulfill({ status: 200, json: resumes }),
  );
  await page.route("**/api/matches/me**", (route) => {
    const url = new URL(route.request().url());
    const resumeId = url.searchParams.get("resumeId");
    // No resumeId = latest READY resume (resume-2, the newest).
    const matches = MATCHES_BY_RESUME[resumeId ?? "resume-2"] ?? [];
    return route.fulfill({ status: 200, json: matches });
  });
  await page.route("**/api/jobs**", (route) => {
    const url = new URL(route.request().url());
    const id = url.pathname.split("/").at(-1);
    if (id && id !== "jobs") {
      const job = JOBS.find((j) => j.id === id);
      return route.fulfill(job ? { status: 200, json: job } : { status: 404, json: { message: "Not found" } });
    }
    let content = [...JOBS];
    const source = url.searchParams.get("source");
    if (source) content = content.filter((j) => j.source === source);
    const q = url.searchParams.get("query")?.toLowerCase();
    if (q) content = content.filter((j) => `${j.title} ${j.company}`.toLowerCase().includes(q));
    return route.fulfill({
      status: 200,
      json: { content, page: 0, size: 20, totalElements: content.length, totalPages: 1 },
    });
  });
  await page.route("**/api/tracker", (route) =>
    route.fulfill({ status: 200, json: [] }),
  );
  await page.route("**/api/approvals/pending", (route) =>
    route.fulfill({ status: 200, json: [] }),
  );
  await page.route("**/api/notifications/unread-count", (route) =>
    route.fulfill({ status: 200, json: { unreadCount: 0 } }),
  );
  await page.route("**/api/interviews/sessions", (route) =>
    route.fulfill({ status: 200, json: [] }),
  );
  return {
    getLastProfilePut: () => lastProfilePut,
  };
}

async function login(page: Page, opts: { profilePutStatus?: number } = {}) {
  const stubs = await stubW6(page, opts);
  await page.goto("/login");
  await page.getByLabel("Email").fill("dev@example.com");
  await page.getByLabel("Password").fill("DevPass123!");
  await page.getByRole("button", { name: "Sign in" }).click();
  await expect(
    page.getByRole("heading", { name: /Good (morning|afternoon|evening)/ }),
  ).toBeVisible({ timeout: 15_000 });
  return stubs;
}

async function gotoViaNav(page: Page, name: string) {
  const menuButton = page.getByRole("button", { name: "Open menu" });
  if (await menuButton.isVisible()) await menuButton.click();
  await page.getByRole("link", { name, exact: true }).first().click();
}

test.describe("W6 per-resume matches + job sources", () => {
  test("matches: default is the latest READY resume; switching the selector reloads matches and the URL", async ({
    page,
  }) => {
    await login(page);
    await gotoViaNav(page, "Matches");

    // Default = latest READY (resume-2, Java Backend): 91%, Indeed badge,
    // no apply link because sourceUrl is null.
    await expect(page.getByText("Senior Software Engineer, Backend (Java)").first()).toBeVisible();
    await expect(page.getByText("91%").first()).toBeVisible();
    await expect(
      page.getByTestId("job-source-badge").filter({ hasText: "Indeed" }),
    ).toBeVisible();
    await expect(page.getByRole("link", { name: /Apply on/ })).toHaveCount(0);

    // Switch to the Android resume: URL gains ?resumeId=resume-1 and the
    // matches reload to the Android job with a LinkedIn apply link.
    await page.getByLabel("Resume").selectOption("resume-1");
    await expect(page).toHaveURL(/resumeId=resume-1/);
    await expect(page.getByText("Senior Android Engineer").first()).toBeVisible();
    await expect(page.getByText("88%").first()).toBeVisible();
    await expect(
      page.getByTestId("job-source-badge").filter({ hasText: "LinkedIn" }),
    ).toBeVisible();
    const link = page.getByTestId("job-source-link").filter({ hasText: "Apply on LinkedIn" });
    await expect(link).toBeVisible();
    await expect(link).toHaveAttribute(
      "href",
      "https://www.linkedin.com/jobs/search/?keywords=Android%20Engineer",
    );
    await expect(link).toHaveAttribute("target", "_blank");
    await expect(link).toHaveAttribute("rel", "noopener noreferrer");
  });

  test("dashboard: per-resume strip + top matches follow the selected resume", async ({
    page,
  }) => {
    await login(page);
    // Already on the dashboard after login. The per-resume strip shows
    // each label with its top match score (labels also appear as select
    // options, so assert the strip via its unique score lines).
    await expect(page.getByText("Top match 88%")).toBeVisible();
    await expect(page.getByText("Top match 91%")).toBeVisible();
    await expect(page.getByRole("option", { name: "Android Developer" })).toBeAttached();
    await expect(page.getByRole("option", { name: "Java Backend" })).toBeAttached();

    // Default top match is the Java Backend resume's (91%).
    await expect(page.getByText("Senior Software Engineer, Backend (Java)").first()).toBeVisible();

    // Switch on the dashboard: top matches follow, and the link to
    // Matches keeps the selection in the URL.
    await page.getByLabel("Resume").selectOption("resume-1");
    await expect(page.getByText("Senior Android Engineer").first()).toBeVisible();
    // The top-matches link (title · company) keeps the selection in its href.
    const matchesLink = page.getByRole("link", { name: /Senior Android Engineer ·/ }).first();
    await expect(matchesLink).toHaveAttribute("href", /resumeId=resume-1/);
  });

  test("resumes: labels shown, inline rename PUTs the label, upload has a label field", async ({
    page,
  }) => {
    await login(page);
    await gotoViaNav(page, "Resumes");

    await expect(page.getByText("Android Developer").first()).toBeVisible();
    await expect(page.getByText("Java Backend").first()).toBeVisible();
    await expect(page.getByText("android.pdf")).toBeVisible();
    await expect(page.getByPlaceholder("Android Developer")).toBeVisible();

    await page.getByRole("button", { name: "Rename Android Developer" }).click();
    await page.getByLabel("Label for android.pdf").fill("Android Lead");
    await page.getByRole("button", { name: "Save" }).click();
    await expect(page.getByText("Android Lead").first()).toBeVisible();
    await expect(page.getByText("Resume renamed")).toBeVisible();
  });

  test("jobs: source badge + apply link on rows and detail, source filter works", async ({
    page,
  }) => {
    await login(page);
    await gotoViaNav(page, "Jobs");

    await expect(page.getByText("Senior Android Engineer").first()).toBeVisible();
    await expect(
      page.getByTestId("job-source-badge").filter({ hasText: "LinkedIn" }).first(),
    ).toBeVisible();
    await expect(page.getByRole("link", { name: "Apply on LinkedIn" }).first()).toBeVisible();
    await expect(
      page.getByTestId("job-source-badge").filter({ hasText: "Indeed" }).first(),
    ).toBeVisible();

    await page.getByLabel("Job source").selectOption("LINKEDIN");
    await expect(page.getByText("Senior Android Engineer").first()).toBeVisible();
    await expect(page.getByText("Senior Software Engineer, Backend (Java)")).toHaveCount(0);
  });

  test("profile: LinkedIn link + work authorisation round-trip on save", async ({
    page,
  }) => {
    const stubs = await login(page);
    await gotoViaNav(page, "Profile");

    await expect(
      page.getByRole("link", { name: "https://www.linkedin.com/in/dev-example" }),
    ).toBeVisible();
    await expect(page.getByText("H-1B").first()).toBeVisible();

    await page.getByLabel("LinkedIn URL").fill("https://www.linkedin.com/in/dev-new");
    await page.getByLabel("Work authorisation").selectOption("GREEN_CARD");
    await page.getByRole("button", { name: "Save profile" }).click();
    await expect(page.getByText("Profile saved")).toBeVisible();
    expect(stubs.getLastProfilePut()).toMatchObject({
      linkedinUrl: "https://www.linkedin.com/in/dev-new",
      workAuthorization: "GREEN_CARD",
    });
  });

  test("profile: API validation error is shown inline", async ({ page }) => {
    await login(page, { profilePutStatus: 400 });
    await gotoViaNav(page, "Profile");
    await page.getByLabel("LinkedIn URL").fill("https://www.linkedin.com/in/dev-new");
    await page.getByRole("button", { name: "Save profile" }).click();
    // Scoped to <main>: Next's route announcer is also role=alert, and
    // the toast shows the same message outside main.
    await expect(page.getByRole("main").getByText("INVALID_LINKEDIN_URL")).toBeVisible();
  });

  test("390px: matches page renders without horizontal overflow", async ({
    page,
  }) => {
    await page.setViewportSize({ width: 390, height: 844 });
    await login(page);
    await gotoViaNav(page, "Matches");
    await expect(page.getByText("Senior Software Engineer, Backend (Java)").first()).toBeVisible();
    const overflow = await page.evaluate(
      () =>
        document.documentElement.scrollWidth -
        document.documentElement.clientWidth,
    );
    expect(overflow).toBe(0);
  });
});
