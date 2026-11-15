package com.epitomejob.common

/**
 * Header / attribute names shared by every EpitomeJob service. Values match
 * the monolith's {@code AppConstants} (verified at main b59dc45) so the
 * gateway and the monolith never drift on spelling.
 *
 * Kotlin `object` with `const val`: each constant compiles to a
 * `public static final` field, so Java callers keep using
 * {@code TraceConstants.REQUEST_ID_HEADER} unchanged.
 */
object TraceConstants {

  /** Request/response header carrying the end-to-end trace id. */
  const val REQUEST_ID_HEADER = "X-Request-Id"

  /** MDC key the monolith logs the trace id under. */
  const val TRACE_ID_MDC_KEY = "traceId"

  /** ServerWebExchange attribute the trace id is stored under. */
  const val TRACE_ID_ATTRIBUTE = "traceId"

  /** Authorization header name. */
  const val AUTHORIZATION_HEADER = "Authorization"

  /** Prefix (including the trailing space) of a Bearer token. */
  const val BEARER_PREFIX = "Bearer "

  /**
   * Downstream headers the gateway sets from the validated JWT. The gateway
   * strips any client-supplied values first, so downstream services can
   * trust these — the gateway is the only entry point.
   */
  const val USER_ID_HEADER = "X-User-Id"
  const val USER_EMAIL_HEADER = "X-User-Email"
  const val USER_ROLE_HEADER = "X-User-Role"
}
