package com.epitomejob.releasecontrol.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateReleaseRequest(
    @NotBlank @Pattern(regexp = "^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$") String repository,
    @Positive long pullRequestNumber,
    @NotBlank @Pattern(regexp = "^[0-9a-fA-F]{40,64}$") String sourceSha,
    @NotBlank @Size(max = 128) String idempotencyKey
) {}
