package com.epitomejob.billing.service

import com.epitomejob.billing.dto.BillingStatusResponse
import com.epitomejob.billing.dto.CheckoutResponse
import com.epitomejob.billing.model.CheckoutSession
import com.epitomejob.billing.model.Entitlement
import com.epitomejob.billing.model.Plan
import com.epitomejob.billing.repository.CheckoutSessionRepository
import com.epitomejob.billing.repository.EntitlementRepository
import com.epitomejob.billing.repository.PlanRepository
import com.epitomejob.common.constants.AppConstants
import com.epitomejob.common.exception.AlreadyEntitledException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.http.HttpStatus
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.Optional
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class BillingServiceTest {

  companion object {
    private val USER_ID = UUID.randomUUID()
  }

  @Mock
  private lateinit var provider: BillingProvider

  @Mock
  private lateinit var plans: PlanRepository

  @Mock
  private lateinit var sessions: CheckoutSessionRepository

  @Mock
  private lateinit var entitlements: EntitlementRepository

  @AfterEach
  fun clearContext() {
    SecurityContextHolder.clearContext()
  }

  private fun authenticate() {
    SecurityContextHolder.getContext().setAuthentication(
      UsernamePasswordAuthenticationToken(USER_ID.toString(), null, listOf()))
  }

  private fun service(enabled: Boolean, providerName: String): BillingService {
    return BillingService(provider, providerName, enabled, plans, sessions, entitlements)
  }

  private fun sprintPass(): Plan {
    return Plan(AppConstants.Billing.PLAN_SPRINT_PASS, "Sprint Pass", 7900,
      "USD", 30, "30 days of full access — one-time payment.", true)
  }

  private fun lifetime(): Plan {
    return Plan(AppConstants.Billing.PLAN_LIFETIME, "Lifetime", 14900,
      "USD", null, "Lifetime access — one-time payment.", true)
  }

  private fun humanReviewed(): Plan {
    return Plan(AppConstants.Billing.PLAN_HUMAN_REVIEWED, "Human-Reviewed Package", 29900,
      "USD", null, "Human-reviewed resume + plan. Fulfilled manually by our team — we contact you after checkout.", true)
  }

  @Test
  fun statusAlwaysReturnsFlagAndProvider() {
    assertEquals(BillingStatusResponse(true, "fake"),
      service(true, "fake").status())
    assertEquals(BillingStatusResponse(false, "stripe"),
      service(false, "stripe").status())
  }

  @Test
  fun listPlansReturnsSeedOrderNotAlphabetical() {
    whenever(plans.findByActiveTrue()).thenReturn(listOf(lifetime(), humanReviewed(), sprintPass()))

    val result = service(true, "fake").listPlans()

    assertEquals(listOf("SPRINT_PASS", "LIFETIME", "HUMAN_REVIEWED"),
      result.map { it.code })
    assertEquals(7900, result[0].priceCents)
    assertEquals(14900, result[1].priceCents)
    assertEquals(29900, result[2].priceCents)
    assertNull(result[1].durationDays)
  }

  @Test
  fun listPlansWhenFlagOffIs404() {
    val ex = assertThrows(ResponseStatusException::class.java) {
      service(false, "fake").listPlans()
    }
    assertEquals(HttpStatus.NOT_FOUND, ex.statusCode)
  }

  @Test
  fun checkoutUnknownPlanIs404() {
    authenticate()
    whenever(plans.findById("NOPE")).thenReturn(Optional.empty())

    val ex = assertThrows(ResponseStatusException::class.java) {
      service(true, "fake").checkout("NOPE")
    }
    assertEquals(HttpStatus.NOT_FOUND, ex.statusCode)
    verifyNoInteractions(provider)
  }

  @Test
  fun checkoutAlreadyEntitledIs409AndNeverCallsProvider() {
    authenticate()
    whenever(plans.findById("SPRINT_PASS")).thenReturn(Optional.of(sprintPass()))
    whenever(entitlements.existsByUserIdAndPlanCodeAndStatus(USER_ID, "SPRINT_PASS", "ACTIVE"))
      .thenReturn(true)

    assertThrows(AlreadyEntitledException::class.java) {
      service(true, "fake").checkout("SPRINT_PASS")
    }
    verifyNoInteractions(provider)
  }

  @Test
  fun checkoutHumanReviewedCreatesPendingManualAndNoProviderSession() {
    authenticate()
    whenever(plans.findById("HUMAN_REVIEWED")).thenReturn(Optional.of(humanReviewed()))
    whenever(entitlements.existsByUserIdAndPlanCodeAndStatus(USER_ID, "HUMAN_REVIEWED", "ACTIVE"))
      .thenReturn(false)
    whenever(entitlements.save(any())).thenAnswer { i -> i.getArgument<Entitlement>(0) }
    whenever(sessions.save(any())).thenAnswer { i -> i.getArgument<CheckoutSession>(0) }

    val response: CheckoutResponse = service(true, "fake").checkout("HUMAN_REVIEWED")

    assertEquals("PENDING_MANUAL", response.status)
    assertNull(response.checkoutUrl)
    assertNotNull(response.checkoutSessionId)
    verifyNoInteractions(provider)
    val entitlement = argumentCaptor<Entitlement>()
    verify(entitlements).save(entitlement.capture())
    assertEquals("PENDING_MANUAL", entitlement.firstValue.status)
    assertNull(entitlement.firstValue.expiresAt)
    val session = argumentCaptor<CheckoutSession>()
    verify(sessions).save(session.capture())
    assertNull(session.value.providerSessionId)
    assertEquals("CREATED", session.value.status)
  }

  @Test
  fun checkoutCreatesSessionRowWithProviderResult() {
    authenticate()
    whenever(plans.findById("SPRINT_PASS")).thenReturn(Optional.of(sprintPass()))
    whenever(entitlements.existsByUserIdAndPlanCodeAndStatus(USER_ID, "SPRINT_PASS", "ACTIVE"))
      .thenReturn(false)
    whenever(provider.createCheckoutSession(eq(USER_ID), any()))
      .thenReturn(CheckoutSessionResult("fake_abc", null))
    whenever(sessions.save(any())).thenAnswer { i -> i.getArgument<CheckoutSession>(0) }

    val response: CheckoutResponse = service(true, "fake").checkout("SPRINT_PASS")

    assertEquals("CREATED", response.status)
    assertEquals("fake", response.provider)
    assertNull(response.checkoutUrl)
    val session = argumentCaptor<CheckoutSession>()
    verify(sessions).save(session.capture())
    assertEquals("fake_abc", session.value.providerSessionId)
    assertEquals("CREATED", session.value.status)
  }

  @Test
  fun checkoutWhenFlagOffIs404() {
    authenticate()
    val ex = assertThrows(ResponseStatusException::class.java) {
      service(false, "fake").checkout("SPRINT_PASS")
    }
    assertEquals(HttpStatus.NOT_FOUND, ex.statusCode)
    verifyNoInteractions(provider)
  }

  @Test
  fun webhookCompletedMarksPaidAndActivatesWithExpiry() {
    whenever(provider.handleWebhook("{}", null))
      .thenReturn(WebhookEvent("CHECKOUT_COMPLETED", "fake_abc"))
    val session = CheckoutSession(USER_ID, "SPRINT_PASS", "fake",
      "fake_abc", "CREATED")
    whenever(sessions.findByProviderSessionId("fake_abc")).thenReturn(Optional.of(session))
    whenever(plans.findById("SPRINT_PASS")).thenReturn(Optional.of(sprintPass()))
    whenever(entitlements.existsByUserIdAndPlanCodeAndStatus(USER_ID, "SPRINT_PASS", "ACTIVE"))
      .thenReturn(false)
    whenever(sessions.save(any())).thenAnswer { i -> i.getArgument<CheckoutSession>(0) }
    whenever(entitlements.save(any())).thenAnswer { i -> i.getArgument<Entitlement>(0) }

    val response = service(true, "fake").handleWebhook("{}", null)

    assertTrue(response.received)
    assertEquals("PAID", session.status)
    val entitlement = argumentCaptor<Entitlement>()
    verify(entitlements).save(entitlement.capture())
    assertEquals("ACTIVE", entitlement.firstValue.status)
    assertNotNull(entitlement.firstValue.expiresAt)
    assertTrue(entitlement.firstValue.expiresAt!!
      .isAfter(Instant.now().plusSeconds(29L * 24 * 3600)))
  }

  @Test
  fun webhookLifetimeHasNoExpiry() {
    whenever(provider.handleWebhook("{}", null))
      .thenReturn(WebhookEvent("CHECKOUT_COMPLETED", "fake_life"))
    val session = CheckoutSession(USER_ID, "LIFETIME", "fake",
      "fake_life", "CREATED")
    whenever(sessions.findByProviderSessionId("fake_life")).thenReturn(Optional.of(session))
    whenever(plans.findById("LIFETIME")).thenReturn(Optional.of(lifetime()))
    whenever(entitlements.existsByUserIdAndPlanCodeAndStatus(USER_ID, "LIFETIME", "ACTIVE"))
      .thenReturn(false)
    whenever(sessions.save(any())).thenAnswer { i -> i.getArgument<CheckoutSession>(0) }
    whenever(entitlements.save(any())).thenAnswer { i -> i.getArgument<Entitlement>(0) }

    service(true, "fake").handleWebhook("{}", null)

    val entitlement = argumentCaptor<Entitlement>()
    verify(entitlements).save(entitlement.capture())
    assertEquals("ACTIVE", entitlement.firstValue.status)
    assertNull(entitlement.firstValue.expiresAt)
  }

  @Test
  fun webhookUnknownSessionIs400() {
    whenever(provider.handleWebhook("{}", null))
      .thenReturn(WebhookEvent("CHECKOUT_COMPLETED", "fake_nope"))
    whenever(sessions.findByProviderSessionId("fake_nope")).thenReturn(Optional.empty())

    val ex = assertThrows(ResponseStatusException::class.java) {
      service(true, "fake").handleWebhook("{}", null)
    }
    assertEquals(HttpStatus.BAD_REQUEST, ex.statusCode)
  }

  @Test
  fun webhookReplayIsIdempotent() {
    whenever(provider.handleWebhook("{}", null))
      .thenReturn(WebhookEvent("CHECKOUT_COMPLETED", "fake_abc"))
    val session = CheckoutSession(USER_ID, "SPRINT_PASS", "fake",
      "fake_abc", "PAID")
    whenever(sessions.findByProviderSessionId("fake_abc")).thenReturn(Optional.of(session))
    whenever(plans.findById("SPRINT_PASS")).thenReturn(Optional.of(sprintPass()))
    whenever(entitlements.existsByUserIdAndPlanCodeAndStatus(USER_ID, "SPRINT_PASS", "ACTIVE"))
      .thenReturn(true)
    whenever(sessions.save(any())).thenAnswer { i -> i.getArgument<CheckoutSession>(0) }

    val response = service(true, "fake").handleWebhook("{}", null)

    assertTrue(response.received)
    verify(entitlements, never()).save(any())
  }

  @Test
  fun webhookOtherEventTypeIsAcknowledgedWithoutAction() {
    whenever(provider.handleWebhook("{}", null))
      .thenReturn(WebhookEvent("charge.succeeded", null))

    val response = service(true, "fake").handleWebhook("{}", null)

    assertTrue(response.received)
    verifyNoInteractions(sessions, entitlements, plans)
  }

  @Test
  fun webhookWhenFlagOffIs404() {
    val ex = assertThrows(ResponseStatusException::class.java) {
      service(false, "fake").handleWebhook("{}", null)
    }
    assertEquals(HttpStatus.NOT_FOUND, ex.statusCode)
    verifyNoInteractions(provider)
  }

  @Test
  fun myEntitlementReturnsActive() {
    authenticate()
    val active = Entitlement(USER_ID, "SPRINT_PASS", "ACTIVE",
      Instant.now().plusSeconds(30L * 24 * 3600))
    whenever(entitlements.findFirstByUserIdAndStatusOrderByStartedAtDesc(USER_ID, "ACTIVE"))
      .thenReturn(Optional.of(active))

    val response = service(true, "fake").myEntitlement()

    assertEquals("SPRINT_PASS", response.planCode)
    assertEquals("ACTIVE", response.status)
    assertNotNull(response.expiresAt)
  }

  @Test
  fun myEntitlementFallsBackToPendingManual() {
    authenticate()
    val pending = Entitlement(USER_ID, "HUMAN_REVIEWED", "PENDING_MANUAL", null)
    whenever(entitlements.findFirstByUserIdAndStatusOrderByStartedAtDesc(USER_ID, "ACTIVE"))
      .thenReturn(Optional.empty())
    whenever(entitlements.findFirstByUserIdAndStatusOrderByStartedAtDesc(USER_ID, "PENDING_MANUAL"))
      .thenReturn(Optional.of(pending))

    val response = service(true, "fake").myEntitlement()

    assertEquals("HUMAN_REVIEWED", response.planCode)
    assertEquals("PENDING_MANUAL", response.status)
    assertNull(response.expiresAt)
  }

  @Test
  fun myEntitlementNoneWhenEmpty() {
    authenticate()
    whenever(entitlements.findFirstByUserIdAndStatusOrderByStartedAtDesc(USER_ID, "ACTIVE"))
      .thenReturn(Optional.empty())
    whenever(entitlements.findFirstByUserIdAndStatusOrderByStartedAtDesc(USER_ID, "PENDING_MANUAL"))
      .thenReturn(Optional.empty())

    val response = service(true, "fake").myEntitlement()

    assertNull(response.planCode)
    assertEquals("NONE", response.status)
    assertNull(response.expiresAt)
  }

  @Test
  fun myEntitlementWhenFlagOffIs404() {
    authenticate()
    val ex = assertThrows(ResponseStatusException::class.java) {
      service(false, "fake").myEntitlement()
    }
    assertEquals(HttpStatus.NOT_FOUND, ex.statusCode)
  }
}
