package com.epitomejob.common

import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import java.util.UUID

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
 *
 * <p>The kotlin-spring (all-open) compiler plugin opens this class for
 * Spring, since Kotlin classes are final by default.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class TraceIdFilter : WebFilter {

  override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
    val id = exchange.request.headers.getFirst(TraceConstants.REQUEST_ID_HEADER)
      ?.takeIf { it.isNotBlank() }
      ?: UUID.randomUUID().toString()
    val mutated = exchange.mutate()
      .request { builder ->
        builder.headers { headers ->
          headers.set(TraceConstants.REQUEST_ID_HEADER, id)
        }
      }
      .build()
    mutated.attributes[TraceConstants.TRACE_ID_ATTRIBUTE] = id
    mutated.response.headers.set(TraceConstants.REQUEST_ID_HEADER, id)
    return chain.filter(mutated)
  }
}
