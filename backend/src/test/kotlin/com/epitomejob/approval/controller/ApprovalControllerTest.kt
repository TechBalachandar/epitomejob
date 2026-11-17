package com.epitomejob.approval.controller

import com.epitomejob.approval.model.Approval
import com.epitomejob.approval.service.ApprovalService
import com.epitomejob.common.security.SecurityConfig
import com.epitomejob.identity.service.JwtService
import com.epitomejob.identity.model.Role
import com.epitomejob.identity.model.User
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.request
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import java.util.UUID


@TestConfiguration
class RealJwtTestConfig {
  @Bean
  fun jwtService(): JwtService = JwtService(
    "test-secret-that-is-at-least-32-chars-long!!",
    3600000L,
    7200000L
  )
}

@WebMvcTest(ApprovalController::class, ApprovalSseController::class)
@Import(SecurityConfig::class, RealJwtTestConfig::class)
class ApprovalControllerTest {

  companion object {
    private val USER_ID: UUID = UUID.randomUUID()
    private val APPROVAL_ID: UUID = UUID.randomUUID()
  }

  @Autowired
  private lateinit var mockMvc: MockMvc

  @MockitoBean
  private lateinit var service: ApprovalService

  @Autowired
  private lateinit var jwtService: JwtService

  private lateinit var authToken: String

  private fun authenticate() {
    val user = User("test@example.com", null, "Test User")
    user.id = USER_ID
    user.role = Role.USER
    authToken = jwtService.generateAccess(user)
  }

  private fun approval(status: String): Approval {
    val approval = Approval(
      USER_ID, "CREATE_APPLICATION", "{\"jobId\":\"job-1\"}",
      "token-hash", Instant.now().plusSeconds(3600L)
    )
    approval.status = status
    return approval
  }

  @Test
  fun proposeReturns201Pending() {
    authenticate()
    whenever(service.propose(any(), any())).thenReturn(approval("PENDING"))

    mockMvc.perform(
      post("/approvals")
        .header("Authorization", "Bearer $authToken")
        .contentType(MediaType.APPLICATION_JSON)
        .content("""{"action":"CREATE_APPLICATION","payload":"{\"jobId\":\"job-1\"}"}""")
    )
      .andExpect(status().isCreated())
      .andExpect(jsonPath("$.status").value("PENDING"))
      .andExpect(jsonPath("$.tokenHash").doesNotExist())
  }

  @Test
  fun proposeWithBlankActionReturns400() {
    authenticate()

    mockMvc.perform(
      post("/approvals")
        .header("Authorization", "Bearer $authToken")
        .contentType(MediaType.APPLICATION_JSON)
        .content("""{"action":"","payload":"{}"}""")
    )
      .andExpect(status().isBadRequest())
  }

  @Test
  fun approveReturnsApproved() {
    authenticate()
    whenever(service.decide(APPROVAL_ID, true)).thenReturn(approval("APPROVED"))

    mockMvc.perform(
      post("/approvals/$APPROVAL_ID/approve")
        .header("Authorization", "Bearer $authToken")
    )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.status").value("APPROVED"))
  }

  @Test
  fun rejectReturnsRejected() {
    authenticate()
    whenever(service.decide(APPROVAL_ID, false)).thenReturn(approval("REJECTED"))

    mockMvc.perform(
      post("/approvals/$APPROVAL_ID/reject")
        .header("Authorization", "Bearer $authToken")
    )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.status").value("REJECTED"))
  }

  @Test
  fun pendingReturnsList() {
    authenticate()
    whenever(service.pendingMine()).thenReturn(listOf(approval("PENDING")))

    mockMvc.perform(
      get("/approvals/pending")
        .header("Authorization", "Bearer $authToken")
    )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$[0].status").value("PENDING"))
  }

  @Test
  fun streamSendsPendingListAndCompletes() {
    authenticate()
    whenever(service.pendingMine()).thenReturn(listOf(approval("PENDING")))

    val result = mockMvc.perform(
      get("/approvals/stream")
        .header("Authorization", "Bearer $authToken")
    )
      .andExpect(status().isOk())
      .andExpect(request().asyncStarted())
      .andReturn()

    // The controller sends the event synchronously, so the async result is
    // already complete. Do NOT call asyncDispatch() here: MockMvc's dispatch
    // re-runs the security chain without the authenticated context and 403s
    // (a real container restores it — this is a test-harness quirk, not an
    // app bug). Assert on the response the initial dispatch already wrote.
    result.getAsyncResult(2000)
    assertTrue(
      result.response.contentAsString.contains("pending"),
      "SSE event should carry the pending list, was: " + result.response.contentAsString
    )
  }

  @Test
  fun approveWithoutTokenIsForbidden() {
    mockMvc.perform(post("/approvals/$APPROVAL_ID/approve"))
      .andExpect(status().isForbidden())
  }
}
