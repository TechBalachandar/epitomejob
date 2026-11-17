package com.epitomejob.interview.model

import com.epitomejob.common.constants.AppConstants
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.UUID

/** One scored answer. Feedback lines are newline-separated TEXT. */
@Entity
@Table(name = "answers", schema = "interview")
class Answer(
  @Column(nullable = false)
  var sessionId: UUID = UUID.randomUUID(),

  @Column(nullable = false)
  var questionId: UUID = UUID.randomUUID(),

  @Column(nullable = false, columnDefinition = "TEXT")
  var answerText: String = "",

  @Column(nullable = false)
  var structure: Int = 0,

  @Column(nullable = false)
  var relevance: Int = 0,

  @Column(nullable = false)
  var specificity: Int = 0,

  @Column(nullable = false)
  var conciseness: Int = 0,

  @Column(nullable = false)
  var overallScore: Int = 0
) {

  @Id
  var id: UUID = UUID.randomUUID()

  /**
   * Stored feedback text (newline-separated). The column keeps the Java
   * field's name (`feedback`); the public property exposes the parsed lines.
   */
  @Column(name = "feedback", nullable = false, columnDefinition = "TEXT")
  private var feedbackText: String = ""

  var feedback: List<String>
    get() {
      if (feedbackText.isBlank()) return emptyList()
      return feedbackText.split(AppConstants.Interview.FEEDBACK_SEPARATOR)
        .filter { it.isNotBlank() }
    }
    set(value) {
      feedbackText = value.joinToString(AppConstants.Interview.FEEDBACK_SEPARATOR)
    }

  constructor(
    sessionId: UUID,
    questionId: UUID,
    answerText: String,
    structure: Int,
    relevance: Int,
    specificity: Int,
    conciseness: Int,
    overallScore: Int,
    feedbackLines: List<String>?
  ) : this(sessionId, questionId, answerText, structure, relevance, specificity, conciseness, overallScore) {
    this.feedback = feedbackLines ?: emptyList()
  }
}
