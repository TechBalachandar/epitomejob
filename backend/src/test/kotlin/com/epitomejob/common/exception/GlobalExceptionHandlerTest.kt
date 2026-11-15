package com.epitomejob.common.exception

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.springframework.http.HttpStatus
import org.springframework.validation.BeanPropertyBindingResult
import org.springframework.validation.FieldError
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.server.ResponseStatusException

class GlobalExceptionHandlerTest {

  private val handler = GlobalExceptionHandler()

  @Test
  fun responseStatusExceptionMapsStatusAndReason() {
    val body = handler.handle(
      ResponseStatusException(HttpStatus.NOT_FOUND, "Approval not found")
    ).body

    assertEquals("404 NOT_FOUND", body?.code)
    assertEquals("Approval not found", body?.message)
  }

  @Test
  fun validationFailureReturnsFieldMessage() {
    val binding = BeanPropertyBindingResult(Any(), "request")
    binding.addError(FieldError("request", "email", "must be a well-formed email address"))
    val ex = MethodArgumentNotValidException(null, binding)

    val response = handler.handle(ex)

    assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    assertEquals("VALIDATION_FAILED", response.body?.code)
    assertEquals("email: must be a well-formed email address", response.body?.message)
  }

  @Test
  fun unexpectedExceptionNeverLeaksInternals() {
    val response = handler.handle(RuntimeException("DB password is hunter2"))

    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode)
    assertEquals("INTERNAL_ERROR", response.body?.code)
    assertEquals("Something went wrong.", response.body?.message)
  }

  @Test
  fun agentUnavailableReturns502WithCode() {
    val response = handler.handle(
      AgentUnavailableException(RuntimeException("connection refused"))
    )

    assertEquals(HttpStatus.BAD_GATEWAY, response.statusCode)
    assertEquals("AGENT_UNAVAILABLE", response.body?.code)
    assertEquals("Coach agent is not reachable", response.body?.message)
  }

  @Test
  fun resumeNotReadyReturns400WithCode() {
    val response = handler.handle(ResumeNotReadyException())

    assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    assertEquals("RESUME_NOT_READY", response.body?.code)
    assertEquals("Resume text is not ready yet", response.body?.message)
  }

  @Test
  fun alreadyEntitledReturns409WithCode() {
    val response = handler.handle(AlreadyEntitledException())

    assertEquals(HttpStatus.CONFLICT, response.statusCode)
    assertEquals("ALREADY_ENTITLED", response.body?.code)
    assertEquals("You already have an active entitlement for this plan", response.body?.message)
  }

  @Test
  fun billingNotConfiguredReturns503WithCodeAndMessage() {
    val response = handler.handle(BillingNotConfiguredException())

    assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.statusCode)
    assertEquals("BILLING_NOT_CONFIGURED", response.body?.code)
    assertEquals("Billing is not connected yet — no payment is taken.", response.body?.message)
  }
}
