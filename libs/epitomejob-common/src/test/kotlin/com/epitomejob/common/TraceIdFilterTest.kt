package com.epitomejob.common

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

/**
 * Unit tests for [TraceIdFilter]. Uses real
 * [MockServerWebExchange] objects — no Mockito, so there are no stubs
 * to drift out of reach of the code path under test.
 */
class TraceIdFilterTest {

  private val filter = TraceIdFilter()

  @Test
  fun generatesUuidWhenHeaderAbsentAndEchoesItOnResponse() {
    val exchange = MockServerWebExchange
      .from(MockServerHttpRequest.get("/profiles/me").build())
    // The filter passes a MUTATED exchange to the chain; capture the id from
    // that exchange, not from the original (whose request is unchanged).
    val seenOnRequest = AtomicReference<String>()

    StepVerifier.create(
      filter.filter(exchange) { e ->
        // Inside the chain the request must carry the generated id…
        val onRequest = e.request.headers.getFirst(TraceConstants.REQUEST_ID_HEADER)
        assertNotNull(onRequest)
        val id = requireNotNull(onRequest)
        // …it must be a UUID…
        UUID.fromString(id)
        // …and it must be stored as the exchange attribute…
        assertEquals(id, e.getAttribute<String>(TraceConstants.TRACE_ID_ATTRIBUTE))
        seenOnRequest.set(id)
        Mono.empty<Void>()
      }
    ).verifyComplete()

    // …and echoed on the response.
    assertEquals(
      seenOnRequest.get(),
      exchange.response.headers.getFirst(TraceConstants.REQUEST_ID_HEADER)
    )
  }

  @Test
  fun preservesClientSuppliedHeader() {
    val exchange = MockServerWebExchange.from(
      MockServerHttpRequest.get("/profiles/me")
        .header(TraceConstants.REQUEST_ID_HEADER, "trace-123")
        .build()
    )

    StepVerifier.create(
      filter.filter(exchange) { e ->
        assertEquals(
          "trace-123",
          e.request.headers.getFirst(TraceConstants.REQUEST_ID_HEADER)
        )
        Mono.empty<Void>()
      }
    ).verifyComplete()

    assertEquals(
      "trace-123",
      exchange.response.headers.getFirst(TraceConstants.REQUEST_ID_HEADER)
    )
  }
}
