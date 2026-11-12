package com.epitomejob.common.exception;

import com.epitomejob.common.constants.AppConstants;

/** The agent-brain could not be reached (W3 contract §3) → 502. */
public class AgentUnavailableException extends RuntimeException {

  public AgentUnavailableException(Throwable cause) {
    super(AppConstants.Errors.AGENT_UNAVAILABLE_MESSAGE, cause);
  }
}
