package com.epitomejob.approval.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Propose an action. The payload is a JSON string (it is stored as TEXT) —
 * clients send {@code "{\"jobId\":\"job-1\",\"resumeId\":\"...\"}"}, not an object.
 */
public record ProposeRequest(
    @NotBlank String action,
    @NotBlank String payload) {}
