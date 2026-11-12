package com.epitomejob.coach.dto;

import jakarta.validation.constraints.NotBlank;

/** POST /coach/chat request (W3 contract §3). */
public record CoachChatRequest(@NotBlank String message) {}
