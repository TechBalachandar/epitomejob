package com.epitomejob.billing.controller;

import com.epitomejob.billing.dto.BillingStatusResponse;
import com.epitomejob.billing.dto.CheckoutResponse;
import com.epitomejob.billing.dto.EntitlementResponse;
import com.epitomejob.billing.dto.PlanDto;
import com.epitomejob.billing.dto.WebhookResponse;
import com.epitomejob.billing.service.BillingService;
import com.epitomejob.common.exception.AlreadyEntitledException;
import com.epitomejob.common.exception.BillingNotConfiguredException;
import com.epitomejob.common.security.SecurityConfig;
import com.epitomejob.identity.service.JwtService;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BillingController.class)
@Import(SecurityConfig.class)
class BillingControllerTest {

  private static final UUID USER_ID = UUID.randomUUID();
  private static final UUID SESSION_ID = UUID.randomUUID();

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private BillingService service;

  @MockitoBean
  private JwtService jwt;

  private void authenticate() {
    Claims claims = mock(Claims.class);
    when(claims.getSubject()).thenReturn(USER_ID.toString());
    when(claims.get("role", String.class)).thenReturn("ROLE_USER");
    when(jwt.parse("test-token")).thenReturn(claims);
  }

  private List<PlanDto> seedPlans() {
    return List.of(
        new PlanDto("SPRINT_PASS", "Sprint Pass", 7900, "USD", 30,
            "30 days of full access — one-time payment.", true),
        new PlanDto("LIFETIME", "Lifetime", 14900, "USD", null,
            "Lifetime access — one-time payment.", true),
        new PlanDto("HUMAN_REVIEWED", "Human-Reviewed Package", 29900, "USD", null,
            "Human-reviewed resume + plan. Fulfilled manually by our team — we contact you after checkout.", true));
  }

  @Test
  void statusIs200EvenWithoutOtherMocks() throws Exception {
    authenticate();
    when(service.status()).thenReturn(new BillingStatusResponse(true, "fake"));

    mockMvc.perform(get("/billing/status").header("Authorization", "Bearer test-token"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.enabled").value(true))
        .andExpect(jsonPath("$.provider").value("fake"));
  }

  @Test
  void statusWithoutTokenIsForbidden() throws Exception {
    mockMvc.perform(get("/billing/status"))
        .andExpect(status().isForbidden());
  }

  @Test
  void plansReturnsSeedOrder() throws Exception {
    authenticate();
    when(service.listPlans()).thenReturn(seedPlans());

    mockMvc.perform(get("/billing/plans").header("Authorization", "Bearer test-token"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].code").value("SPRINT_PASS"))
        .andExpect(jsonPath("$[0].priceCents").value(7900))
        .andExpect(jsonPath("$[0].durationDays").value(30))
        .andExpect(jsonPath("$[1].code").value("LIFETIME"))
        .andExpect(jsonPath("$[1].durationDays").doesNotExist())
        .andExpect(jsonPath("$[2].code").value("HUMAN_REVIEWED"))
        .andExpect(jsonPath("$[2].priceCents").value(29900));
  }

  @Test
  void plansWhenFlagOffIs404() throws Exception {
    authenticate();
    when(service.listPlans())
        .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Billing is not enabled"));

    mockMvc.perform(get("/billing/plans").header("Authorization", "Bearer test-token"))
        .andExpect(status().isNotFound());
  }

  @Test
  void plansWithoutTokenIsForbidden() throws Exception {
    mockMvc.perform(get("/billing/plans"))
        .andExpect(status().isForbidden());
  }

  @Test
  void checkoutReturns201() throws Exception {
    authenticate();
    when(service.checkout(eq("SPRINT_PASS"))).thenReturn(
        new CheckoutResponse(SESSION_ID, "fake", null, "CREATED"));

    mockMvc.perform(post("/billing/checkout").header("Authorization", "Bearer test-token")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"planCode\": \"SPRINT_PASS\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.checkoutSessionId").value(SESSION_ID.toString()))
        .andExpect(jsonPath("$.provider").value("fake"))
        .andExpect(jsonPath("$.checkoutUrl").doesNotExist())
        .andExpect(jsonPath("$.status").value("CREATED"));
  }

  @Test
  void checkoutHumanReviewedReturnsPendingManual() throws Exception {
    authenticate();
    when(service.checkout(eq("HUMAN_REVIEWED"))).thenReturn(
        new CheckoutResponse(SESSION_ID, "fake", null, "PENDING_MANUAL"));

    mockMvc.perform(post("/billing/checkout").header("Authorization", "Bearer test-token")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"planCode\": \"HUMAN_REVIEWED\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.checkoutUrl").doesNotExist())
        .andExpect(jsonPath("$.status").value("PENDING_MANUAL"));
  }

  @Test
  void checkoutAlreadyEntitledIs409WithCode() throws Exception {
    authenticate();
    when(service.checkout(any())).thenThrow(new AlreadyEntitledException());

    mockMvc.perform(post("/billing/checkout").header("Authorization", "Bearer test-token")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"planCode\": \"SPRINT_PASS\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ALREADY_ENTITLED"));
  }

