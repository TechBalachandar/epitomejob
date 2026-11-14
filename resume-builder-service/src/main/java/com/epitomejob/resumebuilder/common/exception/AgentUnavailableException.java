package com.epitomejob.resumebuilder.common.exception;

/** A downstream service could not be reached (timeout / connect failure) → 502. */
public class AgentUnavailableException extends RuntimeException {

  public AgentUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }
}
