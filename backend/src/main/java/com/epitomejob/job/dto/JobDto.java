package com.epitomejob.job.dto;

import com.epitomejob.job.model.Job;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Job shape, exact field names per the W2 contract §1. */
public record JobDto(
    UUID id,
    String title,
    String company,
    String location,
    String workType,
    Integer salaryMin,
    Integer salaryMax,
    String currency,
    boolean sponsorsH1b,
    List<String> skills,
    String summary,
    String role,
    Instant postedAt,
    String source,
    String sourceUrl) {

  public static JobDto from(Job job) {
    return new JobDto(job.getId(), job.getTitle(), job.getCompany(), job.getLocation(),
        job.getWorkType(), job.getSalaryMin(), job.getSalaryMax(), job.getCurrency(),
        job.isSponsorsH1b(), job.getSkills(), job.getSummary(), job.getRole(),
        job.getPostedAt(), job.getSource(), job.getSourceUrl());
  }
}
