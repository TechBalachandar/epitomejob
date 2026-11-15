package com.epitomejob.billing.service

import com.epitomejob.billing.model.Plan
import com.epitomejob.common.constants.AppConstants
import com.epitomejob.common.exception.BillingNotConfiguredException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.stripe.Stripe
import com.stripe.exception.SignatureVerificationException
import com.stripe.exception.StripeException
import com.stripe.model.Event
import com.stripe.model.checkout.Session
import com.stripe.net.Webhook
import com.stripe.param.checkout.SessionCreateParams
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.util.Locale
import java.util.UUID

/**
 * Real Stripe provider (W4 contract §1d), stripe-java 34.1.0.
 *
 * - Checkout: mode=payment, one-time inline price from the plan's
 *   price_cents (Stripe takes a lowercase currency code).
 * - Webhook: signature verified with STRIPE_WEBHOOK_SECRET via
 *   `Webhook.constructEvent`; bad signature → 400. The session id
 *   is read from the verified raw payload with Jackson — stripe-java
 *   34.x no longer exposes `EventDataDeserializer.getObject()`,
 *   so the payload JSON is the stable extraction path.
 * - No key configured → [BillingNotConfiguredException] (503);
 *   never silently fall back to the fake provider.
 */
class StripeBillingProvider(
  private val secretKey: String?,
  private val webhookSecret: String?,
  private val publicUrl: String,
  private val mapper: ObjectMapper
) : BillingProvider {

  override fun createCheckoutSession(userId: UUID, plan: Plan): CheckoutSessionResult {
    val key = secretKey
    if (key == null || key.isBlank()) {
      throw BillingNotConfiguredException()
    }
    Stripe.apiKey = key
    val params = SessionCreateParams.builder()
      .setMode(SessionCreateParams.Mode.PAYMENT)
      .setSuccessUrl(publicUrl + AppConstants.Billing.SUCCESS_URL_SUFFIX)
      .setCancelUrl(publicUrl + AppConstants.Billing.CANCEL_URL_SUFFIX)
      .addLineItem(SessionCreateParams.LineItem.builder()
        .setQuantity(1L)
        .setPriceData(SessionCreateParams.LineItem.PriceData.builder()
          .setCurrency(plan.currency.toLowerCase(Locale.ROOT))
          .setUnitAmount(plan.priceCents.toLong())
          .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder()
            .setName(plan.name)
            .setDescription(plan.description)
            .build())
          .build())
        .build())
      .build()
    try {
      val session = Session.create(params)
      return CheckoutSessionResult(session.id, session.url)
    } catch (e: StripeException) {
      // Never leak Stripe internals to the client.
      throw ResponseStatusException(HttpStatus.BAD_GATEWAY,
        AppConstants.Errors.BILLING_PROVIDER_ERROR, e)
    }
  }

  override fun handleWebhook(rawBody: String?, signatureHeader: String?): WebhookEvent {
    val body = rawBody
    val signature = signatureHeader
    val secret = webhookSecret
    if (body == null || signature == null || signature.isBlank()
      || secret == null || secret.isBlank()) {
      throw ResponseStatusException(HttpStatus.BAD_REQUEST,
        AppConstants.Errors.BILLING_BAD_SIGNATURE)
    }
    val event: Event
    try {
      event = Webhook.constructEvent(body, signature, secret)
    } catch (e: SignatureVerificationException) {
      throw ResponseStatusException(HttpStatus.BAD_REQUEST,
        AppConstants.Errors.BILLING_BAD_SIGNATURE, e)
    } catch (e: RuntimeException) {
      throw ResponseStatusException(HttpStatus.BAD_REQUEST,
        AppConstants.Errors.BILLING_BAD_SIGNATURE, e)
    }
    if (!AppConstants.Billing.STRIPE_EVENT_CHECKOUT_SESSION_COMPLETED.equals(event.type)) {
      return WebhookEvent(event.type, null)
    }
    val sessionId = sessionIdFromPayload(body)
    if (sessionId == null || sessionId.isBlank()) {
      throw ResponseStatusException(HttpStatus.BAD_REQUEST,
        AppConstants.Errors.BILLING_UNKNOWN_SESSION)
    }
    return WebhookEvent(AppConstants.Billing.WEBHOOK_EVENT_CHECKOUT_COMPLETED, sessionId)
  }

  /** data.object.id of the verified event payload. */
  private fun sessionIdFromPayload(rawBody: String): String? {
    try {
      val obj: JsonNode = mapper.readTree(rawBody)
        .path(AppConstants.Billing.WEBHOOK_PAYLOAD_DATA)
        .path(AppConstants.Billing.WEBHOOK_PAYLOAD_OBJECT)
      return obj.path(AppConstants.Billing.WEBHOOK_PAYLOAD_ID).asText(null)
    } catch (e: Exception) {
      return null
    }
  }
}
