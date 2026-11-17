package com.epitomejob.billing.controller

import com.epitomejob.billing.dto.BillingStatusResponse
import com.epitomejob.billing.dto.CheckoutResponse
import com.epitomejob.billing.dto.EntitlementResponse
import com.epitomejob.billing.dto.PlanDto
import com.epitomejob.billing.dto.WebhookResponse
import com.epitomejob.billing.service.BillingService
import com.epitomejob.common.exception.AlreadyEntitledException
import com.epitomejob.common.exception.BillingNotConfiguredException
import com.epitomejob.common.security.SecurityConfig
import com.epitomejob.identity.service.JwtService
import io.jsonwebtoken.Claims
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.UUID

@WebMvcTest(BillingController::class)
@Import(SecurityConfig::class)
class BillingControllerTest {

  companion object {
    private val USER_ID = UUID.randomUUID()
    private val SESSION_ID = UUID.randomUUID()
  }

  @Autowired
  private lateinit var mockMvc: MockMvc

  @MockitoBean
  private lateinit var service: BillingService

  @MockitoBean
  private lateinit var jwt: JwtService

  private fun authenticate() {
    val claims = mock<Claims>()
    whenever(claims.subject).thenReturn(USER_ID.toString())
    whenever(claims.get("role", String::class.java)).thenReturn("ROLE_USER")
    whenever(jwt.parse("test-token")).thenReturn(claims)
  }

  private fun seedPlans(): List<PlanDto> {
    return listOf(
      PlanDto("SPRINT_PASS", "Sprint Pass", 7900, "USD", 30,
        "30 days of full access — one-time payment.", true),
      PlanDto("LIFETIME", "Lifetime", 14900, "USD", null,
        "Lifetime access — one-time payment.", true),
      PlanDto("HUMAN_REVIEWED", "Human-Reviewed Package", 29900, "USD", null,
        "Human-reviewed resume + plan. Fulfilled manually by our team — we contact you after checkout.", true))
  }

  @Test
  fun statusIs200EvenWithoutOtherMocks() {
    authenticate()
    whenever(service.status()).thenReturn(BillingStatusResponse(true, "fake"))

    mockMvc.perform(get("/billing/status").header("Authorization", "Bearer test-token"))
      .andExpect(status().isOk)
      .andExpect(jsonPath("$.enabled").value(true))
      .andExpect(jsonPath("$.provider").value("fake"))
  }

  @Test
  fun statusWithoutTokenIsForbidden() {
    mockMvc.perform(get("/billing/status"))
      .andExpect(status().isForbidden)
  }

  @Test
  fun plansReturnsSeedOrder() {
    authenticate()
    whenever(service.listPlans()).thenReturn(seedPlans())

    mockMvc.perform(get("/billing/plans").header("Authorization", "Bearer test-token"))
      .andExpect(status().isOk)
      .andExpect(jsonPath("$[0].code").value("SPRINT_PASS"))
      .andExpect(jsonPath("$[0].priceCents").value(7900))
      .andExpect(jsonPath("$[0].durationDays").value(30))
      .andExpect(jsonPath("$[1].code").value("LIFETIME"))
      .andExpect(jsonPath("$[1].durationDays").doesNotExist())
      .andExpect(jsonPath("$[2].code").value("HUMAN_REVIEWED"))
      .andExpect(jsonPath("$[2].priceCents").value(29900))
  }

  @Test
  fun plansWhenFlagOffIs404() {
    authenticate()
    whenever(service.listPlans())
      .thenThrow(ResponseStatusException(HttpStatus.NOT_FOUND, "Billing is not enabled"))

    mockMvc.perform(get("/billing/plans").header("Authorization", "Bearer test-token"))
      .andExpect(status().isNotFound)
  }

  @Test
  fun plansWithoutTokenIsForbidden() {
    mockMvc.perform(get("/billing/plans"))
      .andExpect(status().isForbidden)
  }

