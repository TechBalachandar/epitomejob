package com.epitomejob.billing.service

import com.epitomejob.billing.model.Plan
import com.epitomejob.common.constants.AppConstants
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

/**
 * Fake provider for tests and explicit local-dev demo
 * (BILLING_PROVIDER=fake; never the default). Checkout returns a fake
 * session id and no URL — the demo completes via a simulated webhook call.
 * The webhook parses JSON `{"providerSessionId": "...", "type":
 * "CHECKOUT_COMPLETED"}` with no signature.
 */
class FakeBillingProvider(private val mapper: ObjectMapper) : BillingProvider {

  override fun createCheckoutSession(userId: UUID, plan: Plan): CheckoutSessionResult {
    return CheckoutSessionResult(
      AppConstants.Billing.FAKE_SESSION_PREFIX + UUID.randomUUID(), null)
  }

  override fun handleWebhook(rawBody: String?, signatureHeader: String?): WebhookEvent {
    val root: JsonNode?
    try {
      root = mapper.readTree(rawBody)
    } catch (e: JsonProcessingException) {
      throw ResponseStatusException(HttpStatus.BAD_REQUEST,
        AppConstants.Errors.BILLING_INVALID_WEBHOOK, e)
    } catch (e: IllegalArgumentException) {
      throw ResponseStatusException(HttpStatus.BAD_REQUEST,
        AppConstants.Errors.BILLING_INVALID_WEBHOOK, e)
    }
    if (root == null || !root.isObject) {
      throw ResponseStatusException(HttpStatus.BAD_REQUEST,
        AppConstants.Errors.BILLING_INVALID_WEBHOOK)
    }
    val type = root.path(AppConstants.Billing.WEBHOOK_FIELD_TYPE).asText(null)
    val providerSessionId =
      root.path(AppConstants.Billing.WEBHOOK_FIELD_PROVIDER_SESSION_ID).asText(null)
    if (!AppConstants.Billing.WEBHOOK_EVENT_CHECKOUT_COMPLETED.equals(type)
      || providerSessionId == null || providerSessionId.isBlank()) {
      throw ResponseStatusException(HttpStatus.BAD_REQUEST,
        AppConstants.Errors.BILLING_INVALID_WEBHOOK)
    }
    return WebhookEvent(type, providerSessionId)
  }
}
