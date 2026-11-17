package com.epitomejob.billing.model

import com.epitomejob.common.constants.AppConstants
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * What the user is entitled to (W4 contract §1b). `userId` is a plain
 * UUID — no FK to identity.users (no cross-module DB reads). One ACTIVE
 * entitlement per (user_id, plan_code) is enforced by the partial unique
 * index in V5__billing.sql. An entitlement is recorded only; nothing in W4
 * gates any feature on it.
 */
@Entity
@Table(name = "entitlements", schema = "billing")
class Entitlement(
  @Id
  @Column(name = "id", nullable = false)
  var id: UUID = UUID.randomUUID(),

  @Column(name = "user_id", nullable = false)
  var userId: UUID,

  @Column(name = "plan_code", nullable = false)
  var planCode: String,

  @Column(name = "status", nullable = false)
  var status: String = AppConstants.Billing.ENTITLEMENT_STATUS_ACTIVE,

  @Column(name = "started_at", nullable = false)
  var startedAt: Instant = Instant.now(),

  @Column(name = "expires_at")
  var expiresAt: Instant?
) {

  constructor(userId: UUID, planCode: String, status: String, expiresAt: Instant?) : this(
    id = UUID.randomUUID(),
    userId = userId,
    planCode = planCode,
    status = status,
    expiresAt = expiresAt
  )
}
