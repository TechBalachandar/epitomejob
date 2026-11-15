package com.epitomejob.billing.dto

/** POST /billing/webhook response — always {"received": true} for a well-formed event. */
data class WebhookResponse(val received: Boolean)
