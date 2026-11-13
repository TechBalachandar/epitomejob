package com.epitomejob.billing.service;

import com.epitomejob.billing.dto.BillingStatusResponse;
import com.epitomejob.billing.dto.CheckoutResponse;
import com.epitomejob.billing.dto.EntitlementResponse;
import com.epitomejob.billing.dto.PlanDto;
import com.epitomejob.billing.dto.WebhookResponse;
import com.epitomejob.billing.model.CheckoutSession;
import com.epitomejob.billing.model.Entitlement;
import com.epitomejob.billing.model.Plan;
import com.epitomejob.billing.repository.CheckoutSessionRepository;
import com.epitomejob.billing.repository.EntitlementRepository;
import com.epitomejob.billing.repository.PlanRepository;
import com.epitomejob.common.constants.AppConstants;
import com.epitomejob.common.exception.AlreadyEntitledException;
import com.epitomejob.common.util.SecurityUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Billing (M14, W4 contract §1). The flag gate: every method except
 * {@link #status()} returns 404 while billing is disabled. Entitlements are
 * recorded only — nothing in W4 gates any feature on them.
 */
@Service
public class BillingService {

  private final BillingProvider provider;
  private final String providerName;
  private final boolean enabled;
  private final PlanRepository plans;
  private final CheckoutSessionRepository sessions;
  private final EntitlementRepository entitlements;

  public BillingService(BillingProvider provider,
                        @Value("${" + AppConstants.Billing.FEATURE_PROVIDER + ":stripe}")
                        String providerName,
                        @Value("${" + AppConstants.Billing.FEATURE_ENABLED + ":false}")
                        boolean enabled,
                        PlanRepository plans,
                        CheckoutSessionRepository sessions,
                        EntitlementRepository entitlements) {
    this.provider = provider;
    this.providerName = providerName;
    this.enabled = enabled;
    this.plans = plans;
    this.sessions = sessions;
    this.entitlements = entitlements;
  }

  /** Always 200, even when the flag is off — how the web learns the flag state. */
  public BillingStatusResponse status() {
    return new BillingStatusResponse(enabled, providerName);
  }

  /** Active plans only, in seed order (SPRINT_PASS, LIFETIME, HUMAN_REVIEWED). */
  @Transactional(readOnly = true)
  public List<PlanDto> listPlans() {
    requireEnabled();
    return plans.findByActiveTrue().stream()
        .sorted(Comparator.comparingInt(
            plan -> AppConstants.Billing.SEED_ORDER.indexOf(plan.getCode())))
        .map(PlanDto::from)
        .toList();
  }

  /**
   * Creates a checkout session. HUMAN_REVIEWED never touches the provider:
   * it records a PENDING_MANUAL entitlement and a checkout row with no
   * provider session (manual fulfilment). Already-entitled → 409.
   * A Stripe provider with no key throws 503 from the provider.
   */
  @Transactional
  public CheckoutResponse checkout(String planCode) {
    requireEnabled();
    Plan plan = plans.findById(planCode)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
            AppConstants.Errors.PLAN_NOT_FOUND));
    UUID userId = SecurityUtil.currentUserId();
    if (entitlements.existsByUserIdAndPlanCodeAndStatus(userId, planCode,
        AppConstants.Billing.ENTITLEMENT_STATUS_ACTIVE)) {
      throw new AlreadyEntitledException();
    }
    if (AppConstants.Billing.PLAN_HUMAN_REVIEWED.equals(planCode)) {
      entitlements.save(new Entitlement(userId, planCode,
          AppConstants.Billing.ENTITLEMENT_STATUS_PENDING_MANUAL, null));
      CheckoutSession session = sessions.save(new CheckoutSession(userId, planCode,
          providerName, null, AppConstants.Billing.SESSION_STATUS_CREATED));
      return new CheckoutResponse(session.getId(), providerName, null,
          AppConstants.Billing.ENTITLEMENT_STATUS_PENDING_MANUAL);
    }
    CheckoutSessionResult result = provider.createCheckoutSession(userId, plan);
    CheckoutSession session = sessions.save(new CheckoutSession(userId, planCode,
        providerName, result.providerSessionId(), AppConstants.Billing.SESSION_STATUS_CREATED));
    return new CheckoutResponse(session.getId(), providerName, result.checkoutUrl(),
        AppConstants.Billing.SESSION_STATUS_CREATED);
  }

  /**
   * Handles a verified webhook event. On CHECKOUT_COMPLETED: marks the
   * session PAID and creates/activates the entitlement (expiresAt = now +
   * durationDays, null for lifetime). A replayed event is idempotent — a
   * second delivery finds the ACTIVE entitlement and creates nothing.
   * Other event types are acknowledged without action. Unknown session → 400.
   */
  @Transactional
  public WebhookResponse handleWebhook(String rawBody, String signatureHeader) {
    requireEnabled();
    WebhookEvent event = provider.handleWebhook(rawBody, signatureHeader);
    if (!AppConstants.Billing.WEBHOOK_EVENT_CHECKOUT_COMPLETED.equals(event.type())) {
      return new WebhookResponse(true);
    }
    if (event.providerSessionId() == null || event.providerSessionId().isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
          AppConstants.Errors.BILLING_UNKNOWN_SESSION);
    }
    CheckoutSession session = sessions.findByProviderSessionId(event.providerSessionId())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
            AppConstants.Errors.BILLING_UNKNOWN_SESSION));
    session.setStatus(AppConstants.Billing.SESSION_STATUS_PAID);
    sessions.save(session);
    Plan plan = plans.findById(session.getPlanCode())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
            AppConstants.Errors.BILLING_UNKNOWN_SESSION));
    if (!entitlements.existsByUserIdAndPlanCodeAndStatus(session.getUserId(), plan.getCode(),
        AppConstants.Billing.ENTITLEMENT_STATUS_ACTIVE)) {
      Instant expiresAt = plan.getDurationDays() == null
          ? null
          : Instant.now().plus(Duration.ofDays(plan.getDurationDays()));
      entitlements.save(new Entitlement(session.getUserId(), plan.getCode(),
          AppConstants.Billing.ENTITLEMENT_STATUS_ACTIVE, expiresAt));
    }
    return new WebhookResponse(true);
  }

  /** The user's current entitlement: ACTIVE first, then PENDING_MANUAL, else NONE. */
  @Transactional(readOnly = true)
  public EntitlementResponse myEntitlement() {
    requireEnabled();
    UUID userId = SecurityUtil.currentUserId();
    return entitlements
        .findFirstByUserIdAndStatusOrderByStartedAtDesc(userId,
            AppConstants.Billing.ENTITLEMENT_STATUS_ACTIVE)
        .or(() -> entitlements.findFirstByUserIdAndStatusOrderByStartedAtDesc(userId,
            AppConstants.Billing.ENTITLEMENT_STATUS_PENDING_MANUAL))
        .map(EntitlementResponse::from)
        .orElseGet(EntitlementResponse::none);
  }

  private void requireEnabled() {
    if (!enabled) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND,
          AppConstants.Errors.BILLING_NOT_ENABLED);
    }
  }
}
