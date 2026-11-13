package com.epitomejob.common.exception;

import com.epitomejob.common.constants.AppConstants;

/** The user already holds an ACTIVE entitlement for this plan — 409 ALREADY_ENTITLED. */
public class AlreadyEntitledException extends RuntimeException {

  public AlreadyEntitledException() {
    super(AppConstants.Errors.ALREADY_ENTITLED);
  }
}
