package com.epitomejob.gateway.filter

import io.github.resilience4j.bulkhead.ThreadPoolBulkhead
import io.github.resilience4j.bulkhead.ThreadPoolBulkheadRegistry
import org.springframework.cloud.gateway.filter.GatewayFilter
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

/**
 * Thread-pool bulkhead filter for slow downstreams — applied only to routes
 * under `/coach/`, whose calls reach the agent-brain LLM and can
 * take seconds.
 *
 * Why a separate filter: the reactive gateway must never block its Netty
 * event-loop threads, so the semaphore bulkhead configured under
 * `resilience4j.bulkhead` (fast routes, fail-fast, no waiting) is the
 * default. A slow downstream gets the opposite treatment — its work is
 * moved onto a dedicated thread pool (`resilience4j.threadpoolbulkhead`
 * in `application.yml`: 4 core / 10 max threads, queue of 20), so a
 * stalled LLM call can neither starve the event loop nor crowd out fast
 * routes. When the pool + queue are full the call fails fast with
 * `BulkheadFullException`, which the route's circuit breaker turns
 * into the `/fallback/backend` 503.
 *
 * Usage in `application.yml`: `- name: ThreadPoolBulkhead,
 * args: {name: coach}`.
 */
@Component
class ThreadPoolBulkheadGatewayFilterFactory(
  private val registry: ThreadPoolBulkheadRegistry
) : AbstractGatewayFilterFactory<ThreadPoolBulkheadGatewayFilterFactory.Config>(Config::class.java) {

  class Config {
    /** Name of the thread-pool bulkhead instance (see application.yml). */
    var name: String = "coach"
  }

  override fun apply(config: Config): GatewayFilter {
    // Looked up once per route at startup; the "coach" instance config comes
    // from resilience4j.threadpoolbulkhead.instances.coach in application.yml.
    val bulkhead: ThreadPoolBulkhead = registry.bulkhead(config.name)
    // executeSupplier wraps the supplier's result in a CompletionStage, and
    // the supplier returns the chain's CompletableFuture — so the raw result
    // is a nested CompletionStage<CompletableFuture<Void>>, which
    // Mono.fromFuture cannot take (it needs a CompletableFuture, and the
    // ThreadPoolBulkhead API has no executeCompletionStage to flatten it).
    // thenCompose unwraps the inner future to CompletionStage<Void>, and
    // Mono.fromCompletionStage lifts that to the Mono the filter must return.
    return GatewayFilter { exchange, chain ->
      val stage: CompletionStage<CompletableFuture<Void>> =
        bulkhead.executeSupplier { chain.filter(exchange).toFuture() }
      Mono.fromCompletionStage(stage.thenCompose { future -> future })
    }
  }
}
