package com.epitomejob.common

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Guards the error shape the gateway and the monolith share: field names and
 * JSON spelling must stay identical, or clients parsing {@code ApiError}
 * break on one side and not the other.
 */
class ApiErrorTest {

  private val mapper = ObjectMapper()

  @Test
  fun recordAccessors() {
    val error = ApiError("UNAUTHORIZED", "Token has expired", "trace-1")
    assertEquals("UNAUTHORIZED", error.code)
    assertEquals("Token has expired", error.message)
    assertEquals("trace-1", error.traceId)
  }

  @Test
  fun serializesWithTheMonolithFieldNames() {
    val error = ApiError("SERVICE_UNAVAILABLE", "Downstream unavailable", "trace-2")
    val json = mapper.writeValueAsString(error)
    assertEquals(
      "{\"code\":\"SERVICE_UNAVAILABLE\",\"message\":\"Downstream unavailable\",\"traceId\":\"trace-2\"}",
      json
    )
  }

  @Test
  fun nullTraceIdIsOmittedFromJson() {
    val error = ApiError("UNAUTHORIZED", "Missing token", null)
    assertEquals(
      "{\"code\":\"UNAUTHORIZED\",\"message\":\"Missing token\"}",
      mapper.writeValueAsString(error)
    )
  }
}
