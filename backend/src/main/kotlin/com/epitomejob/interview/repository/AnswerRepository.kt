package com.epitomejob.interview.repository

import com.epitomejob.interview.model.Answer
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional
import java.util.UUID

interface AnswerRepository : JpaRepository<Answer, UUID> {
  fun findBySessionId(sessionId: UUID): List<Answer>
  fun findBySessionIdAndQuestionId(sessionId: UUID, questionId: UUID): Optional<Answer>
}
