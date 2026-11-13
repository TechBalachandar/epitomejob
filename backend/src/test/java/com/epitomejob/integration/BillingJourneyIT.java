package com.epitomejob.integration;

import com.epitomejob.billing.repository.CheckoutSessionRepository;
import com.epitomejob.billing.repository.EntitlementRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W4 billing journey against real Postgres + real RustFS, fake provider
 * (features.billing.provider=fake via test properties — production code is
 * never edited to select the provider): status → plans → checkout →
 * webhook → entitlement ACTIVE, plus the 409 and HUMAN_REVIEWED paths.
 * Flyway V5 seeds the three plans.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BillingJourneyIT {

  @Container
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16")
      .withDatabaseName("epitomejob")
      .withUsername("epitomejob")
      .withPassword("epitomejob-dev");

  @Container
  static GenericContainer<?> rustfs = new GenericContainer<>("rustfs/rustfs:1.0.1")
      .withExposedPorts(9000, 9001)
      .withEnv("RUSTFS_ACCESS_KEY", "minioadmin")
      .withEnv("RUSTFS_SECRET_KEY", "minioadmin-dev")
      .withEnv("RUSTFS_ADDRESS", ":9000")
      .withEnv("RUSTFS_CONSOLE_ADDRESS", ":9001")
      .withEnv("RUSTFS_CONSOLE_ENABLE", "true")
      .waitingFor(Wait.forListeningPort());

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
    registry.add("app.minio.endpoint", () -> "http://localhost:" + rustfs.getMappedPort(9000));
    registry.add("app.minio.access-key", () -> "minioadmin");
    registry.add("app.minio.secret-key", () -> "minioadmin-dev");
    registry.add("app.minio.bucket", () -> "resumes");
    registry.add("app.jwt.secret", () -> "test-secret-that-is-at-least-32-chars-long!!");
    registry.add("features.billing.enabled", () -> "true");
    registry.add("features.billing.provider", () -> "fake");
  }

  @Autowired
  private TestRestTemplate rest;

  @Autowired
  private CheckoutSessionRepository checkoutSessions;

  @Autowired
  private EntitlementRepository entitlements;

  private HttpHeaders bearer(String token) {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(token);
    headers.setContentType(MediaType.APPLICATION_JSON);
    return headers;
  }

  private HttpHeaders jsonHeaders() {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    return headers;
  }

  @Test
  void billingJourneyFakeProvider() {
    String email = "billing-" + UUID.randomUUID() + "@example.com";

    var register = rest.postForEntity("/auth/register",
        new HttpEntity<>(Map.of("email", email, "fullName", "Billing User", "password", "Passw0rd!"),
            jsonHeaders()),
        Map.class);
    assertEquals(HttpStatus.CREATED, register.getStatusCode());
    String token = (String) register.getBody().get("accessToken");
    assertNotNull(token);
    UUID userId = UUID.fromString((String) register.getBody().get("userId"));
    assertNotNull(userId);

    // 1. Status: enabled, fake provider.
    var status = rest.exchange("/billing/status", HttpMethod.GET,
        new HttpEntity<>(bearer(token)), Map.class);
    assertEquals(HttpStatus.OK, status.getStatusCode());
    assertEquals(true, status.getBody().get("enabled"));
    assertEquals("fake", status.getBody().get("provider"));

    // 2. Plans: three seeded plans, seed order, exact prices.
    var plans = rest.exchange("/billing/plans", HttpMethod.GET,
        new HttpEntity<>(bearer(token)), List.class);
    assertEquals(HttpStatus.OK, plans.getStatusCode());
    assertEquals(3, plans.getBody().size());
    List<Map<String, Object>> planList = (List<Map<String, Object>>) plans.getBody();
    assertEquals("SPRINT_PASS", planList.get(0).get("code"));
    assertEquals(7900, planList.get(0).get("priceCents"));
    assertEquals(30, planList.get(0).get("durationDays"));
    assertEquals("LIFETIME", planList.get(1).get("code"));
    assertEquals(14900, planList.get(1).get("priceCents"));
    assertNull(planList.get(1).get("durationDays"));
    assertEquals("HUMAN_REVIEWED", planList.get(2).get("code"));
    assertEquals(29900, planList.get(2).get("priceCents"));

    // 3. No entitlement yet.
    var none = rest.exchange("/billing/entitlement/me", HttpMethod.GET,
        new HttpEntity<>(bearer(token)), Map.class);
    assertEquals(HttpStatus.OK, none.getStatusCode());
    assertNull(none.getBody().get("planCode"));
    assertEquals("NONE", none.getBody().get("status"));
    assertNull(none.getBody().get("expiresAt"));

    // 4. Checkout SPRINT_PASS: 201 CREATED, no Stripe URL from the fake provider.
    var checkout = rest.postForEntity("/billing/checkout",
        new HttpEntity<>(Map.of("planCode", "SPRINT_PASS"), bearer(token)), Map.class);
    assertEquals(HttpStatus.CREATED, checkout.getStatusCode());
    assertEquals("CREATED", checkout.getBody().get("status"));
    assertEquals("fake", checkout.getBody().get("provider"));
    assertNull(checkout.getBody().get("checkoutUrl"));
    String checkoutSessionId = (String) checkout.getBody().get("checkoutSessionId");
    assertNotNull(checkoutSessionId);

    // The webhook needs the provider session id, which lives on the stored row.
    String providerSessionId = checkoutSessions.findById(UUID.fromString(checkoutSessionId))
        .orElseThrow().getProviderSessionId();
    assertNotNull(providerSessionId);
    assertTrue(providerSessionId.startsWith("fake_"));

    // 5. Webhook: unknown session is 400.
    var unknown = rest.postForEntity("/billing/webhook",
        new HttpEntity<>(Map.of("providerSessionId", "fake_nope",
            "type", "CHECKOUT_COMPLETED"), jsonHeaders()), Map.class);
    assertEquals(HttpStatus.BAD_REQUEST, unknown.getStatusCode());

    // 6. Webhook: CHECKOUT_COMPLETED → 200 received, no auth needed.
    var webhook = rest.postForEntity("/billing/webhook",
        new HttpEntity<>(Map.of("providerSessionId", providerSessionId,
            "type", "CHECKOUT_COMPLETED"), jsonHeaders()), Map.class);
    assertEquals(HttpStatus.OK, webhook.getStatusCode());
    assertEquals(true, webhook.getBody().get("received"));

    // 7. Entitlement is ACTIVE with an expiry (SPRINT_PASS = 30 days).
    var entitlement = rest.exchange("/billing/entitlement/me", HttpMethod.GET,
        new HttpEntity<>(bearer(token)), Map.class);
    assertEquals(HttpStatus.OK, entitlement.getStatusCode());
    assertEquals("SPRINT_PASS", entitlement.getBody().get("planCode"));
    assertEquals("ACTIVE", entitlement.getBody().get("status"));
    assertNotNull(entitlement.getBody().get("expiresAt"));
    assertEquals(1L, entitlements.countByUserIdAndPlanCodeAndStatus(
        userId, "SPRINT_PASS", "ACTIVE"));

    // 8. Webhook replay is idempotent — still exactly one ACTIVE entitlement.
    var replay = rest.postForEntity("/billing/webhook",
        new HttpEntity<>(Map.of("providerSessionId", providerSessionId,
            "type", "CHECKOUT_COMPLETED"), jsonHeaders()), Map.class);
    assertEquals(HttpStatus.OK, replay.getStatusCode());
    assertEquals(1L, entitlements.countByUserIdAndPlanCodeAndStatus(
        userId, "SPRINT_PASS", "ACTIVE"));

    // 9. Second checkout of the same plan is 409 ALREADY_ENTITLED.
    var again = rest.postForEntity("/billing/checkout",
        new HttpEntity<>(Map.of("planCode", "SPRINT_PASS"), bearer(token)), Map.class);
    assertEquals(HttpStatus.CONFLICT, again.getStatusCode());
    assertEquals("ALREADY_ENTITLED", again.getBody().get("code"));

    // 10. Unknown plan is 404.
    var noPlan = rest.postForEntity("/billing/checkout",
        new HttpEntity<>(Map.of("planCode", "NOPE"), bearer(token)), Map.class);
    assertEquals(HttpStatus.NOT_FOUND, noPlan.getStatusCode());

    // 11. HUMAN_REVIEWED: 201 PENDING_MANUAL, no provider session, entitlement stays ACTIVE.
    var human = rest.postForEntity("/billing/checkout",
        new HttpEntity<>(Map.of("planCode", "HUMAN_REVIEWED"), bearer(token)), Map.class);
    assertEquals(HttpStatus.CREATED, human.getStatusCode());
    assertEquals("PENDING_MANUAL", human.getBody().get("status"));
    assertNull(human.getBody().get("checkoutUrl"));
    var still = rest.exchange("/billing/entitlement/me", HttpMethod.GET,
        new HttpEntity<>(bearer(token)), Map.class);
    assertEquals("SPRINT_PASS", still.getBody().get("planCode"));
    assertEquals("ACTIVE", still.getBody().get("status"));
  }
}
