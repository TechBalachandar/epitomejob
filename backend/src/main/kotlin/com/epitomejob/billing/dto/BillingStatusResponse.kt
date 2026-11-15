package com.epitomejob.billing.dto

/** GET /billing/status — always 200, even when the flag is off. */
data class BillingStatusResponse(val enabled: Boolean, val provider: String)
