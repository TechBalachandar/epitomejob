package com.epitomejob.common.filter

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.slf4j.MDC
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

@ExtendWith(MockitoExtension::class)
class TraceIdFilterTest {

  private val filter = TraceIdFilter()

  @Test
  fun propagatesIncomingRequestId() {
    val request = MockHttpServletRequest()
    request.addHeader("X-Request-Id", "trace-123")
    val response = MockHttpServletResponse()
    val chain = mock(FilterChain::class.java)
    doAnswer { inv ->
      assertEquals("trace-123", MDC.get("traceId"))
      null
    }.`when`(chain).doFilter(request, response)

    filter.doFilter(request, response, chain)

    assertEquals("trace-123", response.getHeader("X-Request-Id"))
    assertNull(MDC.get("traceId"), "MDC must be cleared after the request")
  }

  @Test
  fun generatesRequestIdWhenMissing() {
    val request = MockHttpServletRequest()
    val response = MockHttpServletResponse()
    val chain = mock(FilterChain::class.java)

    filter.doFilter(request, response, chain)

    val generated = response.getHeader("X-Request-Id")
    assertNotNull(generated)
    assertTrue(
      generated!!.matches(
        "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"
      ),
      "generated id must be a UUID, was: $generated"
    )
    assertNull(MDC.get("traceId"))
  }

  @Test
  fun blankRequestIdIsReplaced() {
    val request = MockHttpServletRequest()
    request.addHeader("X-Request-Id", "   ")
    val response = MockHttpServletResponse()

    filter.doFilter(request, response, mock(FilterChain::class.java))

    assertNotNull(response.getHeader("X-Request-Id"))
    assertTrue((response.getHeader("X-Request-Id")?.length ?: 0) > 10)
  }
}
