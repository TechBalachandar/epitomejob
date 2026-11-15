package com.epitomejob.resume.repository

import com.epitomejob.resume.model.Resume
import org.springframework.data.jpa.repository.JpaRepository
import java.util.List
import java.util.UUID

interface ResumeRepository : JpaRepository<Resume, UUID> {
  fun findByUserIdOrderByCreatedAtDesc(userId: UUID): List<Resume>
}
