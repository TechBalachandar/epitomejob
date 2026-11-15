package com.epitomejob.interview.repository

import com.epitomejob.interview.model.Question
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface QuestionRepository : JpaRepository<Question, UUID> {
  fun findByRoleOrderBySortOrderAsc(role: String): List<Question>
}
