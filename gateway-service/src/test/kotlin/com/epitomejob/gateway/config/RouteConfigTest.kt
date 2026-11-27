package com.epitomejob.gateway.config

import com.epitomejob.gateway.filter.JwtAuthFilter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.cloud.gateway.route.RouteDefinition
import org.springframework.cloud.gateway.route.RouteDefinitionLocator
import org.springframework.core.env.Environment

/**
 * Route-table test: proves every expected route exists, points at the right
 * discovery service, carries the resilience filters, and that the auth /
 * security split is as specified. Runs against the real
 * `application.yml` (no route is hand-built in the test).
 *
 * Eureka is disabled for this test (no registry is running under test);
 * route definitions do not need a live registry to be asserted.
 *
 * 14 routes. Twelve point at the monolith (lb://epitomejob-backend);
 * `resume-builder` points at the Resume Builder service
 * (lb://resume-builder-service); `mcp` is deliberately a static
 * http:// URI (the Python mcp-service does not register with Eureka, so
 * discovery cannot resolve it — see the header comment in
 * application.yml).
 */
@SpringBootTest(
  properties = [
    "eureka.client.enabled=false",
    "spring.cloud.discovery.enabled=false"
  ]
)
class RouteConfigTest {

  companion object {
    // Path -> destination, from the monolith's controllers at main b59dc45,
    // plus the resume-builder route (RB1 split, added in ba52bddc).
    private val EXPECTED_ROUTES = mapOf(
      "auth" to "/auth/**",
      "profiles" to "/profiles/**",
      "resumes" to "/resumes/**",
      "jobs" to "/jobs/**",
      "matches" to "/matches/**",
      "tracker" to "/tracker/**",
      "interviews" to "/interviews/**",
      "coach" to "/coach/**",
      "approvals" to "/approvals/**",
      "notifications" to "/notifications/**",
      "billing" to "/billing/**",
      "plans" to "/plans/**",
      "mcp" to "/mcp/**",
      "resume-builder" to "/resume-builder/**"
    )
  }

  @Autowired
  private lateinit var routeDefinitionLocator: RouteDefinitionLocator

  @Autowired
  private lateinit var environment: Environment

  @Autowired
  private lateinit var rateLimitProperties: RateLimitConfig.RateLimitProperties

  private fun routes(): List<RouteDefinition> =
    routeDefinitionLocator.routeDefinitions.collectList().block()!!

  private fun routeById(id: String): RouteDefinition =
    routes().firstOrNull { it.id == id }
      ?: throw AssertionError("Missing route: $id")

  private fun filterNames(route: RouteDefinition): Set<String> =
    route.filters.map { it.name }.toSet()

  private fun pathPredicate(route: RouteDefinition): String =
    route.predicates.filter { it.name == "Path" }
      .flatMap { it.args.values }
      .firstOrNull()
      ?: throw AssertionError("No Path predicate on ${route.id}")

  @Test
  fun everyExpectedRouteExistsWithItsPath() {
    val routes = routes()
    assertEquals(
      EXPECTED_ROUTES.size, routes.size,
      "Route count changed — update EXPECTED_ROUTES and the route table in the README"
    )
    EXPECTED_ROUTES.forEach { (id, path) ->
      assertEquals(path, pathPredicate(routeById(id)), "Path mismatch on route $id")
    }
  }

  @Test
  fun monolithRoutesUseDiscoveryNotAHardcodedHost() {
    // The 12 monolith routes resolve the backend through Eureka. The two
    // non-monolith routes are asserted separately: mcp is a static http://
    // URI by design (see mcpRoutePointsAtTheMcpService), resume-builder is
    // lb://resume-builder-service (see resumeBuilderRoutePointsAtTheResumeBuilderService).
    EXPECTED_ROUTES.keys
      .filter { it != "mcp" && it != "resume-builder" }
      .forEach { id ->
        val uri = routeById(id).uri
        assertEquals("lb", uri.scheme, "Route $id must use lb://")
        assertEquals(
          "epitomejob-backend", uri.host,
          "Route $id must resolve the backend through Eureka"
        )
      }
  }

