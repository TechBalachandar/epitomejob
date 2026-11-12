package com.epitomejob.common.constants;

/**
 * Single home for every literal that more than one class needs, or that must
 * never drift between two spellings (statuses, claims, headers, messages).
 * Rule: if a string appears in two places, it lives here. Annotation values
 * may reference these constants directly — they are compile-time constants.
 */
public final class AppConstants {

  private AppConstants() {}

  /** HTTP / tracing. */
  public static final class Http {
    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String TRACE_ID_MDC_KEY = "traceId";
    public static final String AUTHORIZATION_HEADER = "Authorization";
    public static final String BEARER_PREFIX = "Bearer ";
    private Http() {}
  }

  /** Security: JWT claims, roles, token rules. */
  public static final class Security {
    public static final String CLAIM_EMAIL = "email";
    public static final String CLAIM_ROLE = "role";
    public static final String CLAIM_TYPE = "type";
    public static final String TOKEN_TYPE_REFRESH = "refresh";
    public static final String ROLE_PREFIX = "ROLE_";
    public static final int MIN_JWT_SECRET_LENGTH = 32;
    public static final int BCRYPT_COST = 12;
    private Security() {}
  }

  /** Identity: user lifecycle. */
  public static final class User {
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final int MIN_PASSWORD_LENGTH = 8;
    private User() {}
  }

  /** Approval gate (M7): statuses and expiry. */
  public static final class Approval {
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_REJECTED = "REJECTED";
    /** Apply-flow action (W2 contract §6): documentation only — the backend never executes it. */
    public static final String ACTION_APPLY_JOB = "APPLY_JOB";
    /** Profile-update action (W3 contract §3): executed by the web on approval via PUT /profiles/me. */
    public static final String ACTION_UPDATE_PROFILE = "UPDATE_PROFILE";
    /** Draft-application action (W3 contract §3): the draft text lives in the payload; approval is the audit record. */
    public static final String ACTION_DRAFT_APPLICATION = "DRAFT_APPLICATION";
    /** Approvals expire 24h after they are proposed (signed, idempotent). */
    public static final long EXPIRY_HOURS = 24;
    public static final String SSE_EVENT_PENDING = "pending";
    public static final long SSE_TIMEOUT_MS = 60_000L;
    private Approval() {}
  }

  /** Resume storage (M4). */
  public static final class Resume {
    public static final String STATUS_UPLOADED = "UPLOADED";
    public static final String STATUS_READY = "READY";
    public static final String STATUS_FAILED = "FAILED";
    public static final String PDF_EXTENSION = ".pdf";
    public static final String PDF_CONTENT_TYPE = "application/pdf";
    public static final long MAX_FILE_SIZE_BYTES = 10L * 1024 * 1024;
    public static final int MAX_PARSED_TEXT_CHARS = 20_000;
    private Resume() {}
  }

  /** Profile completeness (M3): each filled field is worth 20 points. */
  public static final class Profile {
    public static final int COMPLETENESS_POINTS_PER_FIELD = 20;
    public static final String DEFAULT_CURRENCY = "USD";
    public static final String DEFAULT_VISIBILITY = "PRIVATE";
    private Profile() {}
  }

  /** Jobs (M5, W2): roles, work types, search limits. */
  public static final class Job {
    public static final String ROLE_BACKEND = "Backend";
    public static final String ROLE_AI = "AI";
    public static final String ROLE_ANDROID = "Android";
    public static final String ROLE_PLATFORM = "Platform";
    public static final String ROLE_ML = "ML";
    public static final String ROLE_GENERAL = "General";
    public static final String WORK_TYPE_REMOTE = "Remote";
    public static final String WORK_TYPE_HYBRID = "Hybrid";
    public static final String WORK_TYPE_ONSITE = "Onsite";
    public static final String DEFAULT_CURRENCY = "USD";
    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 50;
    /** Skills / keywords are stored as one TEXT column, comma-separated. */
    public static final String SKILLS_SEPARATOR = ",";
    private Job() {}
  }

  /** Matching (M6, W2 contract §4): deterministic weights, total 100. */
  public static final class Matching {
    public static final int SKILLS_MAX = 50;
    public static final int LOCATION_MAX = 10;
    public static final int WORK_TYPE_MAX = 10;
    public static final int WORK_TYPE_PARTIAL = 5;
    public static final int SALARY_MAX = 15;
    public static final int SALARY_PARTIAL = 7;
    public static final int SPONSORSHIP_MAX = 15;
    public static final int MAX_RESULTS = 20;
    public static final String SOURCE_RESUME = "RESUME";
    public static final String SOURCE_PROFILE = "PROFILE";
    public static final String WHITESPACE_REGEX = "\\s+";
    public static final String LOCATION_REMOTE = "Remote";
    public static final String WORK_TYPE_NOT_SET_REASON = "Work type not set — partial credit";
    public static final String SALARY_NOT_SET_REASON = "Salary preference not set — partial credit";
    public static final String LOCATION_MATCH_REASON = "Location match";
    public static final String WORK_TYPE_MATCH_REASON = "Work type match";
    public static final String SALARY_MEETS_REASON = "Salary meets your minimum";
    public static final String SALARY_OVERLAP_REASON = "Salary ranges overlap";
    public static final String SPONSORSHIP_REASON = "H-1B sponsorship";
    private Matching() {}
  }

