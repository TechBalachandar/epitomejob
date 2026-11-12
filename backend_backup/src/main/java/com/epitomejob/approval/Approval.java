package com.epitomejob.approval;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name = "approvals", schema = "coach")
public class Approval {
  @Id private UUID id = UUID.randomUUID();
  @Column(nullable = false) private UUID userId;
  @Column(nullable = false) private String action;
  @Column(nullable = false, columnDefinition = "TEXT") private String payload;
  @Column(nullable = false) private String tokenHash;
  @Column(nullable = false) private String status = "PENDING";
  @Column(nullable = false) private Instant expiresAt;
  private Instant decidedAt;
  protected Approval() {}
  public Approval(UUID userId, String action, String payload, String tokenHash, Instant expiresAt) {
    this.userId = userId; this.action = action; this.payload = payload;
    this.tokenHash = tokenHash; this.expiresAt = expiresAt;
  }
  public UUID getId() { return id; }
  public UUID getUserId() { return userId; }
  public String getAction() { return action; }
  public String getPayload() { return payload; }
  public String getStatus() { return status; }
  public void setStatus(String s) { this.status = s; }
  public Instant getExpiresAt() { return expiresAt; }
  public void setDecidedAt(Instant t) { this.decidedAt = t; }
}
