import { test, expect, type Page } from "@playwright/test";

// Resume Builder: entry → upload-stub → template pick → edit a field →
// AI options appear → download button present. The BFF (/api/*) is stubbed
// with page.route — the app talks to the stubs exactly as it would to the
// real backend through the BFF proxy, and no real backend is involved.
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
  headline: "Senior Software Engineer",
  summary: "Principal Mobile Architect turned AI engineer.",
  location: "Reston, VA",
  workType: "HYBRID",
  salaryMin: 150000,
  salaryMax: 190000,
  currency: "USD",
  visibility: "PRIVATE",
  skills: ["Java", "Kotlin", "Spring Boot"],
  h1bClockStartDate: null,
  linkedinUrl: null,
  workAuthorization: null,
  completeness: 80,
  createdAt: "2026-10-13T00:00:00.000Z",
  updatedAt: "2026-11-10T00:00:00.000Z",
};

function templateHtml(accent: string): string {
  return `<!doctype html><html><head><meta charset="utf-8"><style>body{font-family:sans-serif;color:#0f172a;padding:32px}h1{font-size:26px}h2{color:${accent};border-bottom:2px solid ${accent}}</style></head><body><h1>{{fullName}}</h1><p>{{title}}</p><p>{{email}} · {{location}}</p><h2>Summary</h2><p>{{summary}}</p><h2>Experience</h2>{{experience}}<h2>Education</h2>{{education}}<h2>Skills</h2><p>{{skills}}</p></body></html>`;
}

const TEMPLATES = [
  { id: "modern", name: "Modern", description: "Clean indigo accents.", html: templateHtml("#4f46e5") },
  { id: "classic", name: "Classic", description: "Serif, traditional.", html: templateHtml("#334155") },
  { id: "minimal", name: "Minimal", description: "Whitespace, no decoration.", html: templateHtml("#0f172a") },
];

const PARSED_DOC = {
  id: "doc-1",
  templateId: null,
  title: "Parsed resume",
  state: {
    contact: {
      fullName: "Alex Morgan",
      title: "Senior Software Engineer",
      email: "alex@example.com",
      phone: "555-0100",
      location: "Reston, VA",
      linkedin: "linkedin.com/in/alexmorgan",
    },
    summary: "Java engineer with 8 years of experience.",
    experience: [
      {
        id: "exp-1",
        title: "Senior Engineer",
        company: "Acme",
        startDate: "2021",
        endDate: "Present",
        bullets: ["Shipped the claims API."],
      },
    ],
    education: [],
    skills: ["Java", "Spring Boot"],
  },
};

const COPILOT = {
  options: [
    {
      id: "opt-1",
      angle: "Impact-led",
      bullets: ["Cut p95 latency by [X%] across 12 services."],
      atsKeywords: ["Spring Boot", "Kafka"],
    },
    {
      id: "opt-2",
      angle: "ATS-first",
      bullets: ["Led Spring Boot migration for 12 services."],
      atsKeywords: ["Java", "Microservices"],
    },
    {
      id: "opt-3",
      angle: "Concise",
      bullets: ["Delivered 12-service Spring Boot migration."],
      atsKeywords: ["Spring Boot"],
    },
  ],
};

async function stubBuilder(page: Page) {
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
  await page.route("**/api/matches/me**", (route) =>
    route.fulfill({ status: 200, json: [] }),
  );
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
  // One handler for the whole builder BFF surface, by URL + method.
  await page.route("**/api/resume-builder/**", async (route) => {
    const url = new URL(route.request().url());
    const path = url.pathname.replace("/api/resume-builder", "");
    const method = route.request().method();
    if (path === "/templates" && method === "GET") {
      return route.fulfill({ status: 200, json: TEMPLATES });
    }
    if (path === "/parse" && method === "POST") {
      return route.fulfill({ status: 201, json: PARSED_DOC });
    }
    if (path === "/documents" && method === "POST") {
      return route.fulfill({
        status: 201,
        json: { ...PARSED_DOC, id: "doc-1", templateId: "modern" },
      });
    }
    if (path === "/documents/doc-1" && method === "GET") {
      return route.fulfill({ status: 200, json: { ...PARSED_DOC, templateId: "modern" } });
    }
    if (path === "/documents/doc-1" && method === "PUT") {
      return route.fulfill({ status: 200, json: { ...PARSED_DOC, templateId: "modern" } });
    }
    if (path === "/documents/doc-1/copilot" && method === "POST") {
      return route.fulfill({ status: 200, json: COPILOT });
    }
    if (path === "/documents/doc-1/export" && method === "GET") {
      return route.fulfill({
        status: 200,
        contentType: "application/pdf",
        headers: { "Content-Disposition": 'attachment; filename="alex-morgan.pdf"' },
        body: Buffer.from("%PDF-fake"),
      });
    }
    if (path === "/documents/doc-1/save-to-profile" && method === "POST") {
      return route.fulfill({
        status: 200,
        json: { resumeId: "resume-7", label: "Modern resume" },
      });
    }
    return route.fulfill({ status: 404, json: { message: "not found" } });
  });
}

