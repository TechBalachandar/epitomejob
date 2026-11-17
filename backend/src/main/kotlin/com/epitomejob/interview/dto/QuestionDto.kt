package com.epitomejob.interview.dto

import com.epitomejob.interview.model.Question
import java.util.UUID

/** Question shape, exact field names per the W2 contract §3. */
data class QuestionDto(
  val id: UUID,
  val text: String,
  val category: String,
  val role: String
) {

  companion object {
    @JvmStatic
    fun from(question: Question): QuestionDto {
      return QuestionDto(question.id, question.text, question.category, question.role)
    }
  }
}
