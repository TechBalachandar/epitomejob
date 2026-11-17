package com.epitomejob.common.exception

/**
 * Uniform error body for every API failure. Never carries a stack trace.
 *
 * Distinct from the shared library's `com.epitomejob.common.ApiError`: this
 * is the monolith's own error body. `message` and `traceId` are nullable —
 * a `ResponseStatusException` reason may be absent and the MDC trace id may
 * be unset — where the Java record accepted nulls at runtime.
 */
data class ApiError(val code: String, val message: String?, val traceId: String?)
