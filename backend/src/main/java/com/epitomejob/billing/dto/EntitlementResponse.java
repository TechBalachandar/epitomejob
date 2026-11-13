package com.epitomejob.billing.dto;

import com.epitomejob.billing.model.Entitlement;
import com.epitomejob.common.constants.AppConstants;

import java.time.Instant;

/** GET /billing/entitlement/me. No entitlement → {planCode: null, status: "NONE", expiresAt: null}. */
public record EntitlementResponse(String planCode, String status, Instant expiresAt) {

  public static EntitlementResponse from(Entitlement entitlement) {
    return new EntitlementResponse(entitlement.getPlanCode(), entitlement.getStatus(),
        entitlement.getExpiresAt());
  }

  public static EntitlementResponse none() {
    return new EntitlementResponse(null, AppConstants.Billing.ENTITLEMENT_STATUS_NONE, null);
  }
}
