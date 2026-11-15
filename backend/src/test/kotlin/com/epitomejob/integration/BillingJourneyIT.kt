package com.epitomejob.integration

import com.epitomejob.billing.repository.CheckoutSessionRepository
import com.epitomejob.billing.repository.EntitlementRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.UUID

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

  companion object {
    @Container
    @JvmStatic
    val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("pgvector/pgvector:pg16")
      .withDatabaseName("epitomejob")
      .withUsername("epitomejob")
      .withPassword("epitomejob-dev")

    @Container
    @JvmStatic
    val rustfs: GenericContainer<*> = GenericContainer("rustfs/rustfs:1.0.1")
      .withExposedPorts(9000, 9001)
      .withEnv("RUSTFS_ACCESS_KEY", "minioadmin")
      .withEnv("RUSTFS_SECRET_KEY", "minioadmin-dev")
      .withEnv("RUSTFS_ADDRESS", ":9000")
      .withEnv("RUSTFS_CONSOLE_ADDRESS", ":9001")
      .withEnv("RUSTFS_CONSOLE_ENABLE", "true")
      .waitingFor(Wait.forListeningPort())

    @DynamicPropertySource
    @JvmStatic
    fun properties(registry: DynamicPropertyRegistry) {
      registry.add("spring.datasource.url", postgres::getJdbcUrl)
      registry.add("spring.datasource.username", postgres::getUsername)
      registry.add("spring.datasource.password", postgres::getPassword)
      registry.add("app.minio.endpoint") { "http://localhost:${rustfs.getMappedPort(9000)}" }
      registry.add("app.minio.access-key") { "minioadmin" }
      registry.add("app.minio.secret-key") { "minioadmin-dev" }
      registry.add("app.minio.bucket") { "resumes" }
      registry.add("app.jwt.secret") { "test-secret-that-is-at-least-32-chars-long!!" }
      registry.add("features.billing.enabled") { "true" }
      registry.add("features.billing.provider") { "fake" }
    }
  }

  @Autowired
  private lateinit var rest: TestRestTemplate

  @Autowired
  private lateinit var checkoutSessions: CheckoutSessionRepository

  @Autowired
  private lateinit var entitlements: EntitlementRepository

  private fun bearer(token: String): HttpHeaders {
    val headers = HttpHeaders()
    headers.setBearerAuth(token)
    headers.setContentType(MediaType.APPLICATION_JSON)
    return headers
  }

  private fun jsonHeaders(): HttpHeaders {
    val headers = HttpHeaders()
    headers.setContentType(MediaType.APPLICATION_JSON)
    return headers
  }

  @Test
  @Suppress("UNCHECKED_CAST")
  fun billingJourneyFakeProvider() {
    val email = "billing-" + UUID.randomUUID() + "@example.com"

    val register = rest.postForEntity(
      "/auth/register",
      HttpEntity(
        mapOf("email" to email, "fullName" to "Billing User", "password" to "Passw0rd!"),
        jsonHeaders()
      ),
      Map::class.java
    )
    assertEquals(HttpStatus.CREATED, register.statusCode)
    val token = register.body?.get("accessToken") as String
    assertNotNull(token)
    val userId = UUID.fromString(register.body?.get("userId") as String)
    assertNotNull(userId)

    // 1. Status: enabled, fake provider.
    val status = rest.exchange(
      "/billing/status", HttpMethod.GET,
      HttpEntity(bearer(token)), Map::class.java
    )
    assertEquals(HttpStatus.OK, status.statusCode)
    assertEquals(true, status.body?.get("enabled"))
    assertEquals("fake", status.body?.get("provider"))

    // 2. Plans: three seeded plans, seed order, exact prices.
    val plans = rest.exchange(
      "/billing/plans", HttpMethod.GET,
      HttpEntity(bearer(token)), List::class.java
    )
    assertEquals(HttpStatus.OK, plans.statusCode)
    assertEquals(3, plans.body?.size)
    val planList = plans.body as List<Map<String, Any?>>
    assertEquals("SPRINT_PASS", planList[0]["code"])
    assertEquals(7900, planList[0]["priceCents"])
    assertEquals(30, planList[0]["durationDays"])
    assertEquals("LIFETIME", planList[1]["code"])
    assertEquals(14900, planList[1]["priceCents"])
    assertNull(planList[1]["durationDays"])
    assertEquals("HUMAN_REVIEWED", planList[2]["code"])
    assertEquals(29900, planList[2]["priceCents"])

    // 3. No entitlement yet.
    val none = rest.exchange(
      "/billing/entitlement/me", HttpMethod.GET,
      HttpEntity(bearer(token)), Map::class.java
    )
    assertEquals(HttpStatus.OK, none.statusCode)
    assertNull(none.body?.get("planCode"))
    assertEquals("NONE", none.body?.get("status"))
    assertNull(none.body?.get("expiresAt"))

    // 4. Checkout SPRINT_PASS: 201 CREATED, no Stripe URL from the fake provider.
    val checkout = rest.postForEntity(
      "/billing/checkout",
      HttpEntity(mapOf("planCode" to "SPRINT_PASS"), bearer(token)), Map::class.java
    )
    assertEquals(HttpStatus.CREATED, checkout.statusCode)
    assertEquals("CREATED", checkout.body?.get("status"))
    assertEquals("fake", checkout.body?.get("provider"))
    assertNull(checkout.body?.get("checkoutUrl"))
    val checkoutSessionId = checkout.body?.get("checkoutSessionId") as String
    assertNotNull(checkoutSessionId)

    // The webhook needs the provider session id, which lives on the stored row.
    val providerSessionId = checkoutSessions.findById(UUID.fromString(checkoutSessionId))
      .orElseThrow().providerSessionId
    assertNotNull(providerSessionId)
    assertTrue(providerSessionId.startsWith("fake_"))

    // 5. Webhook: unknown session is 400.
    val unknown = rest.postForEntity(
      "/billing/webhook",
      HttpEntity(
        mapOf("providerSessionId" to "fake_nope", "type" to "CHECKOUT_COMPLETED"),
        jsonHeaders()
      ),
      Map::class.java
    )
    assertEquals(HttpStatus.BAD_REQUEST, unknown.statusCode)

    // 6. Webhook: CHECKOUT_COMPLETED → 200 received, no auth needed.
    val webhook = rest.postForEntity(
      "/billing/webhook",
      HttpEntity(
        mapOf("providerSessionId" to providerSessionId, "type" to "CHECKOUT_COMPLETED"),
        jsonHeaders()
      ),
      Map::class.java
    )
    assertEquals(HttpStatus.OK, webhook.statusCode)
    assertEquals(true, webhook.body?.get("received"))

    // 7. Entitlement is ACTIVE with an expiry (SPRINT_PASS = 30 days).
    val entitlement = rest.exchange(
      "/billing/entitlement/me", HttpMethod.GET,
      HttpEntity(bearer(token)), Map::class.java
    )
    assertEquals(HttpStatus.OK, entitlement.statusCode)
    assertEquals("SPRINT_PASS", entitlement.body?.get("planCode"))
    assertEquals("ACTIVE", entitlement.body?.get("status"))
    assertNotNull(entitlement.body?.get("expiresAt"))
    assertEquals(
      1L,
      entitlements.countByUserIdAndPlanCodeAndStatus(userId, "SPRINT_PASS", "ACTIVE")
    )

    // 8. Webhook replay is idempotent — still exactly one ACTIVE entitlement.
    val replay = rest.postForEntity(
      "/billing/webhook",
      HttpEntity(
        mapOf("providerSessionId" to providerSessionId, "type" to "CHECKOUT_COMPLETED"),
        jsonHeaders()
      ),
      Map::class.java
    )
    assertEquals(HttpStatus.OK, replay.statusCode)
    assertEquals(
      1L,
      entitlements.countByUserIdAndPlanCodeAndStatus(userId, "SPRINT_PASS", "ACTIVE")
    )

    // 9. Second checkout of the same plan is 409 ALREADY_ENTITLED.
    val again = rest.postForEntity(
      "/billing/checkout",
      HttpEntity(mapOf("planCode" to "SPRINT_PASS"), bearer(token)), Map::class.java
    )
    assertEquals(HttpStatus.CONFLICT, again.statusCode)
    assertEquals("ALREADY_ENTITLED", again.body?.get("code"))

    // 10. Unknown plan is 404.
    val noPlan = rest.postForEntity(
      "/billing/checkout",
      HttpEntity(mapOf("planCode" to "NOPE"), bearer(token)), Map::class.java
    )
    assertEquals(HttpStatus.NOT_FOUND, noPlan.statusCode)

    // 11. HUMAN_REVIEWED: 201 PENDING_MANUAL, no provider session, entitlement stays ACTIVE.
    val human = rest.postForEntity(
      "/billing/checkout",
      HttpEntity(mapOf("planCode" to "HUMAN_REVIEWED"), bearer(token)), Map::class.java
    )
    assertEquals(HttpStatus.CREATED, human.statusCode)
    assertEquals("PENDING_MANUAL", human.body?.get("status"))
    assertNull(human.body?.get("checkoutUrl"))
    val still = rest.exchange(
      "/billing/entitlement/me", HttpMethod.GET,
      HttpEntity(bearer(token)), Map::class.java
    )
    assertEquals("SPRINT_PASS", still.body?.get("planCode"))
    assertEquals("ACTIVE", still.body?.get("status"))
  }
}
