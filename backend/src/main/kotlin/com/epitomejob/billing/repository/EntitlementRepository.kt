package com.epitomejob.billing.repository

import com.epitomejob.billing.model.Entitlement
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional
import java.util.UUID

interface EntitlementRepository : JpaRepository<Entitlement, UUID> {

  fun existsByUserIdAndPlanCodeAndStatus(userId: UUID, planCode: String, status: String): Boolean

  fun findFirstByUserIdAndStatusOrderByStartedAtDesc(userId: UUID, status: String): Optional<Entitlement>

  fun countByUserIdAndPlanCodeAndStatus(userId: UUID, planCode: String, status: String): Long
}
