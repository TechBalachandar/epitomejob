package com.epitomejob.common.exception;

import com.epitomejob.common.constants.AppConstants;

/**
 * Billing is enabled with provider=stripe but no STRIPE_SECRET_KEY is set —
 * 503 BILLING_NOT_CONFIGURED. Never silently fall back to the fake provider.
 */
public class BillingNotConfiguredException extends RuntimeException {

  public BillingNotConfiguredException() {
    super(AppConstants.Errors.BILLING_NOT_CONFIGURED_MESSAGE);
  }
}
