package com.epitomejob.matching.dto

/** One scored reason, e.g. {"label": "Skills: Java 21, Spring Boot (+38)", "points": 38}. */
data class MatchReasonDto(val label: String, val points: Int)
