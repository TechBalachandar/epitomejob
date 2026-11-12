package com.epitomejob.interview.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** POST /interviews/sessions/{id}/answers body. */
public record AnswerRequest(
    @NotNull UUID questionId,
    @NotBlank String answerText) {
}
