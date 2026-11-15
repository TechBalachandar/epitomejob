package com.epitomejob.interview.repository

import com.epitomejob.interview.model.InterviewSession
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface InterviewSessionRepository : JpaRepository<InterviewSession, UUID> {
  fun findByUserIdOrderByCreatedAtDesc(userId: UUID): List<InterviewSession>
}