  @Test
  fun checkoutReturns201() {
    authenticate()
    whenever(service.checkout(eq("SPRINT_PASS"))).thenReturn(
      CheckoutResponse(SESSION_ID, "fake", null, "CREATED"))

    mockMvc.perform(post("/billing/checkout").header("Authorization", "Bearer test-token")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"planCode\": \"SPRINT_PASS\"}"))
      .andExpect(status().isCreated)
      .andExpect(jsonPath("$.checkoutSessionId").value(SESSION_ID.toString()))
      .andExpect(jsonPath("$.provider").value("fake"))
      .andExpect(jsonPath("$.checkoutUrl").doesNotExist())
      .andExpect(jsonPath("$.status").value("CREATED"))
  }

  @Test
  fun checkoutHumanReviewedReturnsPendingManual() {
    authenticate()
    whenever(service.checkout(eq("HUMAN_REVIEWED"))).thenReturn(
      CheckoutResponse(SESSION_ID, "fake", null, "PENDING_MANUAL"))

    mockMvc.perform(post("/billing/checkout").header("Authorization", "Bearer test-token")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"planCode\": \"HUMAN_REVIEWED\"}"))
      .andExpect(status().isCreated)
      .andExpect(jsonPath("$.checkoutUrl").doesNotExist())
      .andExpect(jsonPath("$.status").value("PENDING_MANUAL"))
  }

  @Test
  fun checkoutAlreadyEntitledIs409WithCode() {
    authenticate()
    whenever(service.checkout(any())).thenThrow(AlreadyEntitledException())

    mockMvc.perform(post("/billing/checkout").header("Authorization", "Bearer test-token")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"planCode\": \"SPRINT_PASS\"}"))
      .andExpect(status().isConflict)
      .andExpect(jsonPath("$.code").value("ALREADY_ENTITLED"))
  }

  @Test
  fun checkoutStripeNotConfiguredIs503WithCodeAndMessage() {
    authenticate()
    whenever(service.checkout(any())).thenThrow(BillingNotConfiguredException())

    mockMvc.perform(post("/billing/checkout").header("Authorization", "Bearer test-token")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"planCode\": \"SPRINT_PASS\"}"))
      .andExpect(status().isServiceUnavailable)
      .andExpect(jsonPath("$.code").value("BILLING_NOT_CONFIGURED"))
      .andExpect(jsonPath("$.message").value("Billing is not connected yet — no payment is taken."))
  }

  @Test
  fun checkoutUnknownPlanIs404() {
    authenticate()
    whenever(service.checkout(any()))
      .thenThrow(ResponseStatusException(HttpStatus.NOT_FOUND, "Plan not found"))

    mockMvc.perform(post("/billing/checkout").header("Authorization", "Bearer test-token")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"planCode\": \"NOPE\"}"))
      .andExpect(status().isNotFound)
  }

  @Test
  fun webhookNeedsNoAuth() {
    whenever(service.handleWebhook(anyOrNull(), anyOrNull())).thenReturn(WebhookResponse(true))

    mockMvc.perform(post("/billing/webhook")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"providerSessionId\": \"fake_abc\", \"type\": \"CHECKOUT_COMPLETED\"}"))
      .andExpect(status().isOk)
      .andExpect(jsonPath("$.received").value(true))
  }

  @Test
  fun webhookUnknownSessionIs400() {
    whenever(service.handleWebhook(anyOrNull(), anyOrNull()))
      .thenThrow(ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown checkout session"))

    mockMvc.perform(post("/billing/webhook")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"providerSessionId\": \"fake_nope\", \"type\": \"CHECKOUT_COMPLETED\"}"))
      .andExpect(status().isBadRequest)
  }

  @Test
  fun entitlementMeReturnsActive() {
    authenticate()
    whenever(service.myEntitlement()).thenReturn(EntitlementResponse("SPRINT_PASS", "ACTIVE",
      Instant.parse("2026-12-12T00:00:00Z")))

    mockMvc.perform(get("/billing/entitlement/me").header("Authorization", "Bearer test-token"))
      .andExpect(status().isOk)
      .andExpect(jsonPath("$.planCode").value("SPRINT_PASS"))
      .andExpect(jsonPath("$.status").value("ACTIVE"))
      .andExpect(jsonPath("$.expiresAt").value("2026-12-12T00:00:00Z"))
  }

  @Test
  fun entitlementMeReturnsNone() {
    authenticate()
    whenever(service.myEntitlement()).thenReturn(EntitlementResponse.none())

    mockMvc.perform(get("/billing/entitlement/me").header("Authorization", "Bearer test-token"))
      .andExpect(status().isOk)
      .andExpect(jsonPath("$.planCode").doesNotExist())
      .andExpect(jsonPath("$.status").value("NONE"))
      .andExpect(jsonPath("$.expiresAt").doesNotExist())
  }

  @Test
  fun entitlementMeWithoutTokenIsForbidden() {
    mockMvc.perform(get("/billing/entitlement/me"))
      .andExpect(status().isForbidden)
  }
}
