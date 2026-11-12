package com.epitomejob.common;
import org.slf4j.MDC;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
@RestControllerAdvice
public class GlobalExceptionHandler {
  @ExceptionHandler(ResponseStatusException.class)
  public ResponseEntity<ApiError> handle(ResponseStatusException ex) {
    return ResponseEntity.status(ex.getStatusCode())
        .body(new ApiError(ex.getStatusCode().toString(), ex.getReason(), MDC.get("traceId")));
  }
  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiError> handle(Exception ex) {
    // Never leak a stack trace to the client.
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(new ApiError("INTERNAL_ERROR", "Something went wrong.", MDC.get("traceId")));
  }
}
