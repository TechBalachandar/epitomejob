package com.epitomejob.interview.controller

import com.epitomejob.common.security.SecurityConfig
import com.epitomejob.identity.service.JwtService
import com.epitomejob.interview.dto.AnswerDto
import com.epitomejob.interview.dto.QuestionDto
import com.epitomejob.interview.dto.SessionDto
import com.epitomejob.interview.service.InterviewService
import io.jsonwebtoken.Claims
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.UUID

@WebMvcTest(InterviewController::class)
@Import(SecurityConfig::class)
class InterviewControllerTest {

  companion object {
    private val USER_ID: UUID = UUID.randomUUID()
    private val JOB_ID: UUID = UUID.randomUUID()
    private val SESSION_ID: UUID = UUID.randomUUID()
    private val QUESTION_ID: UUID = UUID.randomUUID()
  }

  @Autowired
  private lateinit var mockMvc: MockMvc

  @MockitoBean
  private lateinit var service: InterviewService

  @MockitoBean
  private lateinit var jwt: JwtService

  private fun authenticate() {
    val claims = mock<Claims>()
    whenever(claims.subject).thenReturn(USER_ID.toString())
    whenever(claims.get("role", String::class.java)).thenReturn("ROLE_USER")
    whenever(jwt.parse("test-token")).thenReturn(claims)
  }

  private fun sessionDto(): SessionDto {
    return SessionDto(
      SESSION_ID, JOB_ID, "Senior Software Engineer, Backend (Java)",
      "IN_PROGRESS", Instant.now(), null, emptyList(), null
    )
  }

  @Test
  fun questionsReturnsRoleQuestions() {
    authenticate()
    whenever(service.questionsForJob(JOB_ID)).thenReturn(
      listOf(
        QuestionDto(QUESTION_ID, "Explain REST APIs.", "Technical", "Backend")
      )
    )

    mockMvc.perform(
      get("/interviews/questions?jobId=$JOB_ID")
        .header("Authorization", "Bearer <redacted>")
    )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$[0].category").value("Technical"))
  }

  @Test
  fun questionsWithoutJobIdIs400() {
    authenticate()

    mockMvc.perform(
      get("/interviews/questions")
        .header("Authorization", "Bearer <redacted>")
    )
      .andExpect(status().isBadRequest())
  }

  @Test
  fun createSessionReturns201() {
    authenticate()
    whenever(service.createSession(JOB_ID)).thenReturn(sessionDto())

    mockMvc.perform(
      post("/interviews/sessions")
        .header("Authorization", "Bearer <redacted>")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"jobId\":\"$JOB_ID\"}")
    )
      .andExpect(status().isCreated())
      .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
  }

  @Test
  fun submitAnswerReturnsAnswer() {
    authenticate()
    val answer = AnswerDto(
      UUID.randomUUID(), QUESTION_ID, "Explain REST APIs.",
      "My answer.", AnswerDto.AnswerScores(7, 8, 6, 9), 75,
      listOf("Strongly relevant to the question.")
    )
    whenever(service.submitAnswer(eq(SESSION_ID), eq(QUESTION_ID), any()))
      .thenReturn(answer)

    mockMvc.perform(
      post("/interviews/sessions/$SESSION_ID/answers")
        .header("Authorization", "Bearer <redacted>")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"questionId\":\"$QUESTION_ID\",\"answerText\":\"My answer.\"}")
    )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.overallScore").value(75))
      .andExpect(jsonPath("$.scores.relevance").value(8))
  }

  @Test
  fun submitEmptyAnswerIs400() {
    authenticate()

    mockMvc.perform(
      post("/interviews/sessions/$SESSION_ID/answers")
        .header("Authorization", "Bearer <redacted>")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"questionId\":\"$QUESTION_ID\",\"answerText\":\"  \"}")
    )
      .andExpect(status().isBadRequest())
  }

  @Test
  fun getSessionReturnsSession() {
    authenticate()
    whenever(service.getSession(SESSION_ID)).thenReturn(sessionDto())

    mockMvc.perform(
      get("/interviews/sessions/$SESSION_ID")
        .header("Authorization", "Bearer <redacted>")
    )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.jobTitle").value("Senior Software Engineer, Backend (Java)"))
  }

  @Test
  fun getMissingSessionIs404() {
    authenticate()
    whenever(service.getSession(SESSION_ID))
      .thenThrow(ResponseStatusException(HttpStatus.NOT_FOUND, "Interview session not found"))

    mockMvc.perform(
      get("/interviews/sessions/$SESSION_ID")
        .header("Authorization", "Bearer <redacted>")
    )
      .andExpect(status().isNotFound())
  }

  @Test
  fun listMineReturnsSessions() {
    authenticate()
    whenever(service.listMine()).thenReturn(listOf(sessionDto()))

    mockMvc.perform(
      get("/interviews/sessions/me")
        .header("Authorization", "Bearer <redacted>")
    )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$[0].status").value("IN_PROGRESS"))
  }
}
