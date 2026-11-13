package com.epitomejob.billing.controller;

import com.epitomejob.billing.dto.BillingStatusResponse;
import com.epitomejob.billing.dto.CheckoutRequest;
import com.epitomejob.billing.dto.CheckoutResponse;
import com.epitomejob.billing.dto.EntitlementResponse;
import com.epitomejob.billing.dto.PlanDto;
import com.epitomejob.billing.dto.WebhookResponse;
import com.epitomejob.billing.service.BillingService;
import com.epitomejob.common.constants.AppConstants;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Billing endpoints (W4 contract §1e). All authenticated except the webhook
 * (permitAll in SecurityConfig — Stripe cannot send a JWT; the signature is
 * verified inside the handler). Every route except /status returns 404
 * while the billing flag is off (enforced by the service).
 */
@RestController
@RequestMapping("/billing")
public class BillingController {

  private final BillingService service;

  public BillingController(BillingService service) {
    this.service = service;
  }

  @GetMapping("/status")
  public BillingStatusResponse status() {
    return service.status();
  }

  @GetMapping("/plans")
  public List<PlanDto> plans() {
    return service.listPlans();
  }

  @PostMapping("/checkout")
  @ResponseStatus(HttpStatus.CREATED)
  public CheckoutResponse checkout(@Valid @RequestBody CheckoutRequest request) {
    return service.checkout(request.planCode());
  }

  @PostMapping("/webhook")
  public WebhookResponse webhook(
      @RequestBody(required = false) String rawBody,
      @RequestHeader(value = AppConstants.Billing.STRIPE_SIGNATURE_HEADER, required = false)
      String signature) {
    return service.handleWebhook(rawBody, signature);
  }

  @GetMapping("/entitlement/me")
  public EntitlementResponse entitlementMe() {
    return service.myEntitlement();
  }
}
