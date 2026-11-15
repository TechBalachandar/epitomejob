package com.epitomejob.common.constants

/**
 * Single home for every literal that more than one class needs, or that must
 * never drift between two spellings (statuses, claims, headers, messages).
 * Rule: if a string appears in two places, it lives here. Annotation values
 * may reference these constants directly — they are compile-time constants.
 *
 * Nested `object`s with `const val` compile to `public static final` fields,
 * so Java callers keep using `AppConstants.Http.REQUEST_ID_HEADER` etc.
 * unchanged (including in annotations such as `@ConditionalOnProperty` and
 * `@RequestHeader`). The `List` constants are `@JvmField` so Java callers
 * keep reading them as static fields (`AppConstants.Job.SOURCES.contains(...)`).
 */
object AppConstants {

  /** HTTP / tracing. */
  object Http {
    const val REQUEST_ID_HEADER = "X-Request-Id"
    const val TRACE_ID_MDC_KEY = "traceId"
    const val AUTHORIZATION_HEADER = "Authorization"
    const val BEARER_PREFIX = "Bearer "
  }

  /** Security: JWT claims, roles, token rules. */
  object Security {
    const val CLAIM_EMAIL = "email"
    const val CLAIM_ROLE = "role"
    const val CLAIM_TYPE = "type"
    const val TOKEN_TYPE_REFRESH = "refresh"
    const val ROLE_PREFIX = "ROLE_"
    const val MIN_JWT_SECRET_LENGTH = 32
    const val BCRYPT_COST = 12
  }

  /** Identity: user lifecycle. */
  object User {
    const val STATUS_ACTIVE = "ACTIVE"
    const val MIN_PASSWORD_LENGTH = 8
  }

  /** Approval gate (M7): statuses and expiry. */
  object Approval {
    const val STATUS_PENDING = "PENDING"
    const val STATUS_APPROVED = "APPROVED"
    const val STATUS_REJECTED = "REJECTED"
    /** Apply-flow action (W2 contract §6): documentation only — the backend never executes it. */
    const val ACTION_APPLY_JOB = "APPLY_JOB"
    /** Profile-update action (W3 contract §3): executed by the web on approval via PUT /profiles/me. */
    const val ACTION_UPDATE_PROFILE = "UPDATE_PROFILE"
    /** Draft-application action (W3 contract §3): the draft text lives in the payload; approval is the audit record. */
    const val ACTION_DRAFT_APPLICATION = "DRAFT_APPLICATION"
    /** Approvals expire 24h after they are proposed (signed, idempotent). */
    const val EXPIRY_HOURS = 24L
    const val SSE_EVENT_PENDING = "pending"
    const val SSE_TIMEOUT_MS = 60_000L
  }

  /** Resume storage (M4). */
  object Resume {
    const val STATUS_UPLOADED = "UPLOADED"
    const val STATUS_READY = "READY"
    const val STATUS_FAILED = "FAILED"
    const val PDF_EXTENSION = ".pdf"
    const val PDF_CONTENT_TYPE = "application/pdf"
    const val DOC_EXTENSION = ".doc"
    const val DOC_CONTENT_TYPE = "application/msword"
    const val DOCX_EXTENSION = ".docx"
    const val DOCX_CONTENT_TYPE =
      "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    const val MAX_FILE_SIZE_BYTES = 10L * 1024 * 1024
    const val MAX_PARSED_TEXT_CHARS = 20_000
    /** Resume label (W6 contract §2b): optional, trimmed, 1–80 chars. */
    const val MAX_LABEL_LENGTH = 80
  }

