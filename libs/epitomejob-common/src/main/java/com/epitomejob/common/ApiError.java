package com.epitomejob.common;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Uniform error body, copied field-for-field from the monolith
 * ({@code backend/src/main/java/com/epitomejob/common/exception/ApiError.java}
 * at main b59dc45). Every service — monolith, gateway fallbacks, future
 * splits — returns this shape so clients parse errors one way.
 *
 * @param code    machine-readable error code, e.g. {@code "UNAUTHORIZED"}
 * @param message human-readable message; never leaks internals
 * @param traceId value of the {@code X-Request-Id} header for this request
 *                (may be null when no trace id was present)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(String code, String message, String traceId) {
}
