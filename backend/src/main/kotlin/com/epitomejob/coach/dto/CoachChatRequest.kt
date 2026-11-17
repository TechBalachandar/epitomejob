package com.epitomejob.coach.dto

import jakarta.validation.constraints.NotBlank

/** POST /coach/chat request (W3 contract §3). */
data class CoachChatRequest(@field:NotBlank val message: String)
