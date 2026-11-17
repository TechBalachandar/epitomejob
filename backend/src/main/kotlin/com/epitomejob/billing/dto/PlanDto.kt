package com.epitomejob.billing.dto

import com.epitomejob.billing.model.Plan

/** One plan, wire names camelCase per W4 contract §1e/§6. */
data class PlanDto(
  val code: String,
  val name: String,
  val priceCents: Int,
  val currency: String,
  val durationDays: Int?,
  val description: String,
  val active: Boolean
) {

  companion object {
    @JvmStatic
    fun from(plan: Plan): PlanDto {
      return PlanDto(plan.code, plan.name, plan.priceCents,
        plan.currency, plan.durationDays, plan.description, plan.active)
    }
  }
}
