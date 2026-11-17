package com.epitomejob.interview.service

import com.epitomejob.common.constants.AppConstants
import com.epitomejob.common.util.SecurityUtil
import com.epitomejob.interview.dto.AnswerDto
import com.epitomejob.interview.dto.QuestionDto
import com.epitomejob.interview.dto.SessionDto
import com.epitomejob.interview.model.Answer
import com.epitomejob.interview.model.InterviewSession
import com.epitomejob.interview.model.Question
import com.epitomejob.interview.repository.AnswerRepository
import com.epitomejob.interview.repository.InterviewSessionRepository
import com.epitomejob.interview.repository.QuestionRepository
import com.epitomejob.job.model.Job
import com.epitomejob.job.repository.JobRepository
import com.epitomejob.notification.service.NotificationService
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.UUID

/**
 * Mock interviews (M8, W2 contract §5). Deterministic rubric heuristics —
 * the exact formulas live in [scoreStructure] and friends.
 * A session is COMPLETED once every question for the job's role has an
 * answer; posting an answer again REPLACES the previous one (re-scored).
 */
@Service
class InterviewService(
  private val questions: QuestionRepository,
  private val sessions: InterviewSessionRepository,
  private val answers: AnswerRepository,
  private val jobs: JobRepository,
  private val notifications: NotificationService
) {

  @Transactional(readOnly = true)
  fun questionsForJob(jobId: UUID): List<QuestionDto> {
    val job = findJob(jobId)
    return roleQuestions(job.role).map { QuestionDto.from(it) }
  }

  /** Always creates a new session — retakes are allowed. */
  @Transactional
  fun createSession(jobId: UUID): SessionDto {
    val job = findJob(jobId)
    val roleQuestions = roleQuestions(job.role)
    val session = sessions.save(InterviewSession(SecurityUtil.currentUserId(), jobId))
    notifications.create(
      SecurityUtil.currentUserId(),
      AppConstants.Notification.TYPE_INTERVIEW_CREATED,
      AppConstants.Notification.INTERVIEW_TITLE,
      String.format(
        AppConstants.Notification.INTERVIEW_BODY_FORMAT, job.title, roleQuestions.size
      ),
      AppConstants.Notification.LINK_INTERVIEWS
    )
    return SessionDto.from(session, job, roleQuestions, emptyList())
  }

  @Transactional
  fun submitAnswer(sessionId: UUID, questionId: UUID, answerText: String?): AnswerDto {
    val session = findOwnedSession(sessionId)
    if (answerText == null || answerText.isBlank()) {
      throw ResponseStatusException(HttpStatus.BAD_REQUEST, AppConstants.Errors.ANSWER_EMPTY)
    }
    val job = findJob(session.jobId)
    val roleQuestions = roleQuestions(job.role)
    val question = roleQuestions.firstOrNull { it.id == questionId }
      ?: throw ResponseStatusException(
        HttpStatus.BAD_REQUEST, AppConstants.Errors.QUESTION_NOT_IN_SESSION
      )
    val keywords = if (question.keywords.isEmpty()) job.skills else question.keywords
    val structure = scoreStructure(answerText)
    val relevance = scoreRelevance(answerText, keywords)
    val specificity = scoreSpecificity(answerText, job.skills)
    val conciseness = scoreConciseness(answerText)
    val overall = overallScore(structure, relevance, specificity, conciseness)
    val feedback = buildFeedback(answerText, keywords, structure, relevance, specificity, conciseness)

    val answer = answers.findBySessionIdAndQuestionId(sessionId, questionId)
      .orElseGet {
        Answer(
          sessionId, questionId, answerText,
          structure, relevance, specificity, conciseness, overall, feedback
        )
      }
    answer.answerText = answerText
    answer.structure = structure
    answer.relevance = relevance
    answer.specificity = specificity
    answer.conciseness = conciseness
    answer.overallScore = overall
    answer.feedback = feedback
    val saved = answers.save(answer)

    maybeComplete(session, roleQuestions)
    return AnswerDto.from(saved, question.text)
  }

  @Transactional(readOnly = true)
  fun listMine(): List<SessionDto> {
    val userId = SecurityUtil.currentUserId()
    return sessions.findByUserIdOrderByCreatedAtDesc(userId).map { toDto(it) }
  }

  @Transactional(readOnly = true)
  fun getSession(id: UUID): SessionDto {
    return toDto(findOwnedSession(id))
  }

  private fun toDto(session: InterviewSession): SessionDto {
    val job = findJob(session.jobId)
    val roleQuestions = roleQuestions(job.role)
    return SessionDto.from(session, job, roleQuestions, answers.findBySessionId(session.id))
  }

  private fun maybeComplete(session: InterviewSession, roleQuestions: List<Question>) {
    if (AppConstants.Interview.STATUS_COMPLETED == session.status || roleQuestions.isEmpty()) {
      return
    }
    val answered = mutableSetOf<UUID>()
    answers.findBySessionId(session.id).forEach { answered.add(it.questionId) }
    val allAnswered = roleQuestions.all { answered.contains(it.id) }
    if (allAnswered) {
      session.status = AppConstants.Interview.STATUS_COMPLETED
      if (session.completedAt == null) {
        session.completedAt = Instant.now()
      }
      sessions.save(session)
    }
  }

  /** Questions for a role; falls back to General when the role has none. */
  internal fun roleQuestions(role: String): List<Question> {
    val found = questions.findByRoleOrderBySortOrderAsc(role)
    if (found.isEmpty() && AppConstants.Job.ROLE_GENERAL != role) {
      return questions.findByRoleOrderBySortOrderAsc(AppConstants.Job.ROLE_GENERAL)
    }
    return found
  }

  // ---- Rubric (contract §5, exact formulas) ----

  /** STAR word 3 + >=40 words 3 + >=2 sentences 2 + 40–250 words 2, capped at 10. */
  fun scoreStructure(answerText: String): Int {
    val words = wordCount(answerText)
    val sentences = sentenceCount(answerText)
    val normalized = normalize(answerText)
    var score = 0
    if (AppConstants.Interview.STAR_WORDS.any { normalized.contains(it) }) {
      score += 3
    }
    if (words >= 40) {
      score += 3
    }
    if (sentences >= 2) {
      score += 2
    }
    if (words >= 40 && words <= 250) {
      score += 2
    }
    return minOf(AppConstants.Interview.MAX_SCORE, score)
  }

  /** round(10 * matched keywords / total keywords). */
  fun scoreRelevance(answerText: String, keywords: List<String>?): Int {
    if (keywords == null || keywords.isEmpty()) {
      return 0
    }
    val normalized = normalize(answerText)
    val matched = keywords.count { normalize(it).isNotEmpty() && normalized.contains(normalize(it)) }
    return Math.round(AppConstants.Interview.MAX_SCORE * matched.toDouble() / keywords.size).toInt()
  }

  /** Digit 4 + any job-skill token 3 + >=60 words 3, capped at 10. */
  fun scoreSpecificity(answerText: String, jobSkills: List<String>?): Int {
    val words = wordCount(answerText)
    val normalized = normalize(answerText)
    var score = 0
    if (answerText.any { it.isDigit() }) {
      score += 4
    }
    if (jobSkills != null && jobSkills
        .flatMap { skill -> normalize(skill).split(AppConstants.Matching.WHITESPACE_REGEX.toRegex()) }
        .filter { it.isNotEmpty() }
        .any { normalized.contains(it) }
    ) {
      score += 3
    }
    if (words >= 60) {
      score += 3
    }
    return minOf(AppConstants.Interview.MAX_SCORE, score)
  }

  /** 10 for 40–200 words, 7 for 20–39 or 201–300, else 4. */
  fun scoreConciseness(answerText: String): Int {
    val words = wordCount(answerText)
    if (words >= 40 && words <= 200) {
      return AppConstants.Interview.MAX_SCORE
    }
    if ((words >= 20 && words < 40) || (words > 200 && words <= 300)) {
      return 7
    }
    return 4
  }

  /** round(10 * mean of the four dimension scores). */
  fun overallScore(structure: Int, relevance: Int, specificity: Int, conciseness: Int): Int {
    return Math.round(
      AppConstants.Interview.OVERALL_SCALE * (structure + relevance + specificity + conciseness) / 4.0 / 10.0
    ).toInt()
  }

  /**
   * Feedback, in order: one miss line per dimension below 5, then one line
   * for the strongest dimension (>= 8) or the generic line when none is >= 8.
   * Templates are verbatim from contract §5.
   */
  fun buildFeedback(
    answerText: String,
    keywords: List<String>?,
    structure: Int,
    relevance: Int,
    specificity: Int,
    conciseness: Int
  ): List<String> {
    val feedback = mutableListOf<String>()
    if (structure < AppConstants.Interview.FEEDBACK_THRESHOLD_LOW) {
      feedback.add(AppConstants.Interview.FEEDBACK_STRUCTURE_MISS)
    }
    if (relevance < AppConstants.Interview.FEEDBACK_THRESHOLD_LOW) {
      val missing = missingKeywords(answerText, keywords)
      feedback.add(
        AppConstants.Interview.FEEDBACK_RELEVANCE_MISS_PREFIX +
          missing.subList(0, minOf(2, missing.size)).joinToString(", ") + "."
      )
    }
    if (specificity < AppConstants.Interview.FEEDBACK_THRESHOLD_LOW) {
      feedback.add(AppConstants.Interview.FEEDBACK_SPECIFICITY_MISS)
    }
    if (conciseness < AppConstants.Interview.FEEDBACK_THRESHOLD_LOW) {
      feedback.add(AppConstants.Interview.FEEDBACK_CONCISENESS_MISS)
    }
    val scores = intArrayOf(structure, relevance, specificity, conciseness)
    val positives = arrayOf(
      AppConstants.Interview.FEEDBACK_STRUCTURE_GOOD,
      AppConstants.Interview.FEEDBACK_RELEVANCE_GOOD,
      AppConstants.Interview.FEEDBACK_SPECIFICITY_GOOD,
      AppConstants.Interview.FEEDBACK_CONCISENESS_GOOD
    )
    var bestIndex = -1
    for (i in scores.indices) {
      if (scores[i] >= AppConstants.Interview.FEEDBACK_THRESHOLD_HIGH &&
        (bestIndex < 0 || scores[i] > scores[bestIndex])
      ) {
        bestIndex = i
      }
    }
    feedback.add(if (bestIndex >= 0) positives[bestIndex] else AppConstants.Interview.FEEDBACK_GENERIC)
    return feedback
  }

  private fun missingKeywords(answerText: String, keywords: List<String>?): List<String> {
    if (keywords == null || keywords.isEmpty()) {
      return emptyList()
    }
    val normalized = normalize(answerText)
    return keywords.filter { normalize(it).isNotEmpty() && !normalized.contains(normalize(it)) }
  }

  private fun findOwnedSession(id: UUID): InterviewSession {
    val userId = SecurityUtil.currentUserId()
    return sessions.findById(id)
      .filter { it.userId == userId }
      .orElseThrow {
        ResponseStatusException(HttpStatus.NOT_FOUND, AppConstants.Errors.SESSION_NOT_FOUND)
      }
  }

  private fun findJob(jobId: UUID): Job {
    return jobs.findById(jobId)
      .orElseThrow {
        ResponseStatusException(HttpStatus.NOT_FOUND, AppConstants.Errors.JOB_NOT_FOUND)
      }
  }

  companion object {
    internal fun wordCount(text: String?): Int {
      if (text == null || text.isBlank()) {
        return 0
      }
      return text.trim().split(AppConstants.Matching.WHITESPACE_REGEX.toRegex()).size
    }

    /** Count of [.!?] boundaries; at least 1 when the text is non-empty. */
    internal fun sentenceCount(text: String?): Int {
      if (text == null || text.isEmpty()) {
        return 0
      }
      return maxOf(1, text.count { it == '.' || it == '!' || it == '?' })
    }

    private fun normalize(value: String?): String {
      if (value == null) {
        return ""
      }
      return value.lowercase().replace(AppConstants.Matching.WHITESPACE_REGEX.toRegex(), " ").trim()
    }
  }
}
