package com.epitomejob.gateway.filter;

import io.github.resilience4j.bulkhead.ThreadPoolBulkhead;
import io.github.resilience4j.bulkhead.ThreadPoolBulkheadRegistry;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Thread-pool bulkhead filter for slow downstreams — applied only to the
 * {@code /coach/**} route, whose calls reach the agent-brain LLM and can
 * take seconds.
 *
 * <p>Why a separate filter: the reactive gateway must never block its Netty
 * event-loop threads, so the semaphore bulkhead configured under
 * {@code resilience4j.bulkhead} (fast routes, fail-fast, no waiting) is the
 * default. A slow downstream gets the opposite treatment — its work is
 * moved onto a dedicated thread pool ({@code resilience4j.threadpoolbulkhead}
 * in {@code application.yml}: 4 core / 10 max threads, queue of 20), so a
 * stalled LLM call can neither starve the event loop nor crowd out fast
 * routes. When the pool + queue are full the call fails fast with
 * {@code BulkheadFullException}, which the route's circuit breaker turns
 * into the {@code /fallback/backend} 503.
 *
 * <p>Usage in {@code application.yml}: {@code - name: ThreadPoolBulkhead,
 * args: {name: coach}}.
 */
@Component
public class ThreadPoolBulkheadGatewayFilterFactory
    extends AbstractGatewayFilterFactory<ThreadPoolBulkheadGatewayFilterFactory.Config> {

  private final ThreadPoolBulkheadRegistry registry;

  public ThreadPoolBulkheadGatewayFilterFactory(ThreadPoolBulkheadRegistry registry) {
    super(Config.class);
    this.registry = registry;
  }

  public static class Config {
    /** Name of the thread-pool bulkhead instance (see application.yml). */
    private String name = "coach";

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }
  }

  @Override
  public GatewayFilter apply(Config config) {
    // Looked up once per route at startup; the "coach" instance config comes
    // from resilience4j.threadpoolbulkhead.instances.coach in application.yml.
    ThreadPoolBulkhead bulkhead = registry.bulkhead(config.getName());
    return (exchange, chain) -> Mono.fromFuture(
        bulkhead.executeSupplier(() -> chain.filter(exchange).toFuture()));
  }
}