  /** Profile completeness (M3): each filled field is worth 20 points. */
  object Profile {
    const val COMPLETENESS_POINTS_PER_FIELD = 20
    const val DEFAULT_CURRENCY = "USD"
    const val DEFAULT_VISIBILITY = "PRIVATE"
    const val VISIBILITY_PUBLIC = "PUBLIC"
    const val VISIBILITY_PRIVATE = "PRIVATE"
    /** LinkedIn URL (W6 contract §2e): max 300 chars, must start with http(s). */
    const val MAX_LINKEDIN_URL_LENGTH = 300
    /** Work authorisation values (W6 contract §2e). */
    const val WORK_AUTH_US_CITIZEN = "US_CITIZEN"
    const val WORK_AUTH_GREEN_CARD = "GREEN_CARD"
    const val WORK_AUTH_EAD = "EAD"
    const val WORK_AUTH_H1B = "H1B"
    const val WORK_AUTH_OTHER = "OTHER"
    @JvmField
    val WORK_AUTHORIZATIONS: List<String> = listOf(
      WORK_AUTH_US_CITIZEN, WORK_AUTH_GREEN_CARD, WORK_AUTH_EAD, WORK_AUTH_H1B,
      WORK_AUTH_OTHER
    )
  }

  /** Jobs (M5, W2): roles, work types, search limits. */
  object Job {
    const val ROLE_BACKEND = "Backend"
    const val ROLE_AI = "AI"
    const val ROLE_ANDROID = "Android"
    const val ROLE_PLATFORM = "Platform"
    const val ROLE_ML = "ML"
    const val ROLE_GENERAL = "General"
    const val WORK_TYPE_REMOTE = "Remote"
    const val WORK_TYPE_HYBRID = "Hybrid"
    const val WORK_TYPE_ONSITE = "Onsite"
    const val DEFAULT_CURRENCY = "USD"
    const val DEFAULT_PAGE_SIZE = 20
    const val MAX_PAGE_SIZE = 50
    /** Skills / keywords are stored as one TEXT column, comma-separated. */
    const val SKILLS_SEPARATOR = ","
    /** Job sources (W6 contract §2d): where the posting was aggregated from. */
    const val SOURCE_LINKEDIN = "LINKEDIN"
    const val SOURCE_INDEED = "INDEED"
    const val SOURCE_MONSTER = "MONSTER"
    const val SOURCE_NAUKRI = "NAUKRI"
    const val SOURCE_COMPANY = "COMPANY"
    const val SOURCE_SEED = "SEED"
    /** Live Adzuna API feed (replaces the W2 stub). Uppercase like the other
     * sources; Job.setSource validates against SOURCES, and the source
     * filter is case-insensitive. */
    const val SOURCE_ADZUNA = "ADZUNA"
    @JvmField
    val SOURCES: List<String> = listOf(
      SOURCE_LINKEDIN, SOURCE_INDEED, SOURCE_MONSTER, SOURCE_NAUKRI,
      SOURCE_COMPANY, SOURCE_SEED, SOURCE_ADZUNA
    )
  }

  /** Matching (M6, W2 contract §4): deterministic weights, total 100. */
  object Matching {
    const val SKILLS_MAX = 50
    const val LOCATION_MAX = 10
    const val WORK_TYPE_MAX = 10
    const val WORK_TYPE_PARTIAL = 5
    const val SALARY_MAX = 15
    const val SALARY_PARTIAL = 7
    const val SPONSORSHIP_MAX = 15
    const val MAX_RESULTS = 20
    const val SOURCE_RESUME = "RESUME"
    const val SOURCE_PROFILE = "PROFILE"
    const val WHITESPACE_REGEX = "\\s+"
    const val LOCATION_REMOTE = "Remote"
    const val WORK_TYPE_NOT_SET_REASON = "Work type not set — partial credit"
    const val SALARY_NOT_SET_REASON = "Salary preference not set — partial credit"
    const val LOCATION_MATCH_REASON = "Location match"
    const val WORK_TYPE_MATCH_REASON = "Work type match"
    const val SALARY_MEETS_REASON = "Salary meets your minimum"
    const val SALARY_OVERLAP_REASON = "Salary ranges overlap"
    const val SPONSORSHIP_REASON = "H-1B sponsorship"
  }