  @Test
  void checkoutStripeNotConfiguredIs503WithCodeAndMessage() throws Exception {
    authenticate();
    when(service.checkout(any())).thenThrow(new BillingNotConfiguredException());

    mockMvc.perform(post("/billing/checkout").header("Authorization", "Bearer test-token")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"planCode\": \"SPRINT_PASS\"}"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.code").value("BILLING_NOT_CONFIGURED"))
        .andExpect(jsonPath("$.message").value("Billing is not connected yet — no payment is taken."));
  }

  @Test
  void checkoutUnknownPlanIs404() throws Exception {
    authenticate();
    when(service.checkout(any()))
        .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Plan not found"));

    mockMvc.perform(post("/billing/checkout").header("Authorization", "Bearer test-token")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"planCode\": \"NOPE\"}"))
        .andExpect(status().isNotFound());
  }

  @Test
  void webhookNeedsNoAuth() throws Exception {
    when(service.handleWebhook(any(), any())).thenReturn(new WebhookResponse(true));

    mockMvc.perform(post("/billing/webhook")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"providerSessionId\": \"fake_abc\", \"type\": \"CHECKOUT_COMPLETED\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.received").value(true));
  }

  @Test
  void webhookUnknownSessionIs400() throws Exception {
    when(service.handleWebhook(any(), any()))
        .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown checkout session"));

    mockMvc.perform(post("/billing/webhook")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"providerSessionId\": \"fake_nope\", \"type\": \"CHECKOUT_COMPLETED\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void entitlementMeReturnsActive() throws Exception {
    authenticate();
    when(service.myEntitlement()).thenReturn(new EntitlementResponse("SPRINT_PASS", "ACTIVE",
        Instant.parse("2026-12-12T00:00:00Z")));

    mockMvc.perform(get("/billing/entitlement/me").header("Authorization", "Bearer test-token"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.planCode").value("SPRINT_PASS"))
        .andExpect(jsonPath("$.status").value("ACTIVE"))
        .andExpect(jsonPath("$.expiresAt").value("2026-12-12T00:00:00Z"));
  }

  @Test
  void entitlementMeReturnsNone() throws Exception {
    authenticate();
    when(service.myEntitlement()).thenReturn(EntitlementResponse.none());

    mockMvc.perform(get("/billing/entitlement/me").header("Authorization", "Bearer test-token"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.planCode").doesNotExist())
        .andExpect(jsonPath("$.status").value("NONE"))
        .andExpect(jsonPath("$.expiresAt").doesNotExist());
  }

  @Test
  void entitlementMeWithoutTokenIsForbidden() throws Exception {
    mockMvc.perform(get("/billing/entitlement/me"))
        .andExpect(status().isForbidden());
  }
}
