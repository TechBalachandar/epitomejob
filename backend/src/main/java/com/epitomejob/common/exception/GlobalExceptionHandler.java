package com.epitomejob.common.exception;

import com.epitomejob.common.constants.AppConstants;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/** Maps every failure to a uniform {@link ApiError}. Never leaks internals. */
@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(ResponseStatusException.class)
  public ResponseEntity<ApiError> handle(ResponseStatusException ex) {
    return ResponseEntity.status(ex.getStatusCode())
        .body(new ApiError(ex.getStatusCode().toString(), ex.getReason(), MDC.get(AppConstants.Http.TRACE_ID_MDC_KEY)));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiError> handle(MethodArgumentNotValidException ex) {
    String message = ex.getBindingResult().getFieldErrors().stream()
        .findFirst()
        .map(error -> error.getField() + ": " + error.getDefaultMessage())
        .orElse(AppConstants.Errors.VALIDATION_FAILED_CODE);
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(new ApiError(AppConstants.Errors.VALIDATION_FAILED_CODE, message,
            MDC.get(AppConstants.Http.TRACE_ID_MDC_KEY)));
  }

  @ExceptionHandler(AgentUnavailableException.class)
  public ResponseEntity<ApiError> handle(AgentUnavailableException ex) {
    return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
        .body(new ApiError(AppConstants.Errors.AGENT_UNAVAILABLE_CODE, ex.getMessage(),
            MDC.get(AppConstants.Http.TRACE_ID_MDC_KEY)));
  }

  @ExceptionHandler(ResumeNotReadyException.class)
  public ResponseEntity<ApiError> handle(ResumeNotReadyException ex) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(new ApiError(AppConstants.Errors.RESUME_NOT_READY_CODE, ex.getMessage(),
            MDC.get(AppConstants.Http.TRACE_ID_MDC_KEY)));
  }

  @ExceptionHandler(AlreadyEntitledException.class)
  public ResponseEntity<ApiError> handle(AlreadyEntitledException ex) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(new ApiError(AppConstants.Errors.ALREADY_ENTITLED_CODE, ex.getMessage(),
            MDC.get(AppConstants.Http.TRACE_ID_MDC_KEY)));
  }

  @ExceptionHandler(BillingNotConfiguredException.class)
  public ResponseEntity<ApiError> handle(BillingNotConfiguredException ex) {
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
        .body(new ApiError(AppConstants.Errors.BILLING_NOT_CONFIGURED_CODE, ex.getMessage(),
            MDC.get(AppConstants.Http.TRACE_ID_MDC_KEY)));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiError> handle(Exception ex) {
    // Never leak a stack trace or an internal message to the client.
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(new ApiError(AppConstants.Errors.INTERNAL_ERROR_CODE,
            AppConstants.Errors.INTERNAL_ERROR_MESSAGE,
            MDC.get(AppConstants.Http.TRACE_ID_MDC_KEY)));
  }
}
