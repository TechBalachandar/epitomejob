package com.epitomejob.matching.dto;

import com.epitomejob.job.dto.JobDto;

import java.util.List;

/** One scored reason, e.g. {"label": "Skills: Java 21, Spring Boot (+38)", "points": 38}. */
public record MatchReasonDto(String label, int points) {
}
