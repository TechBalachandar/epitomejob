package com.epitomejob.billing.service

import com.epitomejob.billing.model.Plan
import java.util.UUID

/** Payment-provider boundary (W4 contract §1d). The service never talks to Stripe directly. */
interface BillingProvider {

  fun createCheckoutSession(userId: UUID, plan: Plan): CheckoutSessionResult

  fun handleWebhook(rawBody: String?, signatureHeader: String?): WebhookEvent
}
