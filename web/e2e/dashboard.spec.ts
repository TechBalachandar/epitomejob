import { test, expect, type Page } from "@playwright/test";

// W5 dashboard: every number on the page comes from a stubbed BFF
// response — the same shape the real backend returns — and nothing is
// hardcoded. Login once, then assert the live stats, the 60-day clock,
// derived next steps, derived recent activity and the top-matches preview.
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
    title: "Senior Software Engineer, Backend (Java)",
    company: "Northwind Health Systems",
    location: "Reston, VA",
    workType: "Hybrid",
    salaryMin: 145000,
    salaryMax: 175000,
    currency: "USD",
    sponsorsH1b: true,
    skills: ["Java 21", "Spring Boot", "Kafka"],
    summary: "Build and own Spring Boot microservices for claims processing.",
    role: "Backend",
    postedAt: "2026-11-10T09:00:00Z",
  },
  {
    id: "job-2",
    title: "Agentic AI Engineer",
    company: "Meridian Care Technologies",
    location: "Remote (US)",
    workType: "Remote",
    salaryMin: 160000,
    salaryMax: 195000,
    currency: "USD",
    sponsorsH1b: true,
    skills: ["Python", "LangGraph", "RAG"],
    summary: "Design multi-agent workflows for member operations.",
    role: "AI",
    postedAt: "2026-11-11T09:00:00Z",
  },
];

const MATCHES = [
  {
    job: JOBS[1],
    score: 92,
    reasons: [{ label: "Skills: Python, LangGraph (+38)", points: 38 }],
    gaps: ["Missing skill: Evals"],
    source: "RESUME",
  },
  {
    job: JOBS[0],
    score: 82,
    reasons: [{ label: "Skills: Java 21, Spring Boot (+38)", points: 38 }],
    gaps: ["Missing skill: Azure"],
    source: "PROFILE",
  },
];

function baseProfile(overrides: Record<string, unknown> = {}) {
  return {
    id: "profile-1",
    userId: "user-1",
    headline: "",
    summary: "Principal Mobile Architect turned AI engineer.",
    location: "Reston, VA",
    workType: "HYBRID",
    salaryMin: null,
    salaryMax: null,
    currency: "USD",
    visibility: "PRIVATE",
    skills: ["Java", "LangGraph", "Spring Boot"],
    h1bClockStartDate: dateDaysAgo(30),
    completeness: 60,
    createdAt: "2026-10-13T00:00:00.000Z",
    updatedAt: "2026-11-10T00:00:00.000Z",
    ...overrides,
  };
}

async function stubDashboard(page: Page, profile: Record<string, unknown>) {
  await page.route("**/api/auth/login", (route) =>
    route.fulfill({ status: 200, json: SESSION }),
  );
  await page.route("**/api/auth/refresh", (route) =>
    route.fulfill({ status: 401, json: { message: "No refresh token" } }),
  );
  await page.route("**/api/profiles/me", (route) =>
    route.fulfill({ status: 200, json: profile }),
  );
  await page.route("**/api/resumes/me", (route) =>
    route.fulfill({
      status: 200,
      json: [
        {
          id: "resume-1",
          label: "resume.pdf",
          fileName: "resume.pdf",
          fileType: "application/pdf",
          fileSize: 1234,
          status: "READY",
          createdAt: isoDaysAgo(2),
        },
      ],
    }),
  );
  // W6: the dashboard fetches matches with ?resumeId= (selected resume)
  // and once per READY resume for the per-resume strip — the glob must
  // match the query string too.
  await page.route("**/api/matches/me**", (route) =>
    route.fulfill({ status: 200, json: MATCHES }),
  );
  await page.route("**/api/tracker", (route) =>
    route.fulfill({
      status: 200,
      json: [
        {
          id: "trk-1",
          jobId: "job-2",
          title: "Agentic AI Engineer",
          company: "Meridian Care Technologies",
          resumeId: "resume-1",
          status: "SAVED",
          notes: null,
          createdAt: isoDaysAgo(3),
          updatedAt: isoDaysAgo(1),
        },
        {
          id: "trk-2",
          jobId: "job-1",
          title: "Senior Software Engineer, Backend (Java)",
          company: "Northwind Health Systems",
          resumeId: null,
          status: "REJECTED",
          notes: null,
          createdAt: isoDaysAgo(5),
          updatedAt: isoDaysAgo(4),
        },
      ],
    }),
  );
  await page.route("**/api/approvals/pending", (route) =>
    route.fulfill({
      status: 200,
      json: [
        {
          id: "appr-1",
          userId: "user-1",
          action: "APPLY_JOB",
          payload: JSON.stringify({ jobId: "job-2" }),
          status: "PENDING",
          expiresAt: isoDaysAgo(-1),
          decidedAt: null,
        },
        {
          id: "appr-2",
          userId: "user-1",
          action: "APPLY_JOB",
          payload: JSON.stringify({ jobId: "job-1" }),
          status: "PENDING",
          expiresAt: isoDaysAgo(-1),
          decidedAt: null,
        },
      ],
    }),
  );
  // M13 bell: the header bell polls the unread count on every
  // authenticated page; stub it so the BFF never 500s (no backend in e2e).
  await page.route("**/api/notifications/unread-count", (route) =>
    route.fulfill({ status: 200, json: { unreadCount: 0 } }),
  );
  await page.route("**/api/interviews/sessions", (route) =>
    route.fulfill({
      status: 200,
      json: [
        {
          id: "sess-1",
          jobId: "job-2",
          jobTitle: "Agentic AI Engineer",
          status: "IN_PROGRESS",
          createdAt: isoDaysAgo(1),
          completedAt: null,
          answers: [],
          averageScore: null,
        },
      ],
    }),
  );
}

