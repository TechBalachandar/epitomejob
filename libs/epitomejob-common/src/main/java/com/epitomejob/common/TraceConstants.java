package com.epitomejob.common;

/**
 * Header / attribute names shared by every EpitomeJob service. Values match
 * the monolith's {@code AppConstants} (verified at main b59dc45) so the
 * gateway and the monolith never drift on spelling.
 */
public final class TraceConstants {

  private TraceConstants() {
  }

  /** Request/response header carrying the end-to-end trace id. */
  public static final String REQUEST_ID_HEADER = "X-Request-Id";

  /** MDC key the monolith logs the trace id under. */
  public static final String TRACE_ID_MDC_KEY = "traceId";

  /** ServerWebExchange attribute the trace id is stored under. */
  public static final String TRACE_ID_ATTRIBUTE = "traceId";

  /** Authorization header name. */
  public static final String AUTHORIZATION_HEADER = "Authorization";

  /** Prefix (including the trailing space) of a Bearer token. */
  public static final String BEARER_PREFIX = "Bearer ";

  /**
   * Downstream headers the gateway sets from the validated JWT. The gateway
   * strips any client-supplied values first, so downstream services can
   * trust these — the gateway is the only entry point.
   */
  public static final String USER_ID_HEADER = "X-User-Id";
  public static final String USER_EMAIL_HEADER = "X-User-Email";
  public static final String USER_ROLE_HEADER = "X-User-Role";
}
