// Mock data for the pages that have no backend endpoint yet in Phase 0
// (jobs, matches, interviews, tracker, coach, dashboard activity).
// Realistic, static content — no lorem ipsum. W2/W3 replace these with
// real Job Discovery / Matching / Interview data.
import type {
  Interview,
  Job,
  Match,
  TrackerCard,
} from "./types";

export const MOCK_JOBS: Job[] = [
  {
    id: "job-1",
    title: "Senior Software Engineer, Backend (Java)",
    company: "Northwind Health Systems",
    location: "Reston, VA",
    workMode: "Hybrid",
    salaryMin: 145000,
    salaryMax: 175000,
    sponsorsVisa: true,
    postedDaysAgo: 2,
    summary:
      "Build and own Spring Boot microservices for claims processing. Java 21, Kafka, Postgres, Azure. H-1B sponsorship available.",
    skills: ["Java 21", "Spring Boot", "Kafka", "PostgreSQL", "Azure"],
  },
  {
    id: "job-2",
    title: "Agentic AI Engineer",
    company: "Meridian Care Technologies",
    location: "Remote (US)",
    workMode: "Remote",
    salaryMin: 160000,
    salaryMax: 195000,
    sponsorsVisa: true,
    postedDaysAgo: 1,
    summary:
      "Design multi-agent workflows for member operations with LangGraph and Python. RAG over policy documents, human-in-the-loop approvals.",
    skills: ["Python", "LangGraph", "RAG", "MCP", "Azure OpenAI"],
  },
  {
    id: "job-3",
    title: "Staff Platform Engineer",
    company: "Bluepeak Financial",
    location: "Arlington, VA",
    workMode: "Hybrid",
    salaryMin: 170000,
    salaryMax: 210000,
    sponsorsVisa: false,
    postedDaysAgo: 4,
    summary:
      "Own the internal developer platform: Kubernetes, CI/CD, service mesh. Go and Java services, 200+ engineers as your customers.",
    skills: ["Kubernetes", "Go", "Java", "Terraform", "CI/CD"],
  },
  {
    id: "job-4",
    title: "Senior AI Engineer, Healthcare",
    company: "Deloitte",
    location: "Rosslyn, VA",
    workMode: "Hybrid",
    salaryMin: 150000,
    salaryMax: 190000,
    sponsorsVisa: true,
    postedDaysAgo: 3,
    summary:
      "Ship LLM-powered healthcare products for federal and commercial clients. Evals, guardrails, HIPAA-aware design.",
    skills: ["Python", "LLMs", "LangChain", "Evals", "HIPAA"],
  },
  {
    id: "job-5",
    title: "Android Engineer (Kotlin)",
    company: "Signal Mobile Banking",
    location: "Remote (US)",
    workMode: "Remote",
    salaryMin: 135000,
    salaryMax: 165000,
    sponsorsVisa: false,
    postedDaysAgo: 6,
    summary:
      "Lead Android development for a banking app used by 2M members. Kotlin, Compose, clean architecture, CI on every PR.",
    skills: ["Kotlin", "Jetpack Compose", "Hilt", "Coroutines"],
  },
  {
    id: "job-6",
    title: "Backend Engineer, Integrations",
    company: "Harbor Credit Union",
    location: "Vienna, VA",
    workMode: "Onsite",
    salaryMin: 125000,
    salaryMax: 150000,
    sponsorsVisa: true,
    postedDaysAgo: 5,
    summary:
      "Build core banking APIs for online and mobile banking. Spring Boot, ServiceNow-driven delivery, MuleSoft experience a plus.",
    skills: ["Java", "Spring Boot", "REST", "MuleSoft", "Oracle"],
  },
  {
    id: "job-7",
    title: "Machine Learning Engineer, Recommendations",
    company: "Cartwheel Commerce",
    location: "Remote (US)",
    workMode: "Remote",
    salaryMin: 155000,
    salaryMax: 185000,
    sponsorsVisa: true,
    postedDaysAgo: 2,
    summary:
      "Own ranking models end to end: features, training, evals, serving. Python, PyTorch, feature store, A/B testing.",
    skills: ["Python", "PyTorch", "Recommenders", "A/B Testing"],
  },
  {
    id: "job-8",
    title: "Solutions Architect, Cloud",
    company: "Summit Federal Consulting",
    location: "Washington, DC",
    workMode: "Hybrid",
    salaryMin: 150000,
    salaryMax: 180000,
    sponsorsVisa: false,
    postedDaysAgo: 7,
    summary:
      "Design Azure landing zones and API platforms for federal clients. App Services, APIM, Entra ID, Key Vault.",
    skills: ["Azure", "APIM", "Entra ID", "Architecture", "Terraform"],
  },
  {
    id: "job-9",
    title: "Senior Full-Stack Engineer (React + Java)",
    company: "Lumen Health Plans",
    location: "Remote (US)",
    workMode: "Remote",
    salaryMin: 140000,
    salaryMax: 170000,
    sponsorsVisa: true,
    postedDaysAgo: 1,
    summary:
      "Build member-facing portals in React 19 and the Spring Boot APIs behind them. Small team, big ownership.",
    skills: ["React", "TypeScript", "Java", "Spring Boot", "Postgres"],
  },
  {
    id: "job-10",
    title: "Data Engineer, EDI Pipelines",
    company: "Northwind Health Systems",
    location: "Reston, VA",
    workMode: "Hybrid",
    salaryMin: 130000,
    salaryMax: 160000,
    sponsorsVisa: true,
    postedDaysAgo: 8,
    summary:
      "Build reliable EDI 837/835 pipelines for claims. Python, Spark, Airflow, data quality checks on every load.",
    skills: ["Python", "Spark", "Airflow", "EDI", "SQL"],
  },
];

