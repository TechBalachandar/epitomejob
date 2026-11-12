package com.epitomejob.tracker.model;

import com.epitomejob.common.constants.AppConstants;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "entries", schema = "tracker")
public class TrackerEntry {

  @Id
  private UUID id = UUID.randomUUID();

  @Column(nullable = false)
  private UUID userId;

  @Column(nullable = false)
  private UUID jobId;

  private UUID resumeId;

  @Column(nullable = false)
  private String status = AppConstants.Tracker.STATUS_SAVED;

  @Column(columnDefinition = "TEXT")
  private String notes;

  @Column(nullable = false)
  private Instant createdAt = Instant.now();

  @Column(nullable = false)
  private Instant updatedAt = Instant.now();

  protected TrackerEntry() {}

  public TrackerEntry(UUID userId, UUID jobId, UUID resumeId) {
    this.userId = userId;
    this.jobId = jobId;
    this.resumeId = resumeId;
  }

  public UUID getId() { return id; }
  public UUID getUserId() { return userId; }
  public UUID getJobId() { return jobId; }
  public UUID getResumeId() { return resumeId; }
  public String getStatus() { return status; }
  public void setStatus(String s) { this.status = s; }
  public String getNotes() { return notes; }
  public void setNotes(String n) { this.notes = n; }
  public Instant getCreatedAt() { return createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }

  public void touch() { this.updatedAt = Instant.now(); }
}
