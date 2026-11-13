package com.epitomejob.billing.service;

import com.epitomejob.billing.model.Plan;

import java.util.UUID;

/** Payment-provider boundary (W4 contract §1d). The service never talks to Stripe directly. */
public interface BillingProvider {

  CheckoutSessionResult createCheckoutSession(UUID userId, Plan plan);

  WebhookEvent handleWebhook(String rawBody, String signatureHeader);
}
