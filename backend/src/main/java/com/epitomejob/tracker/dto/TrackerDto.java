package com.epitomejob.tracker.dto;

import com.epitomejob.job.model.Job;
import com.epitomejob.tracker.model.TrackerEntry;

import java.time.Instant;
import java.util.UUID;

/** Tracker shape, exact field names per the W2 contract §3. */
public record TrackerDto(
    UUID id,
    UUID jobId,
    String title,
    String company,
    UUID resumeId,
    String status,
    String notes,
    Instant createdAt,
    Instant updatedAt) {

  public static TrackerDto from(TrackerEntry entry, Job job) {
    return new TrackerDto(entry.getId(), entry.getJobId(), job.getTitle(), job.getCompany(),
        entry.getResumeId(), entry.getStatus(), entry.getNotes(),
        entry.getCreatedAt(), entry.getUpdatedAt());
  }
}