  /** Tracker (M9, W2): application-pipeline statuses. */
  object Tracker {
    const val STATUS_SAVED = "SAVED"
    const val STATUS_APPLIED = "APPLIED"
    const val STATUS_INTERVIEW = "INTERVIEW"
    const val STATUS_OFFER = "OFFER"
    const val STATUS_REJECTED = "REJECTED"
  }

  /** Mock interviews (M8, W2 contract §5): rubric + verbatim feedback templates. */
  object Interview {
    const val STATUS_IN_PROGRESS = "IN_PROGRESS"
    const val STATUS_COMPLETED = "COMPLETED"
    const val MAX_SCORE = 10
    const val OVERALL_SCALE = 100
    const val FEEDBACK_THRESHOLD_LOW = 5
    const val FEEDBACK_THRESHOLD_HIGH = 8
    /** Feedback lines are stored as one TEXT column, newline-separated. */
    const val FEEDBACK_SEPARATOR = "\n"
    @JvmField
    val STAR_WORDS: List<String> = listOf(
      "situation", "task", "action", "result", "outcome", "learned"
    )
    const val FEEDBACK_STRUCTURE_MISS =
      "Add structure: walk through Situation → Task → Action → Result."
    const val FEEDBACK_RELEVANCE_MISS_PREFIX =
      "Tie your answer to the question: mention "
    const val FEEDBACK_SPECIFICITY_MISS =
      "Add a concrete number or outcome (e.g. users impacted, latency reduced)."
    const val FEEDBACK_CONCISENESS_MISS =
      "Aim for 40–200 words — shorter and sharper."
    const val FEEDBACK_STRUCTURE_GOOD = "Clear structure — easy to follow."
    const val FEEDBACK_RELEVANCE_GOOD = "Strongly relevant to the question."
    const val FEEDBACK_SPECIFICITY_GOOD = "Concrete examples with numbers — nice."
    const val FEEDBACK_CONCISENESS_GOOD = "Well-paced answer."
    const val FEEDBACK_GENERIC =
      "Good start — add one concrete example and tighten the structure."
  }

  /** In-app notifications (M13): types, links, clock-warning bands, wording. */
  object Notification {
    const val TYPE_APPROVAL_PROPOSED = "APPROVAL_PROPOSED"
    const val TYPE_INTERVIEW_CREATED = "INTERVIEW_CREATED"
    const val TYPE_TRACKER_CHANGED = "TRACKER_CHANGED"
    const val TYPE_CLOCK_WARNING = "CLOCK_WARNING"
    const val LINK_APPROVALS = "/approvals"
    const val LINK_INTERVIEWS = "/interviews"
    const val LINK_TRACKER = "/tracker"
    const val LINK_PROFILE = "/profile"
    /** H-1B grace period length, in days (mirrors the W5 web clock). */
    const val CLOCK_TOTAL_DAYS = 60
    const val CLOCK_WARN_14_DAYS = 14
    const val CLOCK_WARN_7_DAYS = 7
    const val CLOCK_ID_14 = "clock-14"
    const val CLOCK_ID_7 = "clock-7"
    const val CLOCK_ID_0 = "clock-0"
    const val CLOCK_ID_EXPIRED = "clock-expired"
    const val APPROVAL_TITLE_PREFIX = "Approval proposed: "
    const val APPROVAL_BODY =
      "Review it in your approvals inbox to approve or reject it."
    const val INTERVIEW_TITLE = "Mock interview started"
    const val INTERVIEW_BODY_FORMAT =
      "Your mock interview for %s is ready — %d questions."
    const val TRACKER_TITLE = "Application updated"
    const val TRACKER_BODY_FORMAT = "%s moved to %s."
    const val CLOCK_TITLE_FORMAT = "H-1B clock: %d days left"
    const val CLOCK_TITLE_ONE_DAY = "H-1B clock: 1 day left"
    const val CLOCK_TITLE_LAST_DAY = "H-1B clock: last day"
    const val CLOCK_TITLE_EXPIRED = "H-1B grace period ended"
    const val CLOCK_BODY_FORMAT = "Your 60-day grace period ends on %s."
    const val CLOCK_BODY_LAST_DAY_FORMAT =
      "Your 60-day grace period ends today (%s)."
    const val CLOCK_BODY_EXPIRED_FORMAT =
      "Your 60-day grace period ended on %s."
  }

