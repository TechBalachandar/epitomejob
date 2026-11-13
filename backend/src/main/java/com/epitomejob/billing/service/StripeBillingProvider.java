package com.epitomejob.billing.service;

import com.epitomejob.billing.model.Plan;
import com.epitomejob.common.constants.AppConstants;
import com.epitomejob.common.exception.BillingNotConfiguredException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stripe.Stripe;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.checkout.SessionCreateParams;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
import java.util.UUID;

/**
 * Real Stripe provider (W4 contract §1d), stripe-java 34.1.0.
 * <ul>
 *   <li>Checkout: mode=payment, one-time inline price from the plan's
 *       price_cents (Stripe takes a lowercase currency code).</li>
 *   <li>Webhook: signature verified with STRIPE_WEBHOOK_SECRET via
 *       {@code Webhook.constructEvent}; bad signature → 400. The session id
 *       is read from the verified raw payload with Jackson — stripe-java
 *       34.x no longer exposes {@code EventDataDeserializer.getObject()},
 *       so the payload JSON is the stable extraction path.</li>
 *   <li>No key configured → {@link BillingNotConfiguredException} (503);
 *       never silently fall back to the fake provider.</li>
 * </ul>
 */
public class StripeBillingProvider implements BillingProvider {

  private final String secretKey;
  private final String webhookSecret;
  private final String publicUrl;
  private final ObjectMapper mapper;

  public StripeBillingProvider(String secretKey, String webhookSecret,
                               String publicUrl, ObjectMapper mapper) {
    this.secretKey = secretKey;
    this.webhookSecret = webhookSecret;
    this.publicUrl = publicUrl;
    this.mapper = mapper;
  }

  @Override
  public CheckoutSessionResult createCheckoutSession(UUID userId, Plan plan) {
    if (secretKey == null || secretKey.isBlank()) {
      throw new BillingNotConfiguredException();
    }
    Stripe.apiKey = secretKey;
    SessionCreateParams params = SessionCreateParams.builder()
        .setMode(SessionCreateParams.Mode.PAYMENT)
        .setSuccessUrl(publicUrl + AppConstants.Billing.SUCCESS_URL_SUFFIX)
        .setCancelUrl(publicUrl + AppConstants.Billing.CANCEL_URL_SUFFIX)
        .addLineItem(SessionCreateParams.LineItem.builder()
            .setQuantity(1L)
            .setPriceData(SessionCreateParams.LineItem.PriceData.builder()
                .setCurrency(plan.getCurrency().toLowerCase(Locale.ROOT))
                .setUnitAmount((long) plan.getPriceCents())
                .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder()
                    .setName(plan.getName())
                    .setDescription(plan.getDescription())
                    .build())
                .build())
            .build())
        .build();
    try {
      Session session = Session.create(params);
      return new CheckoutSessionResult(session.getId(), session.getUrl());
    } catch (StripeException e) {
      // Never leak Stripe internals to the client.
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
          AppConstants.Errors.BILLING_PROVIDER_ERROR, e);
    }
  }

  @Override
  public WebhookEvent handleWebhook(String rawBody, String signatureHeader) {
    if (rawBody == null || signatureHeader == null || signatureHeader.isBlank()
        || webhookSecret == null || webhookSecret.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
          AppConstants.Errors.BILLING_BAD_SIGNATURE);
    }
    final Event event;
    try {
      event = Webhook.constructEvent(rawBody, signatureHeader, webhookSecret);
    } catch (SignatureVerificationException | RuntimeException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
          AppConstants.Errors.BILLING_BAD_SIGNATURE, e);
    }
    if (!AppConstants.Billing.STRIPE_EVENT_CHECKOUT_SESSION_COMPLETED.equals(event.getType())) {
      return new WebhookEvent(event.getType(), null);
    }
    String sessionId = sessionIdFromPayload(rawBody);
    if (sessionId == null || sessionId.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
          AppConstants.Errors.BILLING_UNKNOWN_SESSION);
    }
    return new WebhookEvent(AppConstants.Billing.WEBHOOK_EVENT_CHECKOUT_COMPLETED, sessionId);
  }

  /** data.object.id of the verified event payload. */
  private String sessionIdFromPayload(String rawBody) {
    try {
      JsonNode object = mapper.readTree(rawBody)
          .path(AppConstants.Billing.WEBHOOK_PAYLOAD_DATA)
          .path(AppConstants.Billing.WEBHOOK_PAYLOAD_OBJECT);
      return object.path(AppConstants.Billing.WEBHOOK_PAYLOAD_ID).asText(null);
    } catch (Exception e) {
      return null;
    }
  }
}
