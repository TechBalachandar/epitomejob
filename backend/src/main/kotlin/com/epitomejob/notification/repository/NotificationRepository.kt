package com.epitomejob.notification.repository

import com.epitomejob.notification.model.Notification
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface NotificationRepository : JpaRepository<Notification, UUID> {
  fun findByUserId(userId: UUID, pageable: Pageable): Page<Notification>
  fun countByUserIdAndIsReadFalse(userId: UUID): Long
  fun findByUserIdAndIsReadFalse(userId: UUID): List<Notification>
}
