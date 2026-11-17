package com.epitomejob.billing.service

import com.epitomejob.billing.dto.BillingStatusResponse
import com.epitomejob.billing.dto.CheckoutResponse
import com.epitomejob.billing.dto.EntitlementResponse
import com.epitomejob.billing.dto.PlanDto
import com.epitomejob.billing.dto.WebhookResponse
import com.epitomejob.billing.model.CheckoutSession
import com.epitomejob.billing.model.Entitlement
import com.epitomejob.billing.model.Plan
import com.epitomejob.billing.repository.CheckoutSessionRepository
import com.epitomejob.billing.repository.EntitlementRepository
import com.epitomejob.billing.repository.PlanRepository
import com.epitomejob.common.constants.AppConstants
import com.epitomejob.common.exception.AlreadyEntitledException
import com.epitomejob.common.util.SecurityUtil
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Billing (M14, W4 contract §1). The flag gate: every method except
 * [status] returns 404 while billing is disabled. Entitlements are
 * recorded only — nothing in W4 gates any feature on them.
 */
@Service
class BillingService(
  private val provider: BillingProvider,
  @Value("\${" + AppConstants.Billing.FEATURE_PROVIDER + ":stripe}")
  private val providerName: String,
  @Value("\${" + AppConstants.Billing.FEATURE_ENABLED + ":false}")
  private val enabled: Boolean,
  private val plans: PlanRepository,
  private val sessions: CheckoutSessionRepository,
  private val entitlements: EntitlementRepository
) {

  /** Always 200, even when the flag is off — how the web learns the flag state. */
  fun status(): BillingStatusResponse {
    return BillingStatusResponse(enabled, providerName)
  }

  /** Active plans only, in seed order (SPRINT_PASS, LIFETIME, HUMAN_REVIEWED). */
  @Transactional(readOnly = true)
  fun listPlans(): List<PlanDto> {
    requireEnabled()
    return plans.findByActiveTrue()
      .sortedBy { plan -> AppConstants.Billing.SEED_ORDER.indexOf(plan.code) }
      .map { PlanDto.from(it) }
  }

  /**
   * Creates a checkout session. HUMAN_REVIEWED never touches the provider:
   * it records a PENDING_MANUAL entitlement and a checkout row with no
   * provider session (manual fulfilment). Already-entitled → 409.
   * A Stripe provider with no key throws 503 from the provider.
   */
  @Transactional
  fun checkout(planCode: String): CheckoutResponse {
    requireEnabled()
    val plan = plans.findById(planCode)
      .orElseThrow {
        ResponseStatusException(HttpStatus.NOT_FOUND, AppConstants.Errors.PLAN_NOT_FOUND)
      }
    val userId = SecurityUtil.currentUserId()
    if (entitlements.existsByUserIdAndPlanCodeAndStatus(userId, planCode,
        AppConstants.Billing.ENTITLEMENT_STATUS_ACTIVE)) {
      throw AlreadyEntitledException()
    }
    if (AppConstants.Billing.PLAN_HUMAN_REVIEWED == planCode) {
      entitlements.save(Entitlement(userId, planCode,
        AppConstants.Billing.ENTITLEMENT_STATUS_PENDING_MANUAL, null))
      val session = sessions.save(CheckoutSession(userId, planCode,
        providerName, null, AppConstants.Billing.SESSION_STATUS_CREATED))
      return CheckoutResponse(session.id, providerName, null,
        AppConstants.Billing.ENTITLEMENT_STATUS_PENDING_MANUAL)
    }
    val result = provider.createCheckoutSession(userId, plan)
    val session = sessions.save(CheckoutSession(userId, planCode,
      providerName, result.providerSessionId, AppConstants.Billing.SESSION_STATUS_CREATED))
    return CheckoutResponse(session.id, providerName, result.checkoutUrl,
      AppConstants.Billing.SESSION_STATUS_CREATED)
  }

  /**
   * Handles a verified webhook event. On CHECKOUT_COMPLETED: marks the
   * session PAID and creates/activates the entitlement (expiresAt = now +
   * durationDays, null for lifetime). A replayed event is idempotent — a
   * second delivery finds the ACTIVE entitlement and creates nothing.
   * Other event types are acknowledged without action. Unknown session → 400.
   */
  @Transactional
  fun handleWebhook(rawBody: String?, signatureHeader: String?): WebhookResponse {
    requireEnabled()
    val event = provider.handleWebhook(rawBody, signatureHeader)
    if (AppConstants.Billing.WEBHOOK_EVENT_CHECKOUT_COMPLETED != event.type) {
      return WebhookResponse(true)
    }
    val providerSessionId = event.providerSessionId
    if (providerSessionId == null || providerSessionId.isBlank()) {
      throw ResponseStatusException(HttpStatus.BAD_REQUEST,
        AppConstants.Errors.BILLING_UNKNOWN_SESSION)
    }
    val session = sessions.findByProviderSessionId(providerSessionId)
      .orElseThrow {
        ResponseStatusException(HttpStatus.BAD_REQUEST,
          AppConstants.Errors.BILLING_UNKNOWN_SESSION)
      }
    session.status = AppConstants.Billing.SESSION_STATUS_PAID
    sessions.save(session)
    val plan = plans.findById(session.planCode)
      .orElseThrow {
        ResponseStatusException(HttpStatus.BAD_REQUEST,
          AppConstants.Errors.BILLING_UNKNOWN_SESSION)
      }
    if (!entitlements.existsByUserIdAndPlanCodeAndStatus(session.userId, plan.code,
        AppConstants.Billing.ENTITLEMENT_STATUS_ACTIVE)) {
      val durationDays = plan.durationDays
      val expiresAt = if (durationDays == null) null
      else Instant.now().plus(Duration.ofDays(durationDays.toLong()))
      entitlements.save(Entitlement(session.userId, plan.code,
        AppConstants.Billing.ENTITLEMENT_STATUS_ACTIVE, expiresAt))
    }
    return WebhookResponse(true)
  }

  /** The user's current entitlement: ACTIVE first, then PENDING_MANUAL, else NONE. */
  @Transactional(readOnly = true)
  fun myEntitlement(): EntitlementResponse {
    requireEnabled()
    val userId = SecurityUtil.currentUserId()
    return entitlements
      .findFirstByUserIdAndStatusOrderByStartedAtDesc(userId,
        AppConstants.Billing.ENTITLEMENT_STATUS_ACTIVE)
      .or {
        entitlements.findFirstByUserIdAndStatusOrderByStartedAtDesc(userId,
          AppConstants.Billing.ENTITLEMENT_STATUS_PENDING_MANUAL)
      }
      .map { EntitlementResponse.from(it) }
      .orElseGet { EntitlementResponse.none() }
  }

  private fun requireEnabled() {
    if (!enabled) {
      throw ResponseStatusException(HttpStatus.NOT_FOUND,
        AppConstants.Errors.BILLING_NOT_ENABLED)
    }
  }
}
