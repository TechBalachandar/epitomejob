package com.epitomejob.billing.model;

import com.epitomejob.common.constants.AppConstants;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * One checkout attempt (W4 contract §1b). {@code userId} is a plain UUID —
 * no FK to identity.users (no cross-module DB reads).
 */
@Entity
@Table(name = "checkout_sessions", schema = "billing")
public class CheckoutSession {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id = UUID.randomUUID();

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "plan_code", nullable = false)
  private String planCode;

  @Column(name = "provider", nullable = false)
  private String provider;

  @Column(name = "provider_session_id")
  private String providerSessionId;

  @Column(name = "status", nullable = false)
  private String status = AppConstants.Billing.SESSION_STATUS_CREATED;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  protected CheckoutSession() {}

  public CheckoutSession(UUID userId, String planCode, String provider,
                         String providerSessionId, String status) {
    this.userId = userId;
    this.planCode = planCode;
    this.provider = provider;
    this.providerSessionId = providerSessionId;
    this.status = status;
  }

  public UUID getId() { return id; }
  public UUID getUserId() { return userId; }
  public String getPlanCode() { return planCode; }
  public String getProvider() { return provider; }
  public String getProviderSessionId() { return providerSessionId; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public Instant getCreatedAt() { return createdAt; }
}
