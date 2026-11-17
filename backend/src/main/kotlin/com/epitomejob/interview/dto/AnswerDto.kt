package com.epitomejob.interview.dto

import com.epitomejob.interview.model.Answer
import java.util.UUID

/** Answer shape, exact field names per the W2 contract §3. */
data class AnswerDto(
  val id: UUID,
  val questionId: UUID,
  val questionText: String,
  val answerText: String,
  val scores: AnswerScores,
  val overallScore: Int,
  val feedback: List<String>
) {

  data class AnswerScores(val structure: Int, val relevance: Int, val specificity: Int, val conciseness: Int)

  companion object {
    @JvmStatic
    fun from(answer: Answer, questionText: String): AnswerDto {
      return AnswerDto(
        answer.id, answer.questionId, questionText, answer.answerText,
        AnswerScores(answer.structure, answer.relevance, answer.specificity, answer.conciseness),
        answer.overallScore, answer.feedback
      )
    }
  }
}
