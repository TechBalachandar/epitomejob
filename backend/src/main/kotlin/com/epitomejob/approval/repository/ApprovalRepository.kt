package com.epitomejob.approval.repository

import com.epitomejob.approval.model.Approval
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ApprovalRepository : JpaRepository<Approval, UUID> {
  fun findByUserIdAndStatusOrderByExpiresAtAsc(userId: UUID, status: String): List<Approval>
}
