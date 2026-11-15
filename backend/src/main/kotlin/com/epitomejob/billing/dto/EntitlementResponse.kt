package com.epitomejob.billing.dto

import com.epitomejob.billing.model.Entitlement
import com.epitomejob.common.constants.AppConstants
import java.time.Instant

/** GET /billing/entitlement/me. No entitlement → {planCode: null, status: "NONE", expiresAt: null}. */
data class EntitlementResponse(
  val planCode: String?,
  val status: String,
  val expiresAt: Instant?
) {

  companion object {
    @JvmStatic
    fun from(entitlement: Entitlement): EntitlementResponse {
      return EntitlementResponse(entitlement.planCode, entitlement.status,
        entitlement.expiresAt)
    }

    @JvmStatic
    fun none(): EntitlementResponse {
      return EntitlementResponse(null, AppConstants.Billing.ENTITLEMENT_STATUS_NONE, null)
    }
  }
}
