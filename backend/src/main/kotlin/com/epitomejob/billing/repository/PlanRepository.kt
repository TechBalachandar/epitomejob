package com.epitomejob.billing.repository

import com.epitomejob.billing.model.Plan
import org.springframework.data.jpa.repository.JpaRepository

interface PlanRepository : JpaRepository<Plan, String> {

  fun findByActiveTrue(): List<Plan>
}
