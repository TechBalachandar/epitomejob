package com.epitomejob.tracker.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** POST /tracker body. resumeId is optional. */
public record TrackerCreateRequest(
    @NotNull UUID jobId,
    UUID resumeId) {
}
