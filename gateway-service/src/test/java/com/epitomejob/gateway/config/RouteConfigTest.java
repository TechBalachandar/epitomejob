package com.epitomejob.gateway.config;

import com.epitomejob.gateway.filter.JwtAuthFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import org.springframework.core.env.Environment;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Route-table test: proves every expected route exists, points at the right
 * discovery service, carries the resilience filters, and that the auth /
 * security split is as specified. Runs against the real
 * {@code application.yml} (no route is hand-built in the test).
 *
 * <p>Eureka is disabled for this test (no registry is running under test);
 * route definitions do not need a live registry to be asserted.
 */
@SpringBootTest(properties = {
    "eureka.client.enabled=false",
    "spring.cloud.discovery.enabled=false"
})
class RouteConfigTest {

  // Path -> destination, from the monolith's controllers at main b59dc45.
  private static final Map<String, String> EXPECTED_ROUTES = Map.ofEntries(
      Map.entry("auth", "/auth/**"),
      Map.entry("profiles", "/profiles/**"),
      Map.entry("resumes", "/resumes/**"),
      Map.entry("jobs", "/jobs/**"),
      Map.entry("matches", "/matches/**"),
      Map.entry("tracker", "/tracker/**"),
      Map.entry("interviews", "/interviews/**"),
      Map.entry("coach", "/coach/**"),
      Map.entry("approvals", "/approvals/**"),
      Map.entry("notifications", "/notifications/**"),
      Map.entry("billing", "/billing/**"),
      Map.entry("plans", "/plans/**"),
      Map.entry("mcp", "/mcp/**"));

  @Autowired
  private RouteDefinitionLocator routeDefinitionLocator;

  @Autowired
  private Environment environment;

  @Autowired
  private RateLimitConfig.RateLimitProperties rateLimitProperties;

  private List<RouteDefinition> routes() {
    return routeDefinitionLocator.getRouteDefinitions().collectList().block();
  }

  private RouteDefinition routeById(String id) {
    return routes().stream()
        .filter(r -> r.getId().equals(id))
        .findFirst()
        .orElseThrow(() -> new AssertionError("Missing route: " + id));
  }

  private static Set<String> filterNames(RouteDefinition route) {
    return route.getFilters().stream()
        .map(f -> f.getName())
        .collect(Collectors.toSet());
  }

  private static String pathPredicate(RouteDefinition route) {
    return route.getPredicates().stream()
        .filter(p -> p.getName().equals("Path"))
        .flatMap(p -> p.getArgs().values().stream())
        .findFirst()
        .orElseThrow(() -> new AssertionError("No Path predicate on " + route.getId()));
  }

  @Test
  void everyExpectedRouteExistsWithItsPath() {
    List<RouteDefinition> routes = routes();
    assertEquals(EXPECTED_ROUTES.size(), routes.size(),
        "Route count changed — update EXPECTED_ROUTES and the route table in the README");
    EXPECTED_ROUTES.forEach((id, path) ->
        assertEquals(path, pathPredicate(routeById(id)), "Path mismatch on route " + id));
  }

  @Test
  void monolithRoutesUseDiscoveryNotAHardcodedHost() {
    EXPECTED_ROUTES.keySet().stream()
        .filter(id -> !id.equals("mcp"))
        .forEach(id -> {
          var uri = routeById(id).getUri();
          assertEquals("lb", uri.getScheme(), "Route " + id + " must use lb://");
          assertEquals("epitomejob-backend", uri.getHost(),
              "Route " + id + " must resolve the backend through Eureka");
        });
  }

  @Test
  void mcpRoutePointsAtTheMcpService() {
    var uri = routeById("mcp").getUri();
    assertEquals("lb", uri.getScheme());
    assertEquals("mcp-service", uri.getHost());
  }

  @Test
  void everyRouteHasRateLimiterRetryAndCircuitBreaker() {
    EXPECTED_ROUTES.keySet().forEach(id -> {
      Set<String> filters = filterNames(routeById(id));
      assertTrue(filters.contains("RequestRateLimiter"), id + ": missing RequestRateLimiter");
      assertTrue(filters.contains("Retry"), id + ": missing Retry");
      assertTrue(filters.contains("CircuitBreaker"), id + ": missing CircuitBreaker");
    });
  }

