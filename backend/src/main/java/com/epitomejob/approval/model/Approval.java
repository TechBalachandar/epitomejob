package com.epitomejob.approval.model;

import com.epitomejob.common.constants.AppConstants;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "approvals", schema = "coach")
public class Approval {

  @Id
  private UUID id = UUID.randomUUID();

  @Column(nullable = false)
  private UUID userId;

  @Column(nullable = false)
  private String action;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String payload;

  /** SHA-256 of the approval token — the plain token is never stored. */
  @Column(nullable = false)
  private String tokenHash;

  @Column(nullable = false)
  private String status = AppConstants.Approval.STATUS_PENDING;

  @Column(nullable = false)
  private Instant expiresAt;

  private Instant decidedAt;

  protected Approval() {}

  public Approval(UUID userId, String action, String payload, String tokenHash, Instant expiresAt) {
    this.userId = userId;
    this.action = action;
    this.payload = payload;
    this.tokenHash = tokenHash;
    this.expiresAt = expiresAt;
  }

  public UUID getId() { return id; }
  public UUID getUserId() { return userId; }
  public String getAction() { return action; }
  public String getPayload() { return payload; }
  public String getStatus() { return status; }
  public void setStatus(String s) { this.status = s; }
  public Instant getExpiresAt() { return expiresAt; }
  public Instant getDecidedAt() { return decidedAt; }
  public void setDecidedAt(Instant t) { this.decidedAt = t; }
}
