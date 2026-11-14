package com.epitomejob.resume.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Rename a resume's label only (W6 contract §2b). Label is required, 1–80 chars. */
public record RenameResumeRequest(
    @NotBlank @Size(max = 80) String label) {}
