package com.epitomejob.resumebuilder.common.exception

import com.epitomejob.resumebuilder.common.constants.AppConstants
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.server.ResponseStatusException

/**
 * Error mapping. ResponseStatusException (thrown by services for 400/404/
 * 413) passes through unchanged; unreachable downstreams become 502;
 * anything unexpected becomes a generic 500 that never leaks internals.
 */
@RestControllerAdvice
class GlobalExceptionHandler {

  @ExceptionHandler(ResponseStatusException::class)
  fun handleResponseStatus(ex: ResponseStatusException): ResponseEntity<Map<String, String>> {
    return ResponseEntity.status(ex.statusCode)
        .body(mapOf("code" to ex.statusCode.toString(),
            "message" to (ex.reason ?: "")))
  }

  @ExceptionHandler(AgentUnavailableException::class)
  fun handleUnavailable(ex: AgentUnavailableException): ResponseEntity<Map<String, String>> {
    return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
        .body(mapOf("code" to "BAD_GATEWAY", "message" to (ex.message ?: "")))
  }

  @ExceptionHandler(IllegalArgumentException::class)
  fun handleBadRequest(ex: IllegalArgumentException): ResponseEntity<Map<String, String>> {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(mapOf("code" to "BAD_REQUEST", "message" to (ex.message ?: "")))
  }

  @ExceptionHandler(IllegalStateException::class)
  fun handleUnauthenticated(ex: IllegalStateException): ResponseEntity<Map<String, String>> {
    // SecurityUtil.currentUserId() with no authentication — the filter
    // should have stopped the request first; 401 if it ever slips through.
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
        .body(mapOf("code" to "UNAUTHORIZED", "message" to AppConstants.Errors.NOT_AUTHENTICATED))
  }

  @ExceptionHandler(Exception::class)
  fun handleUnexpected(ex: Exception): ResponseEntity<Map<String, String>> {
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(mapOf("code" to "INTERNAL_ERROR", "message" to "An unexpected error occurred"))
  }
}
