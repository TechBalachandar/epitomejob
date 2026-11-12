package com.epitomejob.common.filter;

import com.epitomejob.common.constants.AppConstants;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Request tracing. Propagates an incoming {@code X-Request-Id} or generates
 * one, puts it in the MDC as {@code traceId} (so the exception handler and
 * every log line can carry it), and echoes it on the response.
 * Runs first so the whole request — including security — is traced.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                  FilterChain chain) throws ServletException, IOException {
    String traceId = request.getHeader(AppConstants.Http.REQUEST_ID_HEADER);
    if (traceId == null || traceId.isBlank()) {
      traceId = UUID.randomUUID().toString();
    }
    MDC.put(AppConstants.Http.TRACE_ID_MDC_KEY, traceId);
    response.setHeader(AppConstants.Http.REQUEST_ID_HEADER, traceId);
    try {
      chain.doFilter(request, response);
    } finally {
      MDC.clear();
    }
  }
}
