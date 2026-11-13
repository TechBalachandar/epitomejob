package com.epitomejob.billing.dto;

import jakarta.validation.constraints.NotBlank;

/** POST /billing/checkout body. */
public record CheckoutRequest(@NotBlank String planCode) {}
