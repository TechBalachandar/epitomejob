package com.epitomejob.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Guards the error shape the gateway and the monolith share: field names and
 * JSON spelling must stay identical, or clients parsing {@code ApiError}
 * break on one side and not the other.
 */
class ApiErrorTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void recordAccessors() {
    ApiError error = new ApiError("UNAUTHORIZED", "Token has expired", "trace-1");
    assertEquals("UNAUTHORIZED", error.code());
    assertEquals("Token has expired", error.message());
    assertEquals("trace-1", error.traceId());
  }

  @Test
  void serializesWithTheMonolithFieldNames() throws Exception {
    ApiError error = new ApiError("SERVICE_UNAVAILABLE", "Downstream unavailable", "trace-2");
    String json = mapper.writeValueAsString(error);
    assertEquals(
        "{\"code\":\"SERVICE_UNAVAILABLE\",\"message\":\"Downstream unavailable\",\"traceId\":\"trace-2\"}",
        json);
  }

  @Test
  void nullTraceIdIsOmittedFromJson() throws Exception {
    ApiError error = new ApiError("UNAUTHORIZED", "Missing token", null);
    assertEquals("{\"code\":\"UNAUTHORIZED\",\"message\":\"Missing token\"}",
        mapper.writeValueAsString(error));
  }
}