  @Test
  fun mcpRoutePointsAtTheMcpService() {
    // Deliberately NOT lb://: the Python mcp-service does not register with
    // Eureka (no Java client), so discovery cannot resolve it. The name
    // mcp-server resolves via compose DNS and the k8s Service of the same
    // name. See the header comment in application.yml.
    val uri = routeById("mcp").uri
    assertEquals("http", uri.scheme)
    assertEquals("mcp-server", uri.host)
    assertEquals(8001, uri.port)
  }

  @Test
  fun resumeBuilderRoutePointsAtTheResumeBuilderService() {
    // RB1 split: the Resume Builder is its own Eureka service, so the
    // route uses discovery like the monolith routes, with its own host.
    val uri = routeById("resume-builder").uri
    assertEquals("lb", uri.scheme)
    assertEquals("resume-builder-service", uri.host)
  }

  @Test
  fun everyRouteHasRateLimiterRetryAndCircuitBreaker() {
    EXPECTED_ROUTES.keys.forEach { id ->
      val filters = filterNames(routeById(id))
      assertTrue(filters.contains("RequestRateLimiter"), "$id: missing RequestRateLimiter")
      assertTrue(filters.contains("Retry"), "$id: missing Retry")
      assertTrue(filters.contains("CircuitBreaker"), "$id: missing CircuitBreaker")
    }
  }

  @Test
  fun coachRouteHasTheThreadPoolBulkhead() {
    assertTrue(
      filterNames(routeById("coach")).contains("ThreadPoolBulkhead"),
      "coach is the slow LLM route and must run on its own thread pool"
    )
    EXPECTED_ROUTES.keys
      .filter { it != "coach" }
      .forEach { id ->
        assertTrue(
          !filterNames(routeById(id)).contains("ThreadPoolBulkhead"),
          "$id: thread-pool bulkhead belongs only on the coach route"
        )
      }
  }

  @Test
  fun coachRouteUsesTheCoachCircuitBreakerNotBackend() {
    // Issue #11: the coach route used the "backend" circuit breaker, whose
    // 3s time limiter cancelled every chat turn (an Ollama call takes far
    // longer than 3s) and the fallback 503 made Coach unusable. The coach
    // route must use the dedicated "coach" instance (120s time limit).
    val cb = routeById("coach").filters
      .firstOrNull { it.name == "CircuitBreaker" }
      ?: throw AssertionError("No CircuitBreaker filter on coach")
    assertEquals(
      "coach", cb.args["name"],
      "coach must use the coach circuit breaker (120s), not backend (3s)"
    )
    EXPECTED_ROUTES.keys
      .filter { it != "coach" }
      .forEach { id ->
        val other = routeById(id).filters
          .firstOrNull { it.name == "CircuitBreaker" }
          ?: throw AssertionError("No CircuitBreaker filter on $id")
        assertTrue(
          "coach" != other.args["name"],
          "$id: the coach circuit breaker belongs only on the coach route"
        )
      }
  }

  @Test
  fun retryIsGetOnlyAndCircuitBreakerHasFallbackAndStatusCodes() {
    EXPECTED_ROUTES.keys.forEach { id ->
      val route = routeById(id)
      val retry = route.filters
        .firstOrNull { it.name == "Retry" }
        ?: throw AssertionError("No Retry filter on $id")
      assertEquals("3", retry.args["retries"], "$id: retries must be 3")
      assertEquals("GET", retry.args["methods"], "$id: retry must apply to GETs only")
      val cb = route.filters
        .firstOrNull { it.name == "CircuitBreaker" }
        ?: throw AssertionError("No CircuitBreaker filter on $id")
      // Assert the key exists first: a missing fallbackUri must fail naming
      // the route, not throw an NPE from the startsWith call below.
      assertTrue(cb.args.containsKey("fallbackUri"), "$id: circuit breaker must define a fallbackUri")
      assertTrue(
        cb.args["fallbackUri"]!!.startsWith("forward:/fallback/"),
        "$id: circuit breaker must fall back to a local controller"
      )
      assertTrue(
        cb.args.containsKey("statusCodes"),
        "$id: 5xx responses must trip the breaker, not just exceptions"
      )
    }
  }

