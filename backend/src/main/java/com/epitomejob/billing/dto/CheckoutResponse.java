package com.epitomejob.billing.dto;

import java.util.UUID;

/**
 * POST /billing/checkout response. {@code status} is CREATED, except
 * HUMAN_REVIEWED → PENDING_MANUAL with {@code checkoutUrl} null and no
 * provider session (manual fulfilment).
 */
public record CheckoutResponse(UUID checkoutSessionId, String provider,
                               String checkoutUrl, String status) {}
