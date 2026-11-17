package com.epitomejob.billing.service

import com.epitomejob.billing.model.Plan
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

class FakeBillingProviderTest {

  companion object {
    private val MAPPER = ObjectMapper()
  }

  private fun provider(): FakeBillingProvider {
    return FakeBillingProvider(MAPPER)
  }

  private fun plan(): Plan {
    return Plan("SPRINT_PASS", "Sprint Pass", 7900, "USD", 30,
      "30 days of full access — one-time payment.", true)
  }

  @Test
  fun createReturnsFakeSessionIdAndNoUrl() {
    val result = provider().createCheckoutSession(UUID.randomUUID(), plan())

    assertTrue(result.providerSessionId?.startsWith("fake_") ?: false)
    assertNull(result.checkoutUrl)
  }

  @Test
  fun webhookParsesCompletedEvent() {
    val event = provider().handleWebhook(
      "{\"providerSessionId\": \"fake_abc\", \"type\": \"CHECKOUT_COMPLETED\"}", null)

    assertEquals("CHECKOUT_COMPLETED", event.type)
    assertEquals("fake_abc", event.providerSessionId)
  }

  @Test
  fun webhookMalformedJsonIs400() {
    val ex = assertThrows(ResponseStatusException::class.java) {
      provider().handleWebhook("not json", null)
    }
    assertEquals(HttpStatus.BAD_REQUEST, ex.statusCode)
  }

  @Test
  fun webhookWrongTypeIs400() {
    val ex = assertThrows(ResponseStatusException::class.java) {
      provider().handleWebhook(
        "{\"providerSessionId\": \"fake_abc\", \"type\": \"OTHER\"}", null)
    }
    assertEquals(HttpStatus.BAD_REQUEST, ex.statusCode)
  }

  @Test
  fun webhookMissingSessionIdIs400() {
    val ex = assertThrows(ResponseStatusException::class.java) {
      provider().handleWebhook("{\"type\": \"CHECKOUT_COMPLETED\"}", null)
    }
    assertEquals(HttpStatus.BAD_REQUEST, ex.statusCode)
  }

  @Test
  fun webhookNullBodyIs400() {
    val ex = assertThrows(ResponseStatusException::class.java) {
      provider().handleWebhook(null, null)
    }
    assertEquals(HttpStatus.BAD_REQUEST, ex.statusCode)
  }
}
