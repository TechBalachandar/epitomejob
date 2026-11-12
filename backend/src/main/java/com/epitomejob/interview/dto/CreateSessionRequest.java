package com.epitomejob.interview.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** POST /interviews/sessions body. */
public record CreateSessionRequest(
    @NotNull UUID jobId) {
}
