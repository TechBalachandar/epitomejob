package com.epitomejob.billing.service;

import com.epitomejob.billing.model.Plan;
import com.epitomejob.common.constants.AppConstants;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/**
 * Fake provider for tests and explicit local-dev demo
 * (BILLING_PROVIDER=fake; never the default). Checkout returns a fake
 * session id and no URL — the demo completes via a simulated webhook call.
 * The webhook parses JSON {@code {"providerSessionId": "...", "type":
 * "CHECKOUT_COMPLETED"}} with no signature.
 */
public class FakeBillingProvider implements BillingProvider {

  private final ObjectMapper mapper;

  public FakeBillingProvider(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public CheckoutSessionResult createCheckoutSession(UUID userId, Plan plan) {
    return new CheckoutSessionResult(
        AppConstants.Billing.FAKE_SESSION_PREFIX + UUID.randomUUID(), null);
  }

  @Override
  public WebhookEvent handleWebhook(String rawBody, String signatureHeader) {
    final JsonNode root;
    try {
      root = mapper.readTree(rawBody);
    } catch (JsonProcessingException | IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
          AppConstants.Errors.BILLING_INVALID_WEBHOOK, e);
    }
    if (root == null || !root.isObject()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
          AppConstants.Errors.BILLING_INVALID_WEBHOOK);
    }
    String type = root.path(AppConstants.Billing.WEBHOOK_FIELD_TYPE).asText(null);
    String providerSessionId = root.path(AppConstants.Billing.WEBHOOK_FIELD_PROVIDER_SESSION_ID).asText(null);
    if (!AppConstants.Billing.WEBHOOK_EVENT_CHECKOUT_COMPLETED.equals(type)
        || providerSessionId == null || providerSessionId.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
          AppConstants.Errors.BILLING_INVALID_WEBHOOK);
    }
    return new WebhookEvent(type, providerSessionId);
  }
}
