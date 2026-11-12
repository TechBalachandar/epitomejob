package com.epitomejob.matching.dto;

import com.epitomejob.job.dto.JobDto;

import java.util.List;

/** Match shape, exact field names per the W2 contract §3. */
public record MatchDto(
    JobDto job,
    int score,
    List<MatchReasonDto> reasons,
    List<String> gaps,
    String source) {
}
