package com.epitomejob.billing.service

/** A verified webhook event. `type` is CHECKOUT_COMPLETED (other provider event types pass through untouched). */
data class WebhookEvent(val type: String, val providerSessionId: String?)