  /** Coach proxy (M7, W3 contract §3): Java → agent-brain. */
  object Coach {
    /** Agent-brain chat path (relative to app.agent-brain.url). */
    const val CHAT_PATH = "/chat"
    /** Read timeout matches the agent-brain's Ollama timeout (120s). */
    const val AGENT_BRAIN_READ_TIMEOUT_SECONDS = 120
    const val AGENT_BRAIN_CONNECT_TIMEOUT_SECONDS = 10
  }

  /** Billing (M14, W4 contract §1): plans, providers, sessions, entitlements. */
  object Billing {
    /** Property names for the features.billing block (application.yml). */
    const val FEATURE_PROVIDER = "features.billing.provider"
    const val FEATURE_ENABLED = "features.billing.enabled"
    /** Provider names, as selected by features.billing.provider. */
    const val PROVIDER_STRIPE = "stripe"
    const val PROVIDER_FAKE = "fake"
    /** Plan codes, in seed order (V5__billing.sql). Do not invent others. */
    const val PLAN_SPRINT_PASS = "SPRINT_PASS"
    const val PLAN_LIFETIME = "LIFETIME"
    const val PLAN_HUMAN_REVIEWED = "HUMAN_REVIEWED"
    /** Display order for GET /billing/plans (seed order, not alphabetical). */
    @JvmField
    val SEED_ORDER: List<String> = listOf(
      PLAN_SPRINT_PASS, PLAN_LIFETIME, PLAN_HUMAN_REVIEWED
    )
    /** Checkout session statuses (billing.checkout_sessions). */
    const val SESSION_STATUS_CREATED = "CREATED"
    const val SESSION_STATUS_PAID = "PAID"
    const val SESSION_STATUS_EXPIRED = "EXPIRED"
    const val SESSION_STATUS_CANCELLED = "CANCELLED"
    /** Entitlement statuses (billing.entitlements). */
    const val ENTITLEMENT_STATUS_ACTIVE = "ACTIVE"
    const val ENTITLEMENT_STATUS_EXPIRED = "EXPIRED"
    const val ENTITLEMENT_STATUS_PENDING_MANUAL = "PENDING_MANUAL"
    /** Returned by GET /billing/entitlement/me when the user has no entitlement. */
    const val ENTITLEMENT_STATUS_NONE = "NONE"
    /** Webhook event type on the wire (both providers normalise to this). */
    const val WEBHOOK_EVENT_CHECKOUT_COMPLETED = "CHECKOUT_COMPLETED"
    /** Stripe event type that maps to CHECKOUT_COMPLETED. */
    const val STRIPE_EVENT_CHECKOUT_SESSION_COMPLETED = "checkout.session.completed"
    /** Stripe webhook signature header. */
    const val STRIPE_SIGNATURE_HEADER = "Stripe-Signature"
    /** Fake provider session ids look like fake_<uuid>. */
    const val FAKE_SESSION_PREFIX = "fake_"
    /** Stripe Checkout redirect targets (appended to app.public-url). */
    const val SUCCESS_URL_SUFFIX =
      "/plans?checkout=success&session_id={CHECKOUT_SESSION_ID}"
    const val CANCEL_URL_SUFFIX = "/plans?checkout=cancelled"
    /** JSON field names of the fake provider's webhook payload. */
    const val WEBHOOK_FIELD_TYPE = "type"
    const val WEBHOOK_FIELD_PROVIDER_SESSION_ID = "providerSessionId"
    /** JSON path to the session id in a Stripe event payload: data.object.id. */
    const val WEBHOOK_PAYLOAD_DATA = "data"
    const val WEBHOOK_PAYLOAD_OBJECT = "object"
    const val WEBHOOK_PAYLOAD_ID = "id"
  }

