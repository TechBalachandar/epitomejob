package com.epitomejob.billing.controller

import com.epitomejob.billing.dto.BillingStatusResponse
import com.epitomejob.billing.dto.CheckoutRequest
import com.epitomejob.billing.dto.CheckoutResponse
import com.epitomejob.billing.dto.EntitlementResponse
import com.epitomejob.billing.dto.PlanDto
import com.epitomejob.billing.dto.WebhookResponse
import com.epitomejob.billing.service.BillingService
import com.epitomejob.common.constants.AppConstants
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * Billing endpoints (W4 contract §1e). All authenticated except the webhook
 * (permitAll in SecurityConfig — Stripe cannot send a JWT; the signature is
 * verified inside the handler). Every route except /status returns 404
 * while the billing flag is off (enforced by the service).
 */
@RestController
@RequestMapping("/billing")
class BillingController(private val service: BillingService) {

  @GetMapping("/status")
  fun status(): BillingStatusResponse {
    return service.status()
  }

  @GetMapping("/plans")
  fun plans(): List<PlanDto> {
    return service.listPlans()
  }

  @PostMapping("/checkout")
  @ResponseStatus(HttpStatus.CREATED)
  fun checkout(@Valid @RequestBody request: CheckoutRequest): CheckoutResponse {
    return service.checkout(request.planCode)
  }

  @PostMapping("/webhook")
  fun webhook(
    @RequestBody(required = false) rawBody: String?,
    @RequestHeader(value = AppConstants.Billing.STRIPE_SIGNATURE_HEADER, required = false)
    signature: String?
  ): WebhookResponse {
    return service.handleWebhook(rawBody, signature)
  }

  @GetMapping("/entitlement/me")
  fun entitlementMe(): EntitlementResponse {
    return service.myEntitlement()
  }
}
