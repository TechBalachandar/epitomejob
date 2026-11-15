package com.epitomejob.billing.service

import com.epitomejob.billing.model.Plan
import com.epitomejob.common.constants.AppConstants
import com.epitomejob.common.exception.BillingNotConfiguredException
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.HexFormat
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Stripe provider tests run fully offline: signature verification is local
 * HMAC, so a correctly signed payload proves the constructEvent path without
 * any network or Stripe account. Session creation is NOT tested here — it
 * needs a live Stripe key (Mac gate, manual).
 */
class StripeBillingProviderTest {

  companion object {
    private val MAPPER = ObjectMapper()
    private const val WEBHOOK_SECRET = "whsec_test_secret"
    private const val PAYLOAD = "{\"id\":\"evt_test_1\",\"type\":\"checkout.session.completed\"," +
      "\"data\":{\"object\":{\"id\":\"cs_test_123\",\"object\":\"checkout.session\"}}}"
  }

  private fun provider(secretKey: String, webhookSecret: String): StripeBillingProvider {
    return StripeBillingProvider(secretKey, webhookSecret, "http://localhost:3000", MAPPER)
  }

  private fun plan(): Plan {
    return Plan("SPRINT_PASS", "Sprint Pass", 7900, "USD", 30,
      "30 days of full access — one-time payment.", true)
  }

  /** Builds a real Stripe-Signature header: t=<epoch>,v1=HMAC(secret, t + "." + payload). */
  private fun signedHeader(payload: String): String {
    val timestamp = Instant.now().epochSecond
    val signedPayload = "$timestamp.$payload"
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(SecretKeySpec(WEBHOOK_SECRET.toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
    val signature = HexFormat.of().formatHex(mac.doFinal(signedPayload.toByteArray(StandardCharsets.UTF_8)))
    return "t=$timestamp,v1=$signature"
  }

  @Test
  fun createWithoutSecretKeyIs503NotConfigured() {
    val ex = assertThrows(BillingNotConfiguredException::class.java) {
      provider("", WEBHOOK_SECRET).createCheckoutSession(UUID.randomUUID(), plan())
    }
    assertEquals(AppConstants.Errors.BILLING_NOT_CONFIGURED_MESSAGE, ex.message)
  }

  @Test
  fun createWithBlankSecretKeyIs503() {
    assertThrows(BillingNotConfiguredException::class.java) {
      provider("   ", WEBHOOK_SECRET).createCheckoutSession(UUID.randomUUID(), plan())
    }
  }

  @Test
  fun webhookWithValidSignatureReturnsCompletedEvent() {
    val event = provider("sk_test_x", WEBHOOK_SECRET)
      .handleWebhook(PAYLOAD, signedHeader(PAYLOAD))

    assertEquals("CHECKOUT_COMPLETED", event.type)
    assertEquals("cs_test_123", event.providerSessionId)
  }

  @Test
  fun webhookWithBadSignatureIs400() {
    val ex = assertThrows(ResponseStatusException::class.java) {
      provider("sk_test_x", WEBHOOK_SECRET)
        .handleWebhook(PAYLOAD, "t=1234567890,v1=deadbeef")
    }
    assertEquals(HttpStatus.BAD_REQUEST, ex.statusCode)
    assertEquals(AppConstants.Errors.BILLING_BAD_SIGNATURE, ex.reason)
  }

  @Test
  fun webhookWithoutSignatureIs400() {
    val ex = assertThrows(ResponseStatusException::class.java) {
      provider("sk_test_x", WEBHOOK_SECRET).handleWebhook(PAYLOAD, null)
    }
    assertEquals(HttpStatus.BAD_REQUEST, ex.statusCode)
  }

  @Test
  fun webhookWithoutWebhookSecretIs400() {
    val ex = assertThrows(ResponseStatusException::class.java) {
      provider("sk_test_x", "").handleWebhook(PAYLOAD, signedHeader(PAYLOAD))
    }
    assertEquals(HttpStatus.BAD_REQUEST, ex.statusCode)
  }

  @Test
  fun webhookNonCheckoutEventPassesThrough() {
    val payload = "{\"id\":\"evt_test_2\",\"type\":\"charge.succeeded\"," +
      "\"data\":{\"object\":{\"id\":\"ch_test_1\",\"object\":\"charge\"}}}"
    val event = provider("sk_test_x", WEBHOOK_SECRET)
      .handleWebhook(payload, signedHeader(payload))

    assertEquals("charge.succeeded", event.type)
    assertNull(event.providerSessionId)
  }
}
