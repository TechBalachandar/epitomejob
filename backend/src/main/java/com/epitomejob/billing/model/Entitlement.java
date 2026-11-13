package com.epitomejob.billing.model;

import com.epitomejob.common.constants.AppConstants;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * What the user is entitled to (W4 contract §1b). {@code userId} is a plain
 * UUID — no FK to identity.users (no cross-module DB reads). One ACTIVE
 * entitlement per (user_id, plan_code) is enforced by the partial unique
 * index in V5__billing.sql. An entitlement is recorded only; nothing in W4
 * gates any feature on it.
 */
@Entity
@Table(name = "entitlements", schema = "billing")
public class Entitlement {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id = UUID.randomUUID();

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "plan_code", nullable = false)
  private String planCode;

  @Column(name = "status", nullable = false)
  private String status = AppConstants.Billing.ENTITLEMENT_STATUS_ACTIVE;

  @Column(name = "started_at", nullable = false)
  private Instant startedAt = Instant.now();

  @Column(name = "expires_at")
  private Instant expiresAt;

  protected Entitlement() {}

  public Entitlement(UUID userId, String planCode, String status, Instant expiresAt) {
    this.userId = userId;
    this.planCode = planCode;
    this.status = status;
    this.expiresAt = expiresAt;
  }

  public UUID getId() { return id; }
  public UUID getUserId() { return userId; }
  public String getPlanCode() { return planCode; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public Instant getStartedAt() { return startedAt; }
  public Instant getExpiresAt() { return expiresAt; }
}
