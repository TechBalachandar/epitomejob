package com.epitomejob.billing.service;

import com.epitomejob.billing.dto.CheckoutResponse;
import com.epitomejob.billing.model.CheckoutSession;
import com.epitomejob.billing.model.Entitlement;
import com.epitomejob.billing.model.Plan;
import com.epitomejob.billing.repository.CheckoutSessionRepository;
import com.epitomejob.billing.repository.EntitlementRepository;
import com.epitomejob.billing.repository.PlanRepository;
import com.epitomejob.common.constants.AppConstants;
import com.epitomejob.common.exception.AlreadyEntitledException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BillingServiceTest {

  private static final UUID USER_ID = UUID.randomUUID();

  @Mock
  private BillingProvider provider;
  @Mock
  private PlanRepository plans;
  @Mock
  private CheckoutSessionRepository sessions;
  @Mock
  private EntitlementRepository entitlements;

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  private void authenticate() {
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken(USER_ID.toString(), null, List.of()));
  }

  private BillingService service(boolean enabled, String providerName) {
    return new BillingService(provider, providerName, enabled, plans, sessions, entitlements);
  }

  private Plan sprintPass() {
    return new Plan(AppConstants.Billing.PLAN_SPRINT_PASS, "Sprint Pass", 7900,
        "USD", 30, "30 days of full access — one-time payment.", true);
  }

  private Plan lifetime() {
    return new Plan(AppConstants.Billing.PLAN_LIFETIME, "Lifetime", 14900,
        "USD", null, "Lifetime access — one-time payment.", true);
  }

  private Plan humanReviewed() {
    return new Plan(AppConstants.Billing.PLAN_HUMAN_REVIEWED, "Human-Reviewed Package", 29900,
        "USD", null, "Human-reviewed resume + plan. Fulfilled manually by our team — we contact you after checkout.", true);
  }

  @Test
  void statusAlwaysReturnsFlagAndProvider() {
    assertEquals(new com.epitomejob.billing.dto.BillingStatusResponse(true, "fake"),
        service(true, "fake").status());
    assertEquals(new com.epitomejob.billing.dto.BillingStatusResponse(false, "stripe"),
        service(false, "stripe").status());
  }

  @Test
  void listPlansReturnsSeedOrderNotAlphabetical() {
    when(plans.findByActiveTrue()).thenReturn(List.of(lifetime(), humanReviewed(), sprintPass()));

    var result = service(true, "fake").listPlans();

    assertEquals(List.of("SPRINT_PASS", "LIFETIME", "HUMAN_REVIEWED"),
        result.stream().map(p -> p.code()).toList());
    assertEquals(7900, result.get(0).priceCents());
    assertEquals(14900, result.get(1).priceCents());
    assertEquals(29900, result.get(2).priceCents());
    assertNull(result.get(1).durationDays());
  }

  @Test
  void listPlansWhenFlagOffIs404() {
    var ex = assertThrows(ResponseStatusException.class,
        () -> service(false, "fake").listPlans());
    assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
  }

  @Test
  void checkoutUnknownPlanIs404() {
    authenticate();
    when(plans.findById("NOPE")).thenReturn(Optional.empty());

    var ex = assertThrows(ResponseStatusException.class,
        () -> service(true, "fake").checkout("NOPE"));
    assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    verifyNoInteractions(provider);
  }

  @Test
  void checkoutAlreadyEntitledIs409AndNeverCallsProvider() {
    authenticate();
    when(plans.findById("SPRINT_PASS")).thenReturn(Optional.of(sprintPass()));
    when(entitlements.existsByUserIdAndPlanCodeAndStatus(USER_ID, "SPRINT_PASS", "ACTIVE"))
        .thenReturn(true);

    assertThrows(AlreadyEntitledException.class,
        () -> service(true, "fake").checkout("SPRINT_PASS"));
    verifyNoInteractions(provider);
  }

  @Test
  void checkoutHumanReviewedCreatesPendingManualAndNoProviderSession() {
    authenticate();
    when(plans.findById("HUMAN_REVIEWED")).thenReturn(Optional.of(humanReviewed()));
    when(entitlements.existsByUserIdAndPlanCodeAndStatus(USER_ID, "HUMAN_REVIEWED", "ACTIVE"))
        .thenReturn(false);
    when(entitlements.save(any())).thenAnswer(i -> i.getArgument(0));
    when(sessions.save(any())).thenAnswer(i -> i.getArgument(0));

    CheckoutResponse response = service(true, "fake").checkout("HUMAN_REVIEWED");

    assertEquals("PENDING_MANUAL", response.status());
    assertNull(response.checkoutUrl());
    assertNotNull(response.checkoutSessionId());
    verifyNoInteractions(provider);
    ArgumentCaptor<Entitlement> entitlement = ArgumentCaptor.forClass(Entitlement.class);
    verify(entitlements).save(entitlement.capture());
    assertEquals("PENDING_MANUAL", entitlement.getValue().getStatus());
    assertNull(entitlement.getValue().getExpiresAt());
    ArgumentCaptor<CheckoutSession> session = ArgumentCaptor.forClass(CheckoutSession.class);
    verify(sessions).save(session.capture());
    assertNull(session.getValue().getProviderSessionId());
    assertEquals("CREATED", session.getValue().getStatus());
  }

  @Test
  void checkoutCreatesSessionRowWithProviderResult() {
    authenticate();
    when(plans.findById("SPRINT_PASS")).thenReturn(Optional.of(sprintPass()));
    when(entitlements.existsByUserIdAndPlanCodeAndStatus(USER_ID, "SPRINT_PASS", "ACTIVE"))
        .thenReturn(false);
    when(provider.createCheckoutSession(eq(USER_ID), any(Plan.class)))
        .thenReturn(new CheckoutSessionResult("fake_abc", null));
    when(sessions.save(any())).thenAnswer(i -> i.getArgument(0));

    CheckoutResponse response = service(true, "fake").checkout("SPRINT_PASS");

    assertEquals("CREATED", response.status());
    assertEquals("fake", response.provider());
    assertNull(response.checkoutUrl());
    ArgumentCaptor<CheckoutSession> session = ArgumentCaptor.forClass(CheckoutSession.class);
    verify(sessions).save(session.capture());
    assertEquals("fake_abc", session.getValue().getProviderSessionId());
    assertEquals("CREATED", session.getValue().getStatus());
  }

  @Test
  void checkoutWhenFlagOffIs404() {
    authenticate();
    var ex = assertThrows(ResponseStatusException.class,
        () -> service(false, "fake").checkout("SPRINT_PASS"));
    assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    verifyNoInteractions(provider);
  }

  @Test
  void webhookCompletedMarksPaidAndActivatesWithExpiry() {
    when(provider.handleWebhook("{}", null))
        .thenReturn(new WebhookEvent("CHECKOUT_COMPLETED", "fake_abc"));
    CheckoutSession session = new CheckoutSession(USER_ID, "SPRINT_PASS", "fake",
        "fake_abc", "CREATED");
    when(sessions.findByProviderSessionId("fake_abc")).thenReturn(Optional.of(session));
    when(plans.findById("SPRINT_PASS")).thenReturn(Optional.of(sprintPass()));
    when(entitlements.existsByUserIdAndPlanCodeAndStatus(USER_ID, "SPRINT_PASS", "ACTIVE"))
        .thenReturn(false);
    when(sessions.save(any())).thenAnswer(i -> i.getArgument(0));
    when(entitlements.save(any())).thenAnswer(i -> i.getArgument(0));

    var response = service(true, "fake").handleWebhook("{}", null);

    assertTrue(response.received());
    assertEquals("PAID", session.getStatus());
    ArgumentCaptor<Entitlement> entitlement = ArgumentCaptor.forClass(Entitlement.class);
    verify(entitlements).save(entitlement.capture());
    assertEquals("ACTIVE", entitlement.getValue().getStatus());
    assertNotNull(entitlement.getValue().getExpiresAt());
    assertTrue(entitlement.getValue().getExpiresAt()
        .isAfter(Instant.now().plusSeconds(29L * 24 * 3600)));
  }

  @Test
  void webhookLifetimeHasNoExpiry() {
    when(provider.handleWebhook("{}", null))
        .thenReturn(new WebhookEvent("CHECKOUT_COMPLETED", "fake_life"));
    CheckoutSession session = new CheckoutSession(USER_ID, "LIFETIME", "fake",
        "fake_life", "CREATED");
    when(sessions.findByProviderSessionId("fake_life")).thenReturn(Optional.of(session));
    when(plans.findById("LIFETIME")).thenReturn(Optional.of(lifetime()));
    when(entitlements.existsByUserIdAndPlanCodeAndStatus(USER_ID, "LIFETIME", "ACTIVE"))
        .thenReturn(false);
    when(sessions.save(any())).thenAnswer(i -> i.getArgument(0));
    when(entitlements.save(any())).thenAnswer(i -> i.getArgument(0));

    service(true, "fake").handleWebhook("{}", null);

    ArgumentCaptor<Entitlement> entitlement = ArgumentCaptor.forClass(Entitlement.class);
    verify(entitlements).save(entitlement.capture());
    assertEquals("ACTIVE", entitlement.getValue().getStatus());
    assertNull(entitlement.getValue().getExpiresAt());
  }

  @Test
  void webhookUnknownSessionIs400() {
    when(provider.handleWebhook("{}", null))
        .thenReturn(new WebhookEvent("CHECKOUT_COMPLETED", "fake_nope"));
    when(sessions.findByProviderSessionId("fake_nope")).thenReturn(Optional.empty());

    var ex = assertThrows(ResponseStatusException.class,
        () -> service(true, "fake").handleWebhook("{}", null));
    assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
  }

  @Test
  void webhookReplayIsIdempotent() {
    when(provider.handleWebhook("{}", null))
        .thenReturn(new WebhookEvent("CHECKOUT_COMPLETED", "fake_abc"));
    CheckoutSession session = new CheckoutSession(USER_ID, "SPRINT_PASS", "fake",
        "fake_abc", "PAID");
    when(sessions.findByProviderSessionId("fake_abc")).thenReturn(Optional.of(session));
    when(plans.findById("SPRINT_PASS")).thenReturn(Optional.of(sprintPass()));
    when(entitlements.existsByUserIdAndPlanCodeAndStatus(USER_ID, "SPRINT_PASS", "ACTIVE"))
        .thenReturn(true);
    when(sessions.save(any())).thenAnswer(i -> i.getArgument(0));

    var response = service(true, "fake").handleWebhook("{}", null);

    assertTrue(response.received());
    verify(entitlements, never()).save(any());
  }

  @Test
  void webhookOtherEventTypeIsAcknowledgedWithoutAction() {
    when(provider.handleWebhook("{}", null))
        .thenReturn(new WebhookEvent("charge.succeeded", null));

    var response = service(true, "fake").handleWebhook("{}", null);

    assertTrue(response.received());
    verifyNoInteractions(sessions, entitlements, plans);
  }

  @Test
  void webhookWhenFlagOffIs404() {
    var ex = assertThrows(ResponseStatusException.class,
        () -> service(false, "fake").handleWebhook("{}", null));
    assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    verifyNoInteractions(provider);
  }

  @Test
  void myEntitlementReturnsActive() {
    authenticate();
    Entitlement active = new Entitlement(USER_ID, "SPRINT_PASS", "ACTIVE",
        Instant.now().plusSeconds(30L * 24 * 3600));
    when(entitlements.findFirstByUserIdAndStatusOrderByStartedAtDesc(USER_ID, "ACTIVE"))
        .thenReturn(Optional.of(active));

    var response = service(true, "fake").myEntitlement();

    assertEquals("SPRINT_PASS", response.planCode());
    assertEquals("ACTIVE", response.status());
    assertNotNull(response.expiresAt());
  }

  @Test
  void myEntitlementFallsBackToPendingManual() {
    authenticate();
    Entitlement pending = new Entitlement(USER_ID, "HUMAN_REVIEWED", "PENDING_MANUAL", null);
    when(entitlements.findFirstByUserIdAndStatusOrderByStartedAtDesc(USER_ID, "ACTIVE"))
        .thenReturn(Optional.empty());
    when(entitlements.findFirstByUserIdAndStatusOrderByStartedAtDesc(USER_ID, "PENDING_MANUAL"))
        .thenReturn(Optional.of(pending));

    var response = service(true, "fake").myEntitlement();

    assertEquals("HUMAN_REVIEWED", response.planCode());
    assertEquals("PENDING_MANUAL", response.status());
    assertNull(response.expiresAt());
  }

  @Test
  void myEntitlementNoneWhenEmpty() {
    authenticate();
    when(entitlements.findFirstByUserIdAndStatusOrderByStartedAtDesc(USER_ID, "ACTIVE"))
        .thenReturn(Optional.empty());
    when(entitlements.findFirstByUserIdAndStatusOrderByStartedAtDesc(USER_ID, "PENDING_MANUAL"))
        .thenReturn(Optional.empty());

    var response = service(true, "fake").myEntitlement();

    assertNull(response.planCode());
    assertEquals("NONE", response.status());
    assertNull(response.expiresAt());
  }

  @Test
  void myEntitlementWhenFlagOffIs404() {
    authenticate();
    var ex = assertThrows(ResponseStatusException.class,
        () -> service(false, "fake").myEntitlement());
    assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
  }
}
