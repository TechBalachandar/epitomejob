package com.epitomejob.billing.dto

import java.util.UUID

/**
 * POST /billing/checkout response. `status` is CREATED, except
 * HUMAN_REVIEWED → PENDING_MANUAL with `checkoutUrl` null and no
 * provider session (manual fulfilment).
 */
data class CheckoutResponse(
  val checkoutSessionId: UUID,
  val provider: String,
  val checkoutUrl: String?,
  val status: String
)
