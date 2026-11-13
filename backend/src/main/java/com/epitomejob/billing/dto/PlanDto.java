package com.epitomejob.billing.dto;

import com.epitomejob.billing.model.Plan;

/** One plan, wire names camelCase per W4 contract §1e/§6. */
public record PlanDto(String code, String name, int priceCents, String currency,
                      Integer durationDays, String description, boolean active) {

  public static PlanDto from(Plan plan) {
    return new PlanDto(plan.getCode(), plan.getName(), plan.getPriceCents(),
        plan.getCurrency(), plan.getDurationDays(), plan.getDescription(), plan.isActive());
  }
}
