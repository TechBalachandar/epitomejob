package com.epitomejob.resumebuilder.common.exception;

import com.epitomejob.resumebuilder.common.constants.AppConstants;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * Error mapping. ResponseStatusException (thrown by services for 400/404/
 * 413) passes through unchanged; unreachable downstreams become 502;
 * anything unexpected becomes a generic 500 that never leaks internals.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(ResponseStatusException.class)
  public ResponseEntity<Map<String, String>> handleResponseStatus(ResponseStatusException ex) {
    return ResponseEntity.status(ex.getStatusCode())
        .body(Map.of("code", ex.getStatusCode().toString(),
            "message", ex.getReason() == null ? "" : ex.getReason()));
  }

  @ExceptionHandler(AgentUnavailableException.class)
  public ResponseEntity<Map<String, String>> handleUnavailable(AgentUnavailableException ex) {
    return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
        .body(Map.of("code", "BAD_GATEWAY", "message", ex.getMessage()));
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException ex) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(Map.of("code", "BAD_REQUEST", "message", ex.getMessage()));
  }

  @ExceptionHandler(IllegalStateException.class)
  public ResponseEntity<Map<String, String>> handleUnauthenticated(IllegalStateException ex) {
    // SecurityUtil.currentUserId() with no authentication — the filter
    // should have stopped the request first; 401 if it ever slips through.
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
        .body(Map.of("code", "UNAUTHORIZED", "message", AppConstants.Errors.NOT_AUTHENTICATED));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<Map<String, String>> handleUnexpected(Exception ex) {
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(Map.of("code", "INTERNAL_ERROR", "message", "An unexpected error occurred"));
  }
}
