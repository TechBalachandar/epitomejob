package com.epitomejob.profile.dto;

import com.epitomejob.profile.model.Profile;

import java.time.Instant;
import java.util.UUID;

/** What the profile endpoints return. Controllers never expose the entity. */
public record ProfileResponse(
    UUID id,
    UUID userId,
    String headline,
    String summary,
    String location,
    String workType,
    Integer salaryMin,
    Integer salaryMax,
    String currency,
    String visibility,
    int completeness,
    Instant createdAt,
    Instant updatedAt) {

  public static ProfileResponse from(Profile p) {
    return new ProfileResponse(p.getId(), p.getUserId(), p.getHeadline(), p.getSummary(),
        p.getLocation(), p.getWorkType(), p.getSalaryMin(), p.getSalaryMax(),
        p.getCurrency(), p.getVisibility(), p.getCompleteness(),
        p.getCreatedAt(), p.getUpdatedAt());
  }
}
