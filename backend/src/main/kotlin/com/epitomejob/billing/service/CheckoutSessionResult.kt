package com.epitomejob.billing.service

/** Result of creating a checkout session with the provider. */
data class CheckoutSessionResult(val providerSessionId: String?, val checkoutUrl: String?)