  @Test
  fun authIsPublicAndEverythingElseIsSecuredByTheJwtFilter() {
    // The split lives in JwtAuthFilter (a GlobalFilter, not a route filter):
    // /auth/** passes with no token; every other route 401s without one.
    // Covered behaviourally in JwtAuthFilterTest; here the routing side:
    assertTrue(JwtAuthFilter.matches("/auth/**", "/auth/login"))
    assertTrue(JwtAuthFilter.matches("/actuator/**", "/actuator/health"))
    EXPECTED_ROUTES.values
      .filter { !it.startsWith("/auth/") }
      .forEach { path ->
        assertTrue(
          !JwtAuthFilter.matches("/auth/**", path.replace("/**", "/me")),
          "$path must NOT be public"
        )
      }
  }

  @Test
  fun rateLimiterConfigIsPresentWithTheDocumentedLimit() {
    // 100 req/min per user/IP: 2 tokens/sec refill, burst of 100.
    assertEquals(2, rateLimitProperties.replenishRate)
    assertEquals(100, rateLimitProperties.burstCapacity)
    assertEquals(1, rateLimitProperties.requestedTokens)
  }

  @Test
  fun resilienceDefaultsArePresentInConfig() {
    // Breaker: 50% over 10 calls, 30s open, 3 half-open probes.
    assertEquals(
      "50", environment.getProperty(
        "resilience4j.circuitbreaker.instances.backend.failureRateThreshold"
      )
    )
    assertEquals(
      "10", environment.getProperty(
        "resilience4j.circuitbreaker.instances.backend.slidingWindowSize"
      )
    )
    assertEquals(
      "30s", environment.getProperty(
        "resilience4j.circuitbreaker.instances.backend.waitDurationInOpenState"
      )
    )
    assertEquals(
      "3", environment.getProperty(
        "resilience4j.circuitbreaker.instances.backend.permittedNumberOfCallsInHalfOpenState"
      )
    )
    // Time limiter: 3s per attempt.
    assertEquals(
      "3s", environment.getProperty(
        "resilience4j.timelimiter.instances.backend.timeoutDuration"
      )
    )
    assertEquals(
      "3s", environment.getProperty(
        "resilience4j.timelimiter.instances.mcp-service.timeoutDuration"
      )
    )
    // Coach exception (issue #11): the LLM route gets 120s, matching the
    // agent-brain Ollama timeout — a 3s limit cancels every chat turn.
    assertEquals(
      "120s", environment.getProperty(
        "resilience4j.timelimiter.instances.coach.timeoutDuration"
      )
    )
    // Resume-builder Co-Pilot also calls Ollama via agent-brain (e.g.
    // 3-5 cover-letter options), so it gets 120s too — a 3s limit
    // cancelled every generation and the fallback 503 made Cover Letter
    // generation unusable.
    assertEquals(
      "120s", environment.getProperty(
        "resilience4j.timelimiter.instances.resume-builder.timeoutDuration"
      )
    )
    // Semaphore bulkhead on the fast routes (fail fast, no queueing).
    assertEquals(
      "0", environment.getProperty(
        "resilience4j.bulkhead.instances.backend.maxWaitDuration"
      )
    )
    // Health endpoint exposed.
    assertEquals(
      "health,info", environment.getProperty(
        "management.endpoints.web.exposure.include"
      )
    )
  }
}
