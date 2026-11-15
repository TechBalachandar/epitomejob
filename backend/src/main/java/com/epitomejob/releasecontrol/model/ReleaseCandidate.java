package com.epitomejob.releasecontrol.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "release_candidates", schema = "release_control",
    uniqueConstraints = @UniqueConstraint(name = "uq_release_idempotency",
        columnNames = {"created_by", "idempotency_key"}))
public class ReleaseCandidate {
  @Id
  private UUID id;

  @Column(nullable = false, length = 255)
  private String repository;

  @Column(name = "pull_request_number", nullable = false)
  private long pullRequestNumber;

  @Column(name = "source_sha", nullable = false, length = 64)
  private String sourceSha;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private ReleaseState state;

  @Column(name = "created_by", nullable = false)
  private UUID createdBy;

  @Column(name = "idempotency_key", nullable = false, length = 128)
  private String idempotencyKey;

  @Version
  private long version;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected ReleaseCandidate() {}

  public ReleaseCandidate(String repository, long pullRequestNumber, String sourceSha,
                          UUID createdBy, String idempotencyKey) {
    this.id = UUID.randomUUID();
    this.repository = repository;
    this.pullRequestNumber = pullRequestNumber;
    this.sourceSha = sourceSha;
    this.createdBy = createdBy;
    this.idempotencyKey = idempotencyKey;
    this.state = ReleaseState.CREATED;
    this.createdAt = Instant.now();
    this.updatedAt = this.createdAt;
  }

  @PreUpdate
  void touch() { this.updatedAt = Instant.now(); }

  public UUID getId() { return id; }
  public String getRepository() { return repository; }
  public long getPullRequestNumber() { return pullRequestNumber; }
  public String getSourceSha() { return sourceSha; }
  public ReleaseState getState() { return state; }
  public UUID getCreatedBy() { return createdBy; }
  public Instant getCreatedAt() { return createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
}
