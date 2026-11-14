package com.epitomejob.common;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * End-to-end trace filter. Runs first (highest precedence, as a plain
 * {@link WebFilter} so it applies before the gateway's own filter chain):
 * if the request has no {@code X-Request-Id} header, one is generated
 * (random UUID); the id is passed to the downstream on the request, echoed
 * on the response, and stored as an exchange attribute for error bodies.
 *
 * <p>Kept in the common library as a {@code WebFilter} (not a gateway
 * {@code GlobalFilter}) on purpose: any WebFlux service can use it without
 * depending on Spring Cloud Gateway. Services that use it must scan this
 * package, e.g. {@code @SpringBootApplication(scanBasePackages =
 * {"com.epitomejob.gateway", "com.epitomejob.common"})}.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter implements WebFilter {

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
    String traceId = exchange.getRequest().getHeaders()
        .getFirst(TraceConstants.REQUEST_ID_HEADER);
    if (traceId == null || traceId.isBlank()) {
      traceId = UUID.randomUUID().toString();
    }
    final String id = traceId;
    ServerWebExchange mutated = exchange.mutate()
        .request(builder -> builder.headers(headers ->
            headers.set(TraceConstants.REQUEST_ID_HEADER, id)))
        .build();
    mutated.getAttributes().put(TraceConstants.TRACE_ID_ATTRIBUTE, id);
    mutated.getResponse().getHeaders()
        .set(TraceConstants.REQUEST_ID_HEADER, id);
    return chain.filter(mutated);
  }
}
