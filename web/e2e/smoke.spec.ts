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
    {
      id: "appr-3",
      userId: "user-1",
      action: "APPLY_JOB",
      payload: JSON.stringify({ jobId: "job-2", resumeId: "resume-1", trackerId: "trk-1" }),
      status: "PENDING",
      expiresAt: in20h,
      decidedAt: null,
    },
    {
      id: "appr-4",
      userId: "user-1",
      action: "UPDATE_PROFILE",
      payload: JSON.stringify({ fields: { headline: "Senior Agentic AI Engineer" } }),
      status: "PENDING",
      expiresAt: in20h,
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
  // --- W2 stubs (contract-shaped, stateful) ---------------------------
  const W2_JOBS = [
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
  const W2_MATCHES = [
    {
      job: W2_JOBS[1],
      score: 92,
      reasons: [{ label: "Skills: Python, LangGraph (+38)", points: 38 }],
      gaps: ["Missing skill: Evals"],
      source: "RESUME",
    },
    {
      job: W2_JOBS[0],
      score: 82,
      reasons: [{ label: "Skills: Java 21, Spring Boot (+38)", points: 38 }],
      gaps: ["Missing skill: Azure"],
      source: "PROFILE",
    },
  ];
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
  ];
  const QUESTIONS = [1, 2, 3, 4, 5].map((n) => ({
    id: `q-${n}`,
    text: `Backend interview question ${n}: how would you design a claims pipeline?`,
    category: n === 1 ? "Behavioral" : "Technical",
    role: "Backend",
  }));
  const sessions: Record<string, any>[] = [];

  await page.route("**/api/resumes/me", (route) =>
    route.fulfill({
      status: 200,
      json: [
        { id: "resume-1", fileName: "resume.pdf", fileType: "application/pdf", fileSize: 1234, status: "READY", createdAt: "2026-11-11T00:00:00Z" },
      ],
    }),
  );
  await page.route("**/api/jobs**", (route) => {
    const url = new URL(route.request().url());
    const id = url.pathname.split("/").at(-1);
    if (id && id !== "jobs") {
      const job = W2_JOBS.find((j) => j.id === id);
      return route.fulfill(job ? { status: 200, json: job } : { status: 404, json: { message: "Not found" } });
    }
    let content = [...W2_JOBS];
    const q = url.searchParams.get("query")?.toLowerCase();
    if (q) content = content.filter((j) => `${j.title} ${j.company} ${j.summary}`.toLowerCase().includes(q));
    const loc = url.searchParams.get("location")?.toLowerCase();
    if (loc) content = content.filter((j) => j.location.toLowerCase().includes(loc));
    const wt = url.searchParams.get("workType");
    if (wt) content = content.filter((j) => j.workType === wt);
    if (url.searchParams.get("sponsorsH1b") === "true") content = content.filter((j) => j.sponsorsH1b);
    return route.fulfill({
      status: 200,
      json: { content, page: 0, size: 20, totalElements: content.length, totalPages: 1 },
    });
  });
  await page.route("**/api/matches**", (route) => {
    const url = new URL(route.request().url());
    const id = url.pathname.split("/").at(-1);
    if (id && id !== "me") {
      const m = W2_MATCHES.find((x) => x.job.id === id);
      return route.fulfill(m ? { status: 200, json: m } : { status: 404, json: { message: "Not found" } });
    }
    return route.fulfill({ status: 200, json: W2_MATCHES });
  });
  await page.route("**/api/tracker**", async (route) => {
    const req = route.request();
    const url = new URL(req.url());
    const parts = url.pathname.split("/").filter(Boolean);
    const id = parts.length > 2 ? parts.at(-1) : null;
    if (req.method() === "GET" && !id) return route.fulfill({ status: 200, json: tracker });
    if (req.method() === "POST") {
      const body = req.postDataJSON() as { jobId: string; resumeId?: string };
      const existing = tracker.find((t) => t.jobId === body.jobId);
      if (existing) return route.fulfill({ status: 409, json: { message: "duplicate" } });
      const job = W2_JOBS.find((j) => j.id === body.jobId);
      if (!job) return route.fulfill({ status: 404, json: { message: "Job not found" } });
      const entry = {
        id: `trk-${tracker.length + 1}`,
        jobId: job.id,
        title: job.title,
        company: job.company,
        resumeId: body.resumeId ?? null,
        status: "SAVED",
        notes: null,
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
      };
      tracker.push(entry);
      return route.fulfill({ status: 201, json: entry });
    }
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
  await page.route("**/api/interviews/**", async (route) => {
    const req = route.request();
    const url = new URL(req.url());
    if (url.pathname === "/api/interviews/questions") {
      if (!url.searchParams.get("jobId")) return route.fulfill({ status: 400, json: { message: "jobId required" } });
      return route.fulfill({ status: 200, json: QUESTIONS });
    }
    if (url.pathname === "/api/interviews/sessions" && req.method() === "POST") {
      const body = req.postDataJSON() as { jobId: string };
      const job = W2_JOBS.find((j) => j.id === body.jobId);
      if (!job) return route.fulfill({ status: 404, json: { message: "Job not found" } });
      const session = {
        id: `sess-${sessions.length + 1}`,
        jobId: job.id,
        jobTitle: job.title,
        status: "IN_PROGRESS",
        createdAt: new Date().toISOString(),
        completedAt: null,
        answers: [],
        averageScore: null,
      };
      sessions.push(session);
      return route.fulfill({ status: 201, json: session });
    }
    if (url.pathname === "/api/interviews/sessions" && req.method() === "GET") {
      return route.fulfill({ status: 200, json: sessions });
    }
    const match = url.pathname.match(/^\/api\/interviews\/sessions\/([^/]+)(\/answers)?$/);
    if (match) {
      const session = sessions.find((x) => x.id === match[1]);
      if (!session) return route.fulfill({ status: 404, json: { message: "Not found" } });
      if (match[2] && req.method() === "POST") {
        const body = req.postDataJSON() as { questionId: string; answerText: string };
        const question = QUESTIONS.find((x) => x.id === body.questionId);
        if (!question || !body.answerText?.trim()) return route.fulfill({ status: 400, json: { message: "Bad answer" } });
        const answer = {
          id: `ans-${session.answers.length + 1}`,
          questionId: question.id,
          questionText: question.text,
          answerText: body.answerText,
          scores: { structure: 7, relevance: 8, specificity: 6, conciseness: 9 },
          overallScore: 75,
          feedback: ["Add a concrete number or outcome (e.g. users impacted, latency reduced).", "Well-paced answer."],
        };
        session.answers = session.answers.filter((a: any) => a.questionId !== question.id);
        session.answers.push(answer);
        if (session.answers.length >= QUESTIONS.length) {
          session.status = "COMPLETED";
          session.completedAt = new Date().toISOString();
          session.averageScore = 75;
        }
        return route.fulfill({ status: 200, json: answer });
      }
      return route.fulfill({ status: 200, json: session });
    }
    return route.fulfill({ status: 404, json: { message: "Not found" } });
  });
  await page.route("**/api/approvals", async (route) => {
    const body = route.request().postDataJSON() as { action: string; payload: string };
    const created = {
      id: `appr-new-${Date.now()}`,
      userId: "user-1",
      action: body.action,
      payload: body.payload,
      status: "PENDING",
      expiresAt: new Date(Date.now() + 20 * 3_600_000).toISOString(),
      decidedAt: null,
    };
    approvals.push(created);
    return route.fulfill({ status: 201, json: created });
  });
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
  // --- W3 Coach stub (contract §2 shape) ---------------------------------
  await page.route("**/api/coach/chat", (route) =>
    route.fulfill({
      status: 200,
      json: {
        reply:
          "Your resume is in good shape — add one quantified result to your Navy Federal bullet.",
        citations: [
          {
            docId: "resume:10000000-0000-4000-8000-000000000001",
            chunk:
              "Principal Mobile Architect at Navy Federal, leading 10+ engineers on banking apps.",
            score: 0.87,
            source: "resume",
          },
        ],
        actions: [],
        specialistsUsed: ["resume", "matching"],
      },
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

  test("W3 coach: reply renders with citations and Worked with line", async ({ page }) => {
    await login(page);
    await gotoViaNav(page, "Coach");

    await page
      .getByLabel("Message the Coach")
      .fill("How can I improve my resume?");
    await page.getByRole("button", { name: "Send message" }).click();
    await expect(page.getByText(/resume is in good shape/)).toBeVisible({
      timeout: 10_000,
    });
    await expect(
      page.getByText(/Principal Mobile Architect at Navy Federal/),
    ).toBeVisible();
    await expect(page.getByText(/resume:10000000/)).toBeVisible();
    await expect(page.getByText(/score 0.87/)).toBeVisible();
    await expect(
      page.getByText("Worked with: resume, matching"),
    ).toBeVisible();
  });

  test("W3 approvals: approving UPDATE_PROFILE PUTs the fields to /profiles/me", async ({ page }) => {
    await login(page);
    await gotoViaNav(page, "Approvals");
    const putPromise = page.waitForRequest(
      (req) =>
        req.url().includes("/api/profiles/me") && req.method() === "PUT",
    );
    const card = page
      .locator("div.rounded-xl", { has: page.getByText("Update Profile") })
      .first();
    await expect(card.getByRole("button", { name: "Approve" })).toBeVisible();
    await card.getByRole("button", { name: "Approve" }).click();
    await expect(page.getByText("Approved")).toBeVisible();
    const put = await putPromise;
    expect(put.postDataJSON()).toEqual({
      headline: "Senior Agentic AI Engineer",
    });
  });

  test("W2 jobs: list renders and search filter works", async ({ page }) => {
    await login(page);
    await gotoViaNav(page, "Jobs");
    await expect(page.getByText("Senior Software Engineer, Backend (Java)").first()).toBeVisible();
    await expect(page.getByText("Agentic AI Engineer").first()).toBeVisible();
    await page.getByLabel("Search jobs").fill("Agentic");
    await expect(page.getByText("Agentic AI Engineer").first()).toBeVisible();
    await expect(page.getByText("Senior Software Engineer, Backend (Java)")).toHaveCount(0);
  });

  test("W2 matches: score, reasons with points, gaps and source badge", async ({ page }) => {
    await login(page);
    await gotoViaNav(page, "Matches");
    await expect(page.getByText("92%").first()).toBeVisible();
    await expect(page.getByText(/Skills: Python, LangGraph/)).toBeVisible();
    await expect(page.getByText("(+38)").first()).toBeVisible();
    await expect(page.getByText("Missing skill: Evals")).toBeVisible();
    await expect(page.getByText("Matched on your resume").first()).toBeVisible();
    await expect(page.getByText("Matched on your profile — no resume yet").first()).toBeVisible();
  });

  test("W2 tracker: status move via the menu", async ({ page }) => {
    await login(page);
    await gotoViaNav(page, "Tracker");
    await expect(page.getByText("Agentic AI Engineer").first()).toBeVisible();
    await page
      .getByLabel("Move Agentic AI Engineer to another column")
      .selectOption("APPLIED");
    await expect(page.getByText("Agentic AI Engineer").first()).toBeVisible();
    // The card now sits in the Applied column (2 Applied cards would mean a dup).
    const appliedColumn = page.locator("div.rounded-xl", { has: page.getByRole("heading", { name: "Applied", exact: true }) });
    await expect(appliedColumn.getByRole("heading", { name: "Agentic AI Engineer" })).toBeVisible();
  });

  test("W2 interviews: answer shows rubric scores and feedback", async ({ page }) => {
    await login(page);
    await gotoViaNav(page, "Interviews");
    await page.getByRole("button", { name: "Start mock interview" }).click();
    await expect(page.getByText(/Backend interview question 1/)).toBeVisible();
    await page
      .getByLabel("Answer to question 1")
      .fill("In my last role I led the situation where our claims pipeline was slow. My task was to cut latency. I took action by adding Kafka partitioning and the result was a 40 percent latency reduction for 2 million members.");
    await page.getByRole("button", { name: "Submit answer" }).click();
    await expect(page.getByText("Mock rubric — deterministic checks, not AI feedback").first()).toBeVisible();
    await expect(page.getByText("Overall score: 75/100").first()).toBeVisible();
    await expect(page.getByText("Relevance: 8/10").first()).toBeVisible();
    await expect(page.getByText(/Add a concrete number or outcome/).first()).toBeVisible();
  });

  test("W2 apply flow: approving APPLY_JOB flips the tracker to APPLIED", async ({ page }) => {
    await login(page);
    await gotoViaNav(page, "Approvals");
    const card = page.locator("div.rounded-xl", { has: page.getByText("Apply Job") }).first();
    await expect(card.getByRole("button", { name: "Approve" })).toBeVisible();
    await card.getByRole("button", { name: "Approve" }).click();
    await expect(page.getByText("Approved")).toBeVisible();
    await gotoViaNav(page, "Tracker");
    const appliedColumn = page.locator("div.rounded-xl", { has: page.getByRole("heading", { name: "Applied", exact: true }) });
    await expect(appliedColumn.getByRole("heading", { name: "Agentic AI Engineer" })).toBeVisible({ timeout: 10_000 });
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
