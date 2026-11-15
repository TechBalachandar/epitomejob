package com.epitomejob.interview.service

import com.epitomejob.interview.model.Answer
import com.epitomejob.interview.model.InterviewSession
import com.epitomejob.interview.model.Question
import com.epitomejob.interview.repository.AnswerRepository
import com.epitomejob.interview.repository.InterviewSessionRepository
import com.epitomejob.interview.repository.QuestionRepository
import com.epitomejob.job.model.Job
import com.epitomejob.job.repository.JobRepository
import com.epitomejob.notification.service.NotificationService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.Optional
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class InterviewServiceTest {

  companion object {
    private val USER_ID: UUID = UUID.randomUUID()
    private val JOB_ID: UUID = UUID.randomUUID()
    private val SESSION_ID: UUID = UUID.randomUUID()
  }

  @Mock
  private lateinit var questions: QuestionRepository

  @Mock
  private lateinit var sessions: InterviewSessionRepository

  @Mock
  private lateinit var answers: AnswerRepository

  @Mock
  private lateinit var jobs: JobRepository

  @Mock
  private lateinit var notifications: NotificationService

  @AfterEach
  fun clearContext() {
    SecurityContextHolder.clearContext()
  }

  private fun authenticate() {
    SecurityContextHolder.getContext().authentication =
      UsernamePasswordAuthenticationToken(USER_ID.toString(), null, emptyList())
  }

  private fun service(): InterviewService {
    return InterviewService(questions, sessions, answers, jobs, notifications)
  }

  private fun job(): Job {
    return Job(
      "Senior Software Engineer, Backend (Java)", "Northwind Health Systems",
      "Reston, VA", "Hybrid", 145000, 175000, true,
      listOf("Java 21", "Spring Boot", "Kafka"), "Backend APIs.", "Backend",
      Instant.parse("2026-11-10T09:00:00Z")
    )
  }

  private fun question(n: Int): Question {
    return Question("Backend", "Technical", "Question $n", listOf("java", "kafka", "spring"), n)
  }

  /** n words of filler, optionally ending with a period. */
  private fun words(n: Int): String {
    return "word ".repeat(maxOf(0, n)).trim()
  }

  @Test
  fun createSessionCreatesInterviewCreatedNotification() {
    authenticate()
    Mockito.`when`(jobs.findById(JOB_ID)).thenReturn(Optional.of(job()))
    Mockito.`when`(questions.findByRoleOrderBySortOrderAsc("Backend"))
      .thenReturn(listOf(question(1), question(2)))
    Mockito.`when`(sessions.save(any())).thenAnswer { inv -> inv.getArgument<InterviewSession>(0) }

    val dto = service().createSession(JOB_ID)

    assertEquals("Senior Software Engineer, Backend (Java)", dto.jobTitle)
    verify(notifications).create(
      eq(USER_ID), eq("INTERVIEW_CREATED"),
      eq("Mock interview started"),
      eq(
        "Your mock interview for Senior Software Engineer, Backend (Java)" +
          " is ready — 2 questions."
      ),
      eq("/interviews")
    )
  }

  @Test
  fun structureScoresStarWordsLengthAndSentences() {
    val svc = service()
    // STAR words + 49 words + 2 sentences + in 40-250 range = 3+3+2+2 = 10.
    val answer = "Situation task action result. Second sentence here. " + words(42)
    assertEquals(10, svc.scoreStructure(answer))
    assertEquals(0, svc.scoreStructure("short"))
  }

  @Test
  fun relevanceIsKeywordHitRate() {
    val svc = service()
    assertEquals(
      7, svc.scoreRelevance(
        "I used java and kafka daily.",
        listOf("java", "kafka", "azure")
      )
    )
    assertEquals(0, svc.scoreRelevance("nothing relevant here", listOf("java", "kafka")))
    assertEquals(0, svc.scoreRelevance("anything", listOf()))
  }

  @Test
  fun specificityNeedsDigitSkillTokenAndLength() {
    val svc = service()
    val rich = "We cut latency by 40 percent for 10000 users using kafka. " + words(55)
    assertEquals(10, svc.scoreSpecificity(rich, listOf("Kafka")))
    assertEquals(0, svc.scoreSpecificity("short answer", listOf("Kafka")))
    assertEquals(4, svc.scoreSpecificity("we served 5 users", listOf("Kafka")))
  }

  @Test
  fun concisenessBands() {
    val svc = service()
    assertEquals(4, svc.scoreConciseness(words(10)))
    assertEquals(7, svc.scoreConciseness(words(30)))
    assertEquals(10, svc.scoreConciseness(words(100)))
    assertEquals(7, svc.scoreConciseness(words(250)))
    assertEquals(4, svc.scoreConciseness(words(400)))
  }

  @Test
  fun overallIsMeanScaledTo100() {
    assertEquals(100, service().overallScore(10, 10, 10, 10))
    assertEquals(75, service().overallScore(7, 8, 6, 9))
    assertEquals(0, service().overallScore(0, 0, 0, 0))
  }

  @Test
  fun feedbackUsesVerbatimMissLinesThenPositive() {
    val svc = service()
    val feedback = svc.buildFeedback("short", listOf("java", "kafka"), 0, 0, 0, 4)
    assertEquals("Add structure: walk through Situation → Task → Action → Result.", feedback[0])
    assertEquals("Tie your answer to the question: mention java, kafka.", feedback[1])
    assertEquals("Add a concrete number or outcome (e.g. users impacted, latency reduced).", feedback[2])
    assertEquals("Aim for 40–200 words — shorter and sharper.", feedback[3])
    assertEquals("Good start — add one concrete example and tighten the structure.", feedback[4])
  }

  @Test
  fun feedbackPicksStrongestDimensionAsPositive() {
    val svc = service()
    val feedback = svc.buildFeedback("x", listOf("java"), 10, 3, 2, 10)
    assertTrue(feedback.contains("Clear structure — easy to follow."))
    assertTrue(feedback.contains("Tie your answer to the question: mention java."))
  }

  @Test
  fun submitAnswerRejectsEmptyAnswerAndForeignQuestion() {
    authenticate()
    val session = InterviewSession(USER_ID, JOB_ID)
    Mockito.`when`(sessions.findById(SESSION_ID)).thenReturn(Optional.of(session))
    Mockito.`when`(jobs.findById(JOB_ID)).thenReturn(Optional.of(job()))
    val roleQuestions = listOf(question(1), question(2))
    Mockito.`when`(questions.findByRoleOrderBySortOrderAsc("Backend")).thenReturn(roleQuestions)

    val empty = assertThrows(ResponseStatusException::class.java) {
      service().submitAnswer(SESSION_ID, roleQuestions[0].id, "  ")
    }
    assertEquals(400, empty.statusCode.value())

    val foreign = assertThrows(ResponseStatusException::class.java) {
      service().submitAnswer(SESSION_ID, UUID.randomUUID(), "an answer")
    }
    assertEquals(400, foreign.statusCode.value())
  }

  @Test
  fun submitAnswerForOtherUsersSessionIs404() {
    authenticate()
    val other = InterviewSession(UUID.randomUUID(), JOB_ID)
    Mockito.`when`(sessions.findById(SESSION_ID)).thenReturn(Optional.of(other))

    val ex = assertThrows(ResponseStatusException::class.java) {
      service().submitAnswer(SESSION_ID, UUID.randomUUID(), "answer")
    }
    assertEquals(404, ex.statusCode.value())
  }

  @Test
  fun answeringAllRoleQuestionsCompletesSession() {
    authenticate()
    val session = InterviewSession(USER_ID, JOB_ID)
    Mockito.`when`(sessions.findById(SESSION_ID)).thenReturn(Optional.of(session))
    val j = job()
    Mockito.`when`(jobs.findById(JOB_ID)).thenReturn(Optional.of(j))
    val roleQuestions = listOf(question(1), question(2))
    Mockito.`when`(questions.findByRoleOrderBySortOrderAsc("Backend")).thenReturn(roleQuestions)
    val q2 = roleQuestions[1]
    Mockito.`when`(answers.findBySessionIdAndQuestionId(SESSION_ID, q2.id))
      .thenReturn(Optional.empty())
    Mockito.`when`(answers.save(any())).thenAnswer { inv -> inv.getArgument<Answer>(0) }
    val first = Answer(
      SESSION_ID, roleQuestions[0].id, "first",
      5, 5, 5, 5, 50, listOf("Good start — add one concrete example and tighten the structure.")
    )
    val second = Answer(
      SESSION_ID, q2.id, "second",
      5, 5, 5, 5, 50, listOf("Good start — add one concrete example and tighten the structure.")
    )
    Mockito.`when`(answers.findBySessionId(session.id)).thenReturn(listOf(first, second))
    Mockito.`when`(sessions.save(any())).thenAnswer { inv -> inv.getArgument<InterviewSession>(0) }

    val dto = service().submitAnswer(
      SESSION_ID, q2.id,
      "Situation task action result with java and kafka for 5 users. " + words(40)
    )

    assertEquals(q2.id, dto.questionId)
    assertEquals("COMPLETED", session.status)
    assertTrue(session.completedAt != null)
  }

  @Test
  fun questionsFallBackToGeneralRole() {
    authenticate()
    val general = Question(
      "General", "Behavioral", "General question",
      listOf("situation", "action"), 1
    )
    Mockito.`when`(questions.findByRoleOrderBySortOrderAsc("Backend")).thenReturn(emptyList())
    Mockito.`when`(questions.findByRoleOrderBySortOrderAsc("General")).thenReturn(listOf(general))

    val found = service().roleQuestions("Backend")
    assertEquals(1, found.size)
    assertEquals("General", found[0].role)
  }
}
