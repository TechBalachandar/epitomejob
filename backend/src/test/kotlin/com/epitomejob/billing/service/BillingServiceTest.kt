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
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
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
    Mockito.`when`(plans.findByActiveTrue()).thenReturn(listOf(lifetime(), humanReviewed(), sprintPass()))

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
    Mockito.`when`(plans.findById("NOPE")).thenReturn(Optional.empty())

    val ex = assertThrows(ResponseStatusException::class.java) {
      service(true, "fake").checkout("NOPE")
    }
    assertEquals(HttpStatus.NOT_FOUND, ex.statusCode)
    verifyNoInteractions(provider)
  }

  @Test
  fun checkoutAlreadyEntitledIs409AndNeverCallsProvider() {
    authenticate()
    Mockito.`when`(plans.findById("SPRINT_PASS")).thenReturn(Optional.of(sprintPass()))
    Mockito.`when`(entitlements.existsByUserIdAndPlanCodeAndStatus(USER_ID, "SPRINT_PASS", "ACTIVE"))
      .thenReturn(true)

    assertThrows(AlreadyEntitledException::class.java) {
      service(true, "fake").checkout("SPRINT_PASS")
    }
    verifyNoInteractions(provider)
  }

  @Test
  fun checkoutHumanReviewedCreatesPendingManualAndNoProviderSession() {
    authenticate()
    Mockito.`when`(plans.findById("HUMAN_REVIEWED")).thenReturn(Optional.of(humanReviewed()))
    Mockito.`when`(entitlements.existsByUserIdAndPlanCodeAndStatus(USER_ID, "HUMAN_REVIEWED", "ACTIVE"))
      .thenReturn(false)
    Mockito.`when`(entitlements.save(any())).thenAnswer { i -> i.getArgument<Entitlement>(0) }
    Mockito.`when`(sessions.save(any())).thenAnswer { i -> i.getArgument<CheckoutSession>(0) }

    val response: CheckoutResponse = service(true, "fake").checkout("HUMAN_REVIEWED")

    assertEquals("PENDING_MANUAL", response.status)
    assertNull(response.checkoutUrl)
    assertNotNull(response.checkoutSessionId)
    verifyNoInteractions(provider)
    val entitlement = ArgumentCaptor.forClass(Entitlement::class.java)
    verify(entitlements).save(entitlement.capture())
    assertEquals("PENDING_MANUAL", entitlement.value.status)
    assertNull(entitlement.value.expiresAt)
    val session = ArgumentCaptor.forClass(CheckoutSession::class.java)
    verify(sessions).save(session.capture())
    assertNull(session.value.providerSessionId)
    assertEquals("CREATED", session.value.status)
  }

  @Test
  fun checkoutCreatesSessionRowWithProviderResult() {
    authenticate()
    Mockito.`when`(plans.findById("SPRINT_PASS")).thenReturn(Optional.of(sprintPass()))
    Mockito.`when`(entitlements.existsByUserIdAndPlanCodeAndStatus(USER_ID, "SPRINT_PASS", "ACTIVE"))
      .thenReturn(false)
    Mockito.`when`(provider.createCheckoutSession(eq(USER_ID), any(Plan::class.java)))
      .thenReturn(CheckoutSessionResult("fake_abc", null))
    Mockito.`when`(sessions.save(any())).thenAnswer { i -> i.getArgument<CheckoutSession>(0) }

    val response: CheckoutResponse = service(true, "fake").checkout("SPRINT_PASS")

    assertEquals("CREATED", response.status)
    assertEquals("fake", response.provider)
    assertNull(response.checkoutUrl)
    val session = ArgumentCaptor.forClass(CheckoutSession::class.java)
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
    Mockito.`when`(provider.handleWebhook("{}", null))
      .thenReturn(WebhookEvent("CHECKOUT_COMPLETED", "fake_abc"))
    val session = CheckoutSession(USER_ID, "SPRINT_PASS", "fake",
      "fake_abc", "CREATED")
    Mockito.`when`(sessions.findByProviderSessionId("fake_abc")).thenReturn(Optional.of(session))
    Mockito.`when`(plans.findById("SPRINT_PASS")).thenReturn(Optional.of(sprintPass()))
    Mockito.`when`(entitlements.existsByUserIdAndPlanCodeAndStatus(USER_ID, "SPRINT_PASS", "ACTIVE"))
      .thenReturn(false)
    Mockito.`when`(sessions.save(any())).thenAnswer { i -> i.getArgument<CheckoutSession>(0) }
    Mockito.`when`(entitlements.save(any())).thenAnswer { i -> i.getArgument<Entitlement>(0) }

    val response = service(true, "fake").handleWebhook("{}", null)

    assertTrue(response.received)
    assertEquals("PAID", session.status)
    val entitlement = ArgumentCaptor.forClass(Entitlement::class.java)
    verify(entitlements).save(entitlement.capture())
    assertEquals("ACTIVE", entitlement.value.status)
    assertNotNull(entitlement.value.expiresAt)
    assertTrue(entitlement.value.expiresAt!!
      .isAfter(Instant.now().plusSeconds(29L * 24 * 3600)))
  }

  @Test
  fun webhookLifetimeHasNoExpiry() {
    Mockito.`when`(provider.handleWebhook("{}", null))
      .thenReturn(WebhookEvent("CHECKOUT_COMPLETED", "fake_life"))
    val session = CheckoutSession(USER_ID, "LIFETIME", "fake",
      "fake_life", "CREATED")
    Mockito.`when`(sessions.findByProviderSessionId("fake_life")).thenReturn(Optional.of(session))
    Mockito.`when`(plans.findById("LIFETIME")).thenReturn(Optional.of(lifetime()))
    Mockito.`when`(entitlements.existsByUserIdAndPlanCodeAndStatus(USER_ID, "LIFETIME", "ACTIVE"))
      .thenReturn(false)
    Mockito.`when`(sessions.save(any())).thenAnswer { i -> i.getArgument<CheckoutSession>(0) }
    Mockito.`when`(entitlements.save(any())).thenAnswer { i -> i.getArgument<Entitlement>(0) }

    service(true, "fake").handleWebhook("{}", null)

    val entitlement = ArgumentCaptor.forClass(Entitlement::class.java)
    verify(entitlements).save(entitlement.capture())
    assertEquals("ACTIVE", entitlement.value.status)
    assertNull(entitlement.value.expiresAt)
  }

  @Test
  fun webhookUnknownSessionIs400() {
    Mockito.`when`(provider.handleWebhook("{}", null))
      .thenReturn(WebhookEvent("CHECKOUT_COMPLETED", "fake_nope"))
    Mockito.`when`(sessions.findByProviderSessionId("fake_nope")).thenReturn(Optional.empty())

    val ex = assertThrows(ResponseStatusException::class.java) {
      service(true, "fake").handleWebhook("{}", null)
    }
    assertEquals(HttpStatus.BAD_REQUEST, ex.statusCode)
  }

  @Test
  fun webhookReplayIsIdempotent() {
    Mockito.`when`(provider.handleWebhook("{}", null))
      .thenReturn(WebhookEvent("CHECKOUT_COMPLETED", "fake_abc"))
    val session = CheckoutSession(USER_ID, "SPRINT_PASS", "fake",
      "fake_abc", "PAID")
    Mockito.`when`(sessions.findByProviderSessionId("fake_abc")).thenReturn(Optional.of(session))
    Mockito.`when`(plans.findById("SPRINT_PASS")).thenReturn(Optional.of(sprintPass()))
    Mockito.`when`(entitlements.existsByUserIdAndPlanCodeAndStatus(USER_ID, "SPRINT_PASS", "ACTIVE"))
      .thenReturn(true)
    Mockito.`when`(sessions.save(any())).thenAnswer { i -> i.getArgument<CheckoutSession>(0) }

    val response = service(true, "fake").handleWebhook("{}", null)

    assertTrue(response.received)
    verify(entitlements, never()).save(any())
  }

  @Test
  fun webhookOtherEventTypeIsAcknowledgedWithoutAction() {
    Mockito.`when`(provider.handleWebhook("{}", null))
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
    Mockito.`when`(entitlements.findFirstByUserIdAndStatusOrderByStartedAtDesc(USER_ID, "ACTIVE"))
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
    Mockito.`when`(entitlements.findFirstByUserIdAndStatusOrderByStartedAtDesc(USER_ID, "ACTIVE"))
      .thenReturn(Optional.empty())
    Mockito.`when`(entitlements.findFirstByUserIdAndStatusOrderByStartedAtDesc(USER_ID, "PENDING_MANUAL"))
      .thenReturn(Optional.of(pending))

    val response = service(true, "fake").myEntitlement()

    assertEquals("HUMAN_REVIEWED", response.planCode)
    assertEquals("PENDING_MANUAL", response.status)
    assertNull(response.expiresAt)
  }

  @Test
  fun myEntitlementNoneWhenEmpty() {
    authenticate()
    Mockito.`when`(entitlements.findFirstByUserIdAndStatusOrderByStartedAtDesc(USER_ID, "ACTIVE"))
      .thenReturn(Optional.empty())
    Mockito.`when`(entitlements.findFirstByUserIdAndStatusOrderByStartedAtDesc(USER_ID, "PENDING_MANUAL"))
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
