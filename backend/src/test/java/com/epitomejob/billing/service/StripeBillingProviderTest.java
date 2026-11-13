package com.epitomejob.billing.service;

import com.epitomejob.billing.model.Plan;
import com.epitomejob.common.constants.AppConstants;
import com.epitomejob.common.exception.BillingNotConfiguredException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Stripe provider tests run fully offline: signature verification is local
 * HMAC, so a correctly signed payload proves the constructEvent path without
 * any network or Stripe account. Session creation is NOT tested here — it
 * needs a live Stripe key (Mac gate, manual).
 */
class StripeBillingProviderTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final String WEBHOOK_SECRET = "whsec_test_secret";
  private static final String PAYLOAD = "{\"id\":\"evt_test_1\",\"type\":\"checkout.session.completed\","
      + "\"data\":{\"object\":{\"id\":\"cs_test_123\",\"object\":\"checkout.session\"}}}";

  private StripeBillingProvider provider(String secretKey, String webhookSecret) {
    return new StripeBillingProvider(secretKey, webhookSecret, "http://localhost:3000", MAPPER);
  }

  private Plan plan() {
    return new Plan("SPRINT_PASS", "Sprint Pass", 7900, "USD", 30,
        "30 days of full access — one-time payment.", true);
  }

  /** Builds a real Stripe-Signature header: t=<epoch>,v1=HMAC(secret, t + "." + payload). */
  private String signedHeader(String payload) throws Exception {
    long timestamp = Instant.now().getEpochSecond();
    String signedPayload = timestamp + "." + payload;
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(WEBHOOK_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    String signature = HexFormat.of().formatHex(mac.doFinal(signedPayload.getBytes(StandardCharsets.UTF_8)));
    return "t=" + timestamp + ",v1=" + signature;
  }

  @Test
  void createWithoutSecretKeyIs503NotConfigured() {
    var ex = assertThrows(BillingNotConfiguredException.class,
        () -> provider("", WEBHOOK_SECRET).createCheckoutSession(UUID.randomUUID(), plan()));
    assertEquals(AppConstants.Errors.BILLING_NOT_CONFIGURED_MESSAGE, ex.getMessage());
  }

  @Test
  void createWithBlankSecretKeyIs503() {
    assertThrows(BillingNotConfiguredException.class,
        () -> provider("   ", WEBHOOK_SECRET).createCheckoutSession(UUID.randomUUID(), plan()));
  }

  @Test
  void webhookWithValidSignatureReturnsCompletedEvent() throws Exception {
    var event = provider("sk_test_x", WEBHOOK_SECRET)
        .handleWebhook(PAYLOAD, signedHeader(PAYLOAD));

    assertEquals("CHECKOUT_COMPLETED", event.type());
    assertEquals("cs_test_123", event.providerSessionId());
  }

  @Test
  void webhookWithBadSignatureIs400() {
    var ex = assertThrows(ResponseStatusException.class,
        () -> provider("sk_test_x", WEBHOOK_SECRET)
            .handleWebhook(PAYLOAD, "t=1234567890,v1=deadbeef"));
    assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    assertEquals(AppConstants.Errors.BILLING_BAD_SIGNATURE, ex.getReason());
  }

  @Test
  void webhookWithoutSignatureIs400() {
    var ex = assertThrows(ResponseStatusException.class,
        () -> provider("sk_test_x", WEBHOOK_SECRET).handleWebhook(PAYLOAD, null));
    assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
  }

  @Test
  void webhookWithoutWebhookSecretIs400() throws Exception {
    var ex = assertThrows(ResponseStatusException.class,
        () -> provider("sk_test_x", "").handleWebhook(PAYLOAD, signedHeader(PAYLOAD)));
    assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
  }

  @Test
  void webhookNonCheckoutEventPassesThrough() throws Exception {
    String payload = "{\"id\":\"evt_test_2\",\"type\":\"charge.succeeded\","
        + "\"data\":{\"object\":{\"id\":\"ch_test_1\",\"object\":\"charge\"}}}";
    var event = provider("sk_test_x", WEBHOOK_SECRET)
        .handleWebhook(payload, signedHeader(payload));

    assertEquals("charge.succeeded", event.type());
    assertNull(event.providerSessionId());
  }
}
