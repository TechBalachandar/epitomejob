package com.epitomejob.billing.dto

import jakarta.validation.constraints.NotBlank

/** POST /billing/checkout body. */
data class CheckoutRequest(@field:NotBlank val planCode: String)
