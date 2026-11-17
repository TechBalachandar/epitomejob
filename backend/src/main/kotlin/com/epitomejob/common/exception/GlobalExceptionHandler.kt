package com.epitomejob.common.exception

import com.epitomejob.common.constants.AppConstants
import org.slf4j.MDC
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.server.ResponseStatusException

/** Maps every failure to a uniform [ApiError]. Never leaks internals. */
@RestControllerAdvice
class GlobalExceptionHandler {

  @ExceptionHandler(ResponseStatusException::class)
  fun handle(ex: ResponseStatusException): ResponseEntity<ApiError> {
    return ResponseEntity.status(ex.statusCode)
      .body(ApiError(ex.statusCode.toString(), ex.reason, MDC.get(AppConstants.Http.TRACE_ID_MDC_KEY)))
  }

  @ExceptionHandler(MethodArgumentNotValidException::class)
  fun handle(ex: MethodArgumentNotValidException): ResponseEntity<ApiError> {
    val message = ex.bindingResult.fieldErrors.firstOrNull()
      ?.let { error -> "${error.field}: ${error.defaultMessage}" }
      ?: AppConstants.Errors.VALIDATION_FAILED_CODE
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
      .body(
        ApiError(
          AppConstants.Errors.VALIDATION_FAILED_CODE, message,
          MDC.get(AppConstants.Http.TRACE_ID_MDC_KEY)
        )
      )
  }

  @ExceptionHandler(HttpMessageNotReadableException::class)
  fun handle(ex: HttpMessageNotReadableException): ResponseEntity<ApiError> {
    // Malformed JSON or a missing required field (e.g. TrackerEntry.jobId):
    // Jackson throws before Bean Validation runs, so map it to 400 here
    // instead of letting the catch-all below turn it into a 500.
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
      .body(
        ApiError(
          AppConstants.Errors.VALIDATION_FAILED_CODE,
          AppConstants.Errors.VALIDATION_FAILED_CODE,
          MDC.get(AppConstants.Http.TRACE_ID_MDC_KEY)
        )
      )
  }

  @ExceptionHandler(AgentUnavailableException::class)
  fun handle(ex: AgentUnavailableException): ResponseEntity<ApiError> {
    return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
      .body(
        ApiError(
          AppConstants.Errors.AGENT_UNAVAILABLE_CODE, ex.message,
          MDC.get(AppConstants.Http.TRACE_ID_MDC_KEY)
        )
      )
  }

  @ExceptionHandler(ResumeNotReadyException::class)
  fun handle(ex: ResumeNotReadyException): ResponseEntity<ApiError> {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
      .body(
        ApiError(
          AppConstants.Errors.RESUME_NOT_READY_CODE, ex.message,
          MDC.get(AppConstants.Http.TRACE_ID_MDC_KEY)
        )
      )
  }

  @ExceptionHandler(AlreadyEntitledException::class)
  fun handle(ex: AlreadyEntitledException): ResponseEntity<ApiError> {
    return ResponseEntity.status(HttpStatus.CONFLICT)
      .body(
        ApiError(
          AppConstants.Errors.ALREADY_ENTITLED_CODE, ex.message,
          MDC.get(AppConstants.Http.TRACE_ID_MDC_KEY)
        )
      )
  }

  @ExceptionHandler(BillingNotConfiguredException::class)
  fun handle(ex: BillingNotConfiguredException): ResponseEntity<ApiError> {
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
      .body(
        ApiError(
          AppConstants.Errors.BILLING_NOT_CONFIGURED_CODE, ex.message,
          MDC.get(AppConstants.Http.TRACE_ID_MDC_KEY)
        )
      )
  }

  @ExceptionHandler(Exception::class)
  fun handle(ex: Exception): ResponseEntity<ApiError> {
    // Never leak a stack trace or an internal message to the client.
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
      .body(
        ApiError(
          AppConstants.Errors.INTERNAL_ERROR_CODE,
          AppConstants.Errors.INTERNAL_ERROR_MESSAGE,
          MDC.get(AppConstants.Http.TRACE_ID_MDC_KEY)
        )
      )
  }
}