async function login(page: Page) {
  await stubBuilder(page);
  await page.goto("/login");
  await page.getByLabel("Email").fill("dev@example.com");
  await page.getByLabel("Password").fill("DevPass123!");
  await page.getByRole("button", { name: "Sign in" }).click();
  await expect(
    page.getByRole("heading", { name: /Good (morning|afternoon|evening)/ }),
  ).toBeVisible({ timeout: 15_000 });
}

async function gotoViaNav(page: Page, name: string) {
  const menuButton = page.getByRole("button", { name: "Open menu" });
  if (await menuButton.isVisible()) await menuButton.click();
  await page.getByRole("link", { name, exact: true }).first().click();
}

test.describe("Resume Builder", () => {
  test("entry → upload → template pick → edit → AI options → download", async ({
    page,
  }) => {
    await login(page);
    await gotoViaNav(page, "Resume Builder");

    // Entry: the two cards and the flow subtitle.
    await expect(
      page.getByText(/Upload your resume to start from what you have/),
    ).toBeVisible();
    await expect(
      page.getByRole("button", { name: "Upload a resume" }),
    ).toBeVisible();
    await expect(page.getByRole("button", { name: "Build fresh" })).toBeVisible();

    // Upload (stubbed parse) lands on the templates screen.
    await page.getByRole("button", { name: "Upload a resume" }).click();
    await page
      .getByTestId("builder-file-input")
      .setInputFiles({
        name: "cv.pdf",
        mimeType: "application/pdf",
        buffer: Buffer.from("%PDF-fake"),
      });
    await expect(page.getByText("Choose a template")).toBeVisible();
    await expect(page.getByRole("heading", { name: "Modern" })).toBeVisible();
    await expect(page.getByRole("heading", { name: "Classic" })).toBeVisible();
    await expect(page.getByRole("heading", { name: "Minimal" })).toBeVisible();

    // Template pick → editor, prefilled from the parsed resume.
    await page.getByRole("button", { name: "Use Modern" }).click();
    await expect(page.getByLabel("Full name")).toBeVisible();
    await expect(page.getByLabel("Full name")).toHaveValue("Alex Morgan");
    await expect(page).toHaveURL(/documentId=doc-1/);

    // Edit a field → the live preview re-renders on the keystroke.
    await page
      .getByRole("textbox", { name: "Summary" })
      .fill("Java engineer, now leading AI work.");
    const previewSrc = await page
      .getByTestId("resume-preview")
      .getAttribute("srcDoc");
    expect(previewSrc).toContain("Java engineer, now leading AI work.");

    // Improve with AI → 3 option cards; applying one patches the block
    // and highlights the [X%] placeholder.
    await page.getByRole("button", { name: "Improve summary with AI" }).click();
    await expect(page.getByTestId("copilot-panel")).toBeVisible();
    await expect(page.getByText("Impact-led")).toBeVisible();
    await expect(page.getByText("ATS-first")).toBeVisible();
    await expect(page.getByText("Concise")).toBeVisible();
    await page.getByRole("button", { name: "Apply this option" }).first().click();
    await expect(page.getByText("Applied")).toBeVisible();
    await expect(page.getByRole("textbox", { name: "Summary" })).toHaveValue(
      "Cut p95 latency by [X%] across 12 services.",
    );
    await expect(page.getByText(/still need a real value/)).toBeVisible();

    // Download + save-to-profile are present; the PDF download fires.
    await expect(page.getByRole("button", { name: "Download PDF" })).toBeVisible();
    await expect(page.getByRole("button", { name: "Download DOCX" })).toBeVisible();
    const download = page.waitForEvent("download");
    await page.getByRole("button", { name: "Download PDF" }).click();
    expect((await download).suggestedFilename()).toBe("alex-morgan.pdf");

    await page.getByRole("button", { name: "Save to profile" }).click();
    // Scoped to <main>: the toast shows the same text outside main.
    await expect(
      page.getByRole("main").getByText(/Saved as resume resume-7/),
    ).toBeVisible();
    await expect(page.getByRole("link", { name: "View in Resumes" })).toHaveAttribute(
      "href",
      "/resumes",
    );
  });

  test("build fresh → templates → editor, 390px without horizontal overflow", async ({
    page,
  }) => {
    await page.setViewportSize({ width: 390, height: 844 });
    await login(page);
    await gotoViaNav(page, "Resume Builder");

    await page.getByRole("button", { name: "Build fresh" }).click();
    await expect(page.getByText("Choose a template")).toBeVisible();
    await page.getByRole("button", { name: "Use Minimal" }).click();
    await expect(page.getByLabel("Full name")).toBeVisible();
    await page.getByLabel("Full name").fill("Alex Morgan");
    const previewSrc = await page
      .getByTestId("resume-preview")
      .getAttribute("srcDoc");
    expect(previewSrc).toContain("Alex Morgan");
    await expect(page.getByRole("button", { name: "Download PDF" })).toBeVisible();

    const overflow = await page.evaluate(
      () =>
        document.documentElement.scrollWidth -
        document.documentElement.clientWidth,
    );
    expect(overflow).toBe(0);
  });
});