  /** Tracker (M9, W2): application-pipeline statuses. */
  public static final class Tracker {
    public static final String STATUS_SAVED = "SAVED";
    public static final String STATUS_APPLIED = "APPLIED";
    public static final String STATUS_INTERVIEW = "INTERVIEW";
    public static final String STATUS_OFFER = "OFFER";
    public static final String STATUS_REJECTED = "REJECTED";
    private Tracker() {}
  }

  /** Mock interviews (M8, W2 contract §5): rubric + verbatim feedback templates. */
  public static final class Interview {
    public static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final int MAX_SCORE = 10;
    public static final int OVERALL_SCALE = 100;
    public static final int FEEDBACK_THRESHOLD_LOW = 5;
    public static final int FEEDBACK_THRESHOLD_HIGH = 8;
    /** Feedback lines are stored as one TEXT column, newline-separated. */
    public static final String FEEDBACK_SEPARATOR = "\n";
    public static final java.util.List<String> STAR_WORDS = java.util.List.of(
        "situation", "task", "action", "result", "outcome", "learned");
    public static final String FEEDBACK_STRUCTURE_MISS =
        "Add structure: walk through Situation → Task → Action → Result.";
    public static final String FEEDBACK_RELEVANCE_MISS_PREFIX =
        "Tie your answer to the question: mention ";
    public static final String FEEDBACK_SPECIFICITY_MISS =
        "Add a concrete number or outcome (e.g. users impacted, latency reduced).";
    public static final String FEEDBACK_CONCISENESS_MISS =
        "Aim for 40–200 words — shorter and sharper.";
    public static final String FEEDBACK_STRUCTURE_GOOD = "Clear structure — easy to follow.";
    public static final String FEEDBACK_RELEVANCE_GOOD = "Strongly relevant to the question.";
    public static final String FEEDBACK_SPECIFICITY_GOOD = "Concrete examples with numbers — nice.";
    public static final String FEEDBACK_CONCISENESS_GOOD = "Well-paced answer.";
    public static final String FEEDBACK_GENERIC =
        "Good start — add one concrete example and tighten the structure.";
    private Interview() {}
  }

  /** Coach proxy (M7, W3 contract §3): Java → agent-brain. */
  public static final class Coach {
    /** Agent-brain chat path (relative to app.agent-brain.url). */
    public static final String CHAT_PATH = "/chat";
    /** Read timeout matches the agent-brain's Ollama timeout (120s). */
    public static final int AGENT_BRAIN_READ_TIMEOUT_SECONDS = 120;
    public static final int AGENT_BRAIN_CONNECT_TIMEOUT_SECONDS = 10;
    private Coach() {}
  }

  /** User-facing error messages (never include internals). */
  public static final class Errors {
    public static final String EMAIL_ALREADY_REGISTERED = "Email already registered";
    public static final String PASSWORD_TOO_SHORT = "Password must be at least 8 characters";
    public static final String INVALID_CREDENTIALS = "Invalid credentials";
    public static final String INVALID_REFRESH_TOKEN = "Invalid refresh token";
    public static final String APPROVAL_NOT_FOUND = "Approval not found";
    public static final String APPROVAL_EXPIRED = "Approval expired — please re-propose";
    public static final String NO_FILE_UPLOADED = "No file uploaded";
    public static final String FILE_TOO_LARGE = "Max 10 MB";
    public static final String PDF_ONLY = "Phase 0 accepts PDF only (DOCX comes in Phase 3)";
    public static final String NOT_AUTHENTICATED = "Not authenticated";
    public static final String JOB_NOT_FOUND = "Job not found";
    public static final String JOB_ID_REQUIRED = "jobId is required";
    public static final String TRACKER_NOT_FOUND = "Tracker entry not found";
    public static final String TRACKER_DUPLICATE = "Job already saved in tracker";
    public static final String INVALID_TRACKER_STATUS = "Invalid tracker status";
    public static final String SESSION_NOT_FOUND = "Interview session not found";
    public static final String ANSWER_EMPTY = "Answer must not be empty";
    public static final String QUESTION_NOT_IN_SESSION = "Question is not part of this session";
    public static final String INTERNAL_ERROR_MESSAGE = "Something went wrong.";
    public static final String INTERNAL_ERROR_CODE = "INTERNAL_ERROR";
    public static final String VALIDATION_FAILED_CODE = "VALIDATION_FAILED";
    public static final String AGENT_UNAVAILABLE_CODE = "AGENT_UNAVAILABLE";
    public static final String AGENT_UNAVAILABLE_MESSAGE = "Coach agent is not reachable";
    public static final String RESUME_NOT_FOUND = "Resume not found";
    public static final String RESUME_NOT_READY_CODE = "RESUME_NOT_READY";
    public static final String RESUME_NOT_READY = "Resume text is not ready yet";
    private Errors() {}
  }
}
