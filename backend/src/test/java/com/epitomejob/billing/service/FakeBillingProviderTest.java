package com.epitomejob.billing.service;

import com.epitomejob.billing.model.Plan;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FakeBillingProviderTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private FakeBillingProvider provider() {
    return new FakeBillingProvider(MAPPER);
  }

  private Plan plan() {
    return new Plan("SPRINT_PASS", "Sprint Pass", 7900, "USD", 30,
        "30 days of full access — one-time payment.", true);
  }

  @Test
  void createReturnsFakeSessionIdAndNoUrl() {
    var result = provider().createCheckoutSession(UUID.randomUUID(), plan());

    assertTrue(result.providerSessionId().startsWith("fake_"));
    assertNull(result.checkoutUrl());
  }

  @Test
  void webhookParsesCompletedEvent() {
    var event = provider().handleWebhook(
        "{\"providerSessionId\": \"fake_abc\", \"type\": \"CHECKOUT_COMPLETED\"}", null);

    assertEquals("CHECKOUT_COMPLETED", event.type());
    assertEquals("fake_abc", event.providerSessionId());
  }

  @Test
  void webhookMalformedJsonIs400() {
    var ex = assertThrows(ResponseStatusException.class,
        () -> provider().handleWebhook("not json", null));
    assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
  }

  @Test
  void webhookWrongTypeIs400() {
    var ex = assertThrows(ResponseStatusException.class,
        () -> provider().handleWebhook(
            "{\"providerSessionId\": \"fake_abc\", \"type\": \"OTHER\"}", null));
    assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
  }

  @Test
  void webhookMissingSessionIdIs400() {
    var ex = assertThrows(ResponseStatusException.class,
        () -> provider().handleWebhook("{\"type\": \"CHECKOUT_COMPLETED\"}", null));
    assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
  }

  @Test
  void webhookNullBodyIs400() {
    var ex = assertThrows(ResponseStatusException.class,
        () -> provider().handleWebhook(null, null));
    assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
  }
}
