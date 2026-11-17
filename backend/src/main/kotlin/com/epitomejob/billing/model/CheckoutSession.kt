package com.epitomejob.billing.model

import com.epitomejob.common.constants.AppConstants
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * One checkout attempt (W4 contract §1b). `userId` is a plain UUID —
 * no FK to identity.users (no cross-module DB reads).
 */
@Entity
@Table(name = "checkout_sessions", schema = "billing")
class CheckoutSession(
  @Id
  @Column(name = "id", nullable = false)
  var id: UUID = UUID.randomUUID(),

  @Column(name = "user_id", nullable = false)
  var userId: UUID,

  @Column(name = "plan_code", nullable = false)
  var planCode: String,

  @Column(name = "provider", nullable = false)
  var provider: String,

  @Column(name = "provider_session_id")
  var providerSessionId: String?,

  @Column(name = "status", nullable = false)
  var status: String = AppConstants.Billing.SESSION_STATUS_CREATED,

  @Column(name = "created_at", nullable = false)
  var createdAt: Instant = Instant.now()
) {

  constructor(
    userId: UUID,
    planCode: String,
    provider: String,
    providerSessionId: String?,
    status: String
  ) : this(
    id = UUID.randomUUID(),
    userId = userId,
    planCode = planCode,
    provider = provider,
    providerSessionId = providerSessionId,
    status = status
  )
}
