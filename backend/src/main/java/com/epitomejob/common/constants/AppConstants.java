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
    public static final String INTERNAL_ERROR_MESSAGE = "Something went wrong.";
    public static final String INTERNAL_ERROR_CODE = "INTERNAL_ERROR";
    public static final String VALIDATION_FAILED_CODE = "VALIDATION_FAILED";
    private Errors() {}
  }
}
