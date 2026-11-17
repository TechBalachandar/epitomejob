package com.epitomejob.interview.dto

import com.epitomejob.interview.model.Answer
import com.epitomejob.interview.model.InterviewSession
import com.epitomejob.interview.model.Question
import com.epitomejob.job.model.Job
import java.time.Instant
import java.util.UUID

/** Session shape, exact field names per the W2 contract §3. */
data class SessionDto(
  val id: UUID,
  val jobId: UUID,
  val jobTitle: String,
  val status: String,
  val createdAt: Instant,
  val completedAt: Instant?,
  val answers: List<AnswerDto>,
  val averageScore: Int?
) {

  companion object {
    /**
     * Answers are listed in the role-question order. averageScore is the
     * rounded mean of the answer overallScores, null when unanswered.
     */
    @JvmStatic
    fun from(
      session: InterviewSession,
      job: Job,
      roleQuestions: List<Question>,
      answers: List<Answer>
    ): SessionDto {
      val answersByQuestion = answers.associateBy { it.questionId }
      val dtos = roleQuestions
        .filter { answersByQuestion.containsKey(it.id) }
        .map { q -> AnswerDto.from(answersByQuestion[q.id]!!, q.text) }
      val average = if (answers.isEmpty()) null
        else Math.round(answers.map { it.overallScore }.average()).toInt()
      return SessionDto(
        session.id, session.jobId, job.title, session.status,
        session.createdAt, session.completedAt, dtos, average
      )
    }
  }
}
