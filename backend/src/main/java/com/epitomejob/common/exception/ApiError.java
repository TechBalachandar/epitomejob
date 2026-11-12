package com.epitomejob.common.exception;

/** Uniform error body for every API failure. Never carries a stack trace. */
public record ApiError(String code, String message, String traceId) {}
