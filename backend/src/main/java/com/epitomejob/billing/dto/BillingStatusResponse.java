package com.epitomejob.billing.dto;

/** GET /billing/status — always 200, even when the flag is off. */
public record BillingStatusResponse(boolean enabled, String provider) {}
