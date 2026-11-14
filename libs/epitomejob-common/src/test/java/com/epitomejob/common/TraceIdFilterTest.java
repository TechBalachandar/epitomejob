package com.epitomejob.common;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Unit tests for {@link TraceIdFilter}. Uses real
 * {@link MockServerWebExchange} objects — no Mockito, so there are no stubs
 * to drift out of reach of the code path under test.
 */
class TraceIdFilterTest {

  private final TraceIdFilter filter = new TraceIdFilter();

  @Test
  void generatesUuidWhenHeaderAbsentAndEchoesItOnResponse() {
    MockServerWebExchange exchange = MockServerWebExchange
        .from(MockServerHttpRequest.get("/profiles/me").build());
    // The filter passes a MUTATED exchange to the chain; capture the id from
    // that exchange, not from the original (whose request is unchanged).
    java.util.concurrent.atomic.AtomicReference<String> seenOnRequest =
        new java.util.concurrent.atomic.AtomicReference<>();

    StepVerifier.create(filter.filter(exchange,
            e -> {
              // Inside the chain the request must carry the generated id…
              String onRequest = e.getRequest().getHeaders()
                  .getFirst(TraceConstants.REQUEST_ID_HEADER);
              assertNotNull(onRequest);
              // …it must be a UUID…
              assertDoesNotThrow(() -> UUID.fromString(onRequest));
              // …and it must be stored as the exchange attribute…
              assertEquals(onRequest,
                  e.getAttribute(TraceConstants.TRACE_ID_ATTRIBUTE));
              seenOnRequest.set(onRequest);
              return reactor.core.publisher.Mono.empty();
            }))
        .verifyComplete();

    // …and echoed on the response.
    assertEquals(seenOnRequest.get(), exchange.getResponse().getHeaders()
        .getFirst(TraceConstants.REQUEST_ID_HEADER));
  }

  @Test
  void preservesClientSuppliedHeader() {
    MockServerWebExchange exchange = MockServerWebExchange.from(
        MockServerHttpRequest.get("/profiles/me")
            .header(TraceConstants.REQUEST_ID_HEADER, "trace-123")
            .build());

    StepVerifier.create(filter.filter(exchange,
            e -> {
              assertEquals("trace-123", e.getRequest().getHeaders()
                  .getFirst(TraceConstants.REQUEST_ID_HEADER));
              return reactor.core.publisher.Mono.empty();
            }))
        .verifyComplete();

    assertEquals("trace-123", exchange.getResponse().getHeaders()
        .getFirst(TraceConstants.REQUEST_ID_HEADER));
  }

  private static void assertDoesNotThrow(ThrowingRunnable runnable) {
    try {
      runnable.run();
    } catch (Exception e) {
      throw new AssertionError("Expected no exception but got: " + e, e);
    }
  }

  @FunctionalInterface
  private interface ThrowingRunnable {
    void run() throws Exception;
  }
}