  @Test
  void coachRouteHasTheThreadPoolBulkhead() {
    assertTrue(filterNames(routeById("coach")).contains("ThreadPoolBulkhead"),
        "coach is the slow LLM route and must run on its own thread pool");
    EXPECTED_ROUTES.keySet().stream()
        .filter(id -> !id.equals("coach"))
        .forEach(id -> assertTrue(
            !filterNames(routeById(id)).contains("ThreadPoolBulkhead"),
            id + ": thread-pool bulkhead belongs only on the coach route"));
  }

  @Test
  void retryIsGetOnlyAndCircuitBreakerHasFallbackAndStatusCodes() {
    EXPECTED_ROUTES.keySet().forEach(id -> {
      var route = routeById(id);
      var retry = route.getFilters().stream()
          .filter(f -> f.getName().equals("Retry")).findFirst().orElseThrow();
      assertEquals("3", retry.getArgs().get("retries"), id + ": retries must be 3");
      assertEquals("GET", retry.getArgs().get("methods"),
          id + ": retry must apply to GETs only");
      var cb = route.getFilters().stream()
          .filter(f -> f.getName().equals("CircuitBreaker")).findFirst().orElseThrow();
      assertTrue(((String) cb.getArgs().get("fallbackUri")).startsWith("forward:/fallback/"),
          id + ": circuit breaker must fall back to a local controller");
      assertTrue(cb.getArgs().containsKey("statusCodes"),
          id + ": 5xx responses must trip the breaker, not just exceptions");
    });
  }

  @Test
  void authIsPublicAndEverythingElseIsSecuredByTheJwtFilter() {
    // The split lives in JwtAuthFilter (a GlobalFilter, not a route filter):
    // /auth/** passes with no token; every other route 401s without one.
    // Covered behaviourally in JwtAuthFilterTest; here the routing side:
    assertTrue(JwtAuthFilter.matches("/auth/**", "/auth/login"));
    assertTrue(JwtAuthFilter.matches("/actuator/**", "/actuator/health"));
    EXPECTED_ROUTES.values().stream()
        .filter(path -> !path.startsWith("/auth/"))
        .forEach(path -> assertTrue(
            !JwtAuthFilter.matches("/auth/**", path.replace("/**", "/me")),
            path + " must NOT be public"));
  }

  @Test
  void rateLimiterConfigIsPresentWithTheDocumentedLimit() {
    // 100 req/min per user/IP: 2 tokens/sec refill, burst of 100.
    assertEquals(2, rateLimitProperties.getReplenishRate());
    assertEquals(100, rateLimitProperties.getBurstCapacity());
    assertEquals(1, rateLimitProperties.getRequestedTokens());
  }

  @Test
  void resilienceDefaultsArePresentInConfig() {
    // Breaker: 50% over 10 calls, 30s open, 3 half-open probes.
    assertEquals("50", environment.getProperty(
        "resilience4j.circuitbreaker.instances.backend.failureRateThreshold"));
    assertEquals("10", environment.getProperty(
        "resilience4j.circuitbreaker.instances.backend.slidingWindowSize"));
    assertEquals("30s", environment.getProperty(
        "resilience4j.circuitbreaker.instances.backend.waitDurationInOpenState"));
    assertEquals("3", environment.getProperty(
        "resilience4j.circuitbreaker.instances.backend.permittedNumberOfCallsInHalfOpenState"));
    // Time limiter: 3s per attempt.
    assertEquals("3s", environment.getProperty(
        "resilience4j.timelimiter.instances.backend.timeoutDuration"));
    assertEquals("3s", environment.getProperty(
        "resilience4j.timelimiter.instances.mcp-service.timeoutDuration"));
    // Semaphore bulkhead on the fast routes (fail fast, no queueing).
    assertEquals("0", environment.getProperty(
        "resilience4j.bulkhead.instances.backend.maxWaitDuration"));
    // Health endpoint exposed.
    assertEquals("health,info", environment.getProperty(
        "management.endpoints.web.exposure.include"));
  }
}
