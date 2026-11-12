package com.epitomejob.interview.model;

import com.epitomejob.common.constants.AppConstants;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sessions", schema = "interview")
public class InterviewSession {

  @Id
  private UUID id = UUID.randomUUID();

  @Column(nullable = false)
  private UUID userId;

  @Column(nullable = false)
  private UUID jobId;

  @Column(nullable = false)
  private String status = AppConstants.Interview.STATUS_IN_PROGRESS;

  @Column(nullable = false)
  private Instant createdAt = Instant.now();

  private Instant completedAt;

  protected InterviewSession() {}

  public InterviewSession(UUID userId, UUID jobId) {
    this.userId = userId;
    this.jobId = jobId;
  }

  public UUID getId() { return id; }
  public UUID getUserId() { return userId; }
  public UUID getJobId() { return jobId; }
  public String getStatus() { return status; }
  public void setStatus(String s) { this.status = s; }
  public Instant getCreatedAt() { return createdAt; }
  public Instant getCompletedAt() { return completedAt; }
  public void setCompletedAt(Instant t) { this.completedAt = t; }
}
