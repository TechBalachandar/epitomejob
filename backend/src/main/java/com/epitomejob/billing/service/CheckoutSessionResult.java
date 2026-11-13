package com.epitomejob.billing.service;

/** Result of creating a checkout session with the provider. */
public record CheckoutSessionResult(String providerSessionId, String checkoutUrl) {}
