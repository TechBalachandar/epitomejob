package com.epitomejob.profile.dto;

import com.epitomejob.profile.model.Profile;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
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
    List<String> skills,
    LocalDate h1bClockStartDate,
    int completeness,
    Instant createdAt,
    Instant updatedAt) {

  public static ProfileResponse from(Profile p) {
    List<String> skills = p.getSkills() == null ? List.of()
        : p.getSkills().stream().sorted().toList();
    return new ProfileResponse(p.getId(), p.getUserId(), p.getHeadline(), p.getSummary(),
        p.getLocation(), p.getWorkType(), p.getSalaryMin(), p.getSalaryMax(),
        p.getCurrency(), p.getVisibility(), skills, p.getH1bClockStartDate(),
        p.getCompleteness(), p.getCreatedAt(), p.getUpdatedAt());
  }
}