export const MOCK_MATCHES: Match[] = [
  {
    jobId: "job-2",
    score: 92,
    reasons: [
      "Java + Spring Boot background maps directly to the agent platform services",
      "Hands-on MCP server and RAG work in your own projects",
      "Healthcare domain experience from claims-adjacent work",
    ],
    gaps: ["Production LangGraph experience", "Formal LLM evals (LangSmith/Arize)"],
  },
  {
    jobId: "job-1",
    score: 88,
    reasons: [
      "17+ years of Java, most of it Spring Boot microservices",
      "Core banking API experience transfers to claims processing",
      "Azure and Kafka are both in your stack",
    ],
    gaps: ["Recent US healthcare EDI work"],
  },
  {
    jobId: "job-4",
    score: 84,
    reasons: [
      "Agentic AI learning track matches the role's core ask",
      "API platform and OAuth2/OIDC experience",
      "Consulting-friendly background: analyst + delivery roles",
    ],
    gaps: ["Client-facing consulting experience", "HIPAA project work"],
  },
  {
    jobId: "job-9",
    score: 81,
    reasons: [
      "React + TypeScript from the EpitomeJob web app",
      "Java/Spring Boot backend depth",
      "Member-portal domain overlap with banking apps",
    ],
    gaps: ["Recent commercial React work"],
  },
];

export const MOCK_INTERVIEWS: Interview[] = [
  {
    id: "int-1",
    company: "Meridian Care Technologies",
    role: "Agentic AI Engineer",
    date: "2026-11-14T10:00:00",
    status: "UPCOMING",
    score: null,
    feedback: null,
  },
  {
    id: "int-2",
    company: "Northwind Health Systems",
    role: "Senior Software Engineer, Backend (Java)",
    date: "2026-11-10T14:00:00",
    status: "COMPLETED",
    score: 82,
    feedback:
      "Strong system-design answer on the claims pipeline. Tighten the story on Kafka ordering guarantees; the STAR examples landed well.",
  },
  {
    id: "int-3",
    company: "Deloitte",
    role: "Senior AI Engineer, Healthcare",
    date: "2026-11-06T09:30:00",
    status: "COMPLETED",
    score: 74,
    feedback:
      "Good instincts on evals and guardrails. The HIPAA answer was high-level — prepare one concrete de-identification example.",
  },
];

export const MOCK_TRACKER: TrackerCard[] = [
  {
    id: "trk-1",
    jobId: "job-2",
    company: "Meridian Care Technologies",
    title: "Agentic AI Engineer",
    status: "INTERVIEW",
    appliedAt: "2026-11-05",
    note: "Recruiter screen done; technical round Nov 14.",
  },
  {
    id: "trk-2",
    jobId: "job-1",
    company: "Northwind Health Systems",
    title: "Senior Software Engineer, Backend (Java)",
    status: "APPLIED",
    appliedAt: "2026-11-08",
    note: "Applied with tailored resume v3.",
  },
  {
    id: "trk-3",
    jobId: "job-4",
    company: "Deloitte",
    title: "Senior AI Engineer, Healthcare",
    status: "APPLIED",
    appliedAt: "2026-11-09",
    note: "Referral from a former colleague.",
  },
  {
    id: "trk-4",
    jobId: "job-9",
    company: "Lumen Health Plans",
    title: "Senior Full-Stack Engineer (React + Java)",
    status: "SAVED",
    appliedAt: "2026-11-11",
    note: "Strong match; apply after resume polish.",
  },
  {
    id: "trk-5",
    jobId: "job-6",
    company: "Harbor Credit Union",
    title: "Backend Engineer, Integrations",
    status: "OFFER",
    appliedAt: "2026-10-28",
    note: "Offer in hand; deciding by Nov 20.",
  },
  {
    id: "trk-6",
    jobId: "job-3",
    company: "Bluepeak Financial",
    title: "Staff Platform Engineer",
    status: "REJECTED",
    appliedAt: "2026-10-20",
    note: "Passed on Go depth; asked to reapply in 6 months.",
  },
];

export const MOCK_ACTIVITY = [
  { id: "act-1", text: "Resume v3 uploaded and parsed — status READY", at: "Nov 11, 4:12 PM" },
  { id: "act-2", text: "Applied to Agentic AI Engineer at Meridian Care", at: "Nov 10, 9:40 AM" },
  { id: "act-3", text: "Coach suggested 3 resume tweaks for the Deloitte role", at: "Nov 9, 6:05 PM" },
  { id: "act-4", text: "Profile completeness reached 80%", at: "Nov 9, 11:22 AM" },
];

export const MOCK_QUESTIONS = [
  "Walk me through how you would design a multi-agent system that drafts job applications but never sends one without human approval.",
  "A matching service returns a 92% score for a role you are not qualified for. How do you find and fix the scoring bug?",
  "Tell me about a time you had to explain a technical trade-off to a non-technical stakeholder.",
];
