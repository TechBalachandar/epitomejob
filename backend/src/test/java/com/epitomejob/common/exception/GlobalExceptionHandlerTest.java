package com.epitomejob.common.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GlobalExceptionHandlerTest {

  private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

  @Test
  void responseStatusExceptionMapsStatusAndReason() {
    var body = handler.handle(
        new ResponseStatusException(HttpStatus.NOT_FOUND, "Approval not found")).getBody();

    assertEquals("404 NOT_FOUND", body.code());
    assertEquals("Approval not found", body.message());
  }

  @Test
  void validationFailureReturnsFieldMessage() throws Exception {
    var binding = new BeanPropertyBindingResult(new Object(), "request");
    binding.addError(new FieldError("request", "email", "must be a well-formed email address"));
    var ex = new MethodArgumentNotValidException(null, binding);

    var response = handler.handle(ex);

    assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    assertEquals("VALIDATION_FAILED", response.getBody().code());
    assertEquals("email: must be a well-formed email address", response.getBody().message());
  }

  @Test
  void unexpectedExceptionNeverLeaksInternals() {
    var response = handler.handle(new RuntimeException("DB password is hunter2"));

    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
    assertEquals("INTERNAL_ERROR", response.getBody().code());
    assertEquals("Something went wrong.", response.getBody().message());
  }

  @Test
  void agentUnavailableReturns502WithCode() {
    var response = handler.handle(
        new AgentUnavailableException(new RuntimeException("connection refused")));

    assertEquals(HttpStatus.BAD_GATEWAY, response.getStatusCode());
    assertEquals("AGENT_UNAVAILABLE", response.getBody().code());
    assertEquals("Coach agent is not reachable", response.getBody().message());
  }

  @Test
  void resumeNotReadyReturns400WithCode() {
    var response = handler.handle(new ResumeNotReadyException());

    assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    assertEquals("RESUME_NOT_READY", response.getBody().code());
    assertEquals("Resume text is not ready yet", response.getBody().message());
  }
}