  /** User-facing error messages (never include internals). */
  object Errors {
    const val EMAIL_ALREADY_REGISTERED = "Email already registered"
    const val PASSWORD_TOO_SHORT = "Password must be at least 8 characters"
    const val INVALID_CREDENTIALS = "Invalid credentials"
    const val INVALID_REFRESH_TOKEN = "Invalid refresh token"
    const val APPROVAL_NOT_FOUND = "Approval not found"
    const val NOTIFICATION_NOT_FOUND = "Notification not found"
    const val APPROVAL_EXPIRED = "Approval expired — please re-propose"
    const val NO_FILE_UPLOADED = "No file uploaded"
    const val FILE_TOO_LARGE = "Max 10 MB"
    const val UNSUPPORTED_RESUME_TYPE = "Upload a PDF, DOC, or DOCX file"
    const val NOT_AUTHENTICATED = "Not authenticated"
    const val JOB_NOT_FOUND = "Job not found"
    const val JOB_ID_REQUIRED = "jobId is required"
    const val TRACKER_NOT_FOUND = "Tracker entry not found"
    const val TRACKER_DUPLICATE = "Job already saved in tracker"
    const val INVALID_TRACKER_STATUS = "Invalid tracker status"
    const val INVALID_VISIBILITY = "Invalid visibility"
    const val INVALID_LINKEDIN_URL =
      "LinkedIn URL must start with http:// or https://"
    const val INVALID_WORK_AUTHORIZATION = "Invalid work authorization"
    const val INVALID_RESUME_LABEL = "Label must be 1–80 characters"
    const val INVALID_JOB_SOURCE = "Invalid job source"
    const val INVALID_JOB_SOURCE_URL =
      "Source URL must start with http:// or https://"
    const val SESSION_NOT_FOUND = "Interview session not found"
    const val ANSWER_EMPTY = "Answer must not be empty"
    const val QUESTION_NOT_IN_SESSION = "Question is not part of this session"
    const val INTERNAL_ERROR_MESSAGE = "Something went wrong."
    const val INTERNAL_ERROR_CODE = "INTERNAL_ERROR"
    const val VALIDATION_FAILED_CODE = "VALIDATION_FAILED"
    const val AGENT_UNAVAILABLE_CODE = "AGENT_UNAVAILABLE"
    const val AGENT_UNAVAILABLE_MESSAGE = "Coach agent is not reachable"
    const val RESUME_NOT_FOUND = "Resume not found"
    const val RESUME_NOT_READY_CODE = "RESUME_NOT_READY"
    const val RESUME_NOT_READY = "Resume text is not ready yet"
    const val BILLING_NOT_ENABLED = "Billing is not enabled"
    const val PLAN_NOT_FOUND = "Plan not found"
    const val ALREADY_ENTITLED_CODE = "ALREADY_ENTITLED"
    const val ALREADY_ENTITLED = "You already have an active entitlement for this plan"
    const val BILLING_NOT_CONFIGURED_CODE = "BILLING_NOT_CONFIGURED"
    const val BILLING_NOT_CONFIGURED_MESSAGE =
      "Billing is not connected yet — no payment is taken."
    const val BILLING_UNKNOWN_SESSION = "Unknown checkout session"
    const val BILLING_BAD_SIGNATURE = "Invalid webhook signature"
    const val BILLING_INVALID_WEBHOOK = "Invalid webhook payload"
    const val BILLING_PROVIDER_ERROR = "Billing provider error — please try again"
  }
}