async function loginToDashboard(page: Page, profile: Record<string, unknown>) {
  await stubDashboard(page, profile);
  await page.goto("/login");
  await page.getByLabel("Email").fill("dev@example.com");
  await page.getByLabel("Password").fill("DevPass123!");
  await page.getByRole("button", { name: "Sign in" }).click();
  await expect(
    page.getByRole("heading", { name: /Good (morning|afternoon|evening)/ }),
  ).toBeVisible({ timeout: 15_000 });
}

test.describe("W5 dashboard (live data)", () => {
  test("renders real numbers, clock, next steps, activity and top matches", async ({
    page,
  }) => {
    await loginToDashboard(page, baseProfile());

    // Greeting by time of day only — no fake name, no hardcoded stats.
    await expect(page.getByText("Good morning, Dev")).toHaveCount(0);
    await expect(page.getByText("Coming in W2")).toHaveCount(0);
    await expect(page.getByText("92% Meridian Care")).toHaveCount(0);

    // Stat cards come from the stubbed hooks: 1 resume, 2 matches with
    // top score 92, 1 active application (SAVED; REJECTED does not count),
    // 2 pending approvals, 1 interview session.
    await expect(
      page.getByRole("link", { name: "1 Resumes", exact: true }),
    ).toContainText("1");
    const matchesCard = page.getByRole("link", {
      name: "2 Matches Top score 92%",
      exact: true,
    });
    await expect(matchesCard).toContainText("2");
    await expect(matchesCard).toContainText("Top score 92%");
    await expect(
      page.getByRole("link", { name: "1 Active applications", exact: true }),
    ).toContainText("1");
    await expect(
      page.getByRole("link", { name: "2 Pending approvals", exact: true }),
    ).toContainText("2");
    await expect(
      page.getByRole("link", { name: "1 Interview sessions", exact: true }),
    ).toContainText("1");

    // 60-day clock: started 30 days ago → 30 days left, active.
    await expect(page.getByText(/30\s*days left/)).toBeVisible();
    await expect(page.getByText(/Deadline/)).toBeVisible();
    await expect(page.getByText("Active", { exact: true })).toBeVisible();

    // Profile completeness comes from the server (60%), not a hardcoded 80.
    await expect(page.getByText("60%")).toBeVisible();

    // Next steps derived in contract order: profile (missing headline +
    // salary range), approvals, top match. A resume exists, so no upload CTA.
    await expect(
      page.getByText("Complete your profile — missing: Headline, Salary range."),
    ).toBeVisible();
    await expect(
      page.getByText("2 approvals waiting for your decision."),
    ).toBeVisible();
    await expect(
      page.getByText("Review your 92% match: Agentic AI Engineer at Meridian Care Technologies."),
    ).toBeVisible();
    await expect(page.getByText("Upload your resume")).toHaveCount(0);

    // Recent activity is derived from real rows, newest first.
    await expect(page.getByText("Resume uploaded: resume.pdf — READY")).toBeVisible();
    await expect(
      page.getByText("Saved Agentic AI Engineer at Meridian Care Technologies"),
    ).toBeVisible();

    // Top matches preview with score bars.
    await expect(page.getByText("Agentic AI Engineer · Meridian Care Technologies")).toBeVisible();
    await expect(page.getByText("92%", { exact: true })).toBeVisible();
    await expect(
      page.getByRole("progressbar", {
        name: "Match score for Agentic AI Engineer",
      }),
    ).toHaveAttribute("aria-valuenow", "92");
  });

  test("clock not-set shows a CTA to /profile", async ({ page }) => {
    await loginToDashboard(
      page,
      baseProfile({ h1bClockStartDate: null, completeness: 100, headline: "Senior Software Engineer", salaryMin: 150000, salaryMax: 190000 }),
    );
    await expect(page.getByText("Not set")).toBeVisible();
    await expect(
      page.getByRole("link", { name: "Set your clock start date" }),
    ).toHaveAttribute("href", "/profile");
  });

  test("clock urgent at 10 days left uses the warning style", async ({
    page,
  }) => {
    await loginToDashboard(page, baseProfile({ h1bClockStartDate: dateDaysAgo(50) }));
    await expect(page.getByText(/10\s*days left/)).toBeVisible();
    await expect(page.getByText("Urgent", { exact: true })).toBeVisible();
    await expect(page.getByText(/almost up/)).toBeVisible();
  });

  test("390px: dashboard renders without horizontal overflow", async ({
    page,
  }) => {
    await page.setViewportSize({ width: 390, height: 844 });
    await loginToDashboard(page, baseProfile());
    const overflow = await page.evaluate(
      () =>
        document.documentElement.scrollWidth -
        document.documentElement.clientWidth,
    );
    expect(overflow).toBe(0);
  });
});
