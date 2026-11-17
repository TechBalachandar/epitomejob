package com.epitomejob.coach.controller

import com.epitomejob.coach.service.AgentBrainClient
import com.epitomejob.coach.service.CoachService
import com.epitomejob.common.exception.AgentUnavailableException
import com.epitomejob.common.security.SecurityConfig
import com.epitomejob.identity.service.JwtService
import com.epitomejob.identity.model.Role
import com.epitomejob.identity.model.User
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

/**
 * @WebMvcTest slice for POST /coach/chat. The agent client is mocked; the
 * real CoachService runs so the user-id resolution is exercised. The real
 * HTTP mapping (passthrough / 401 / 502) is covered in AgentBrainClientTest.
 */

@TestConfiguration
class RealJwtTestConfig {
  @Bean
  fun jwtService(): JwtService = JwtService(
    "test-secret-that-is-at-least-32-chars-long!!",
    3600000L,
    7200000L
  )
}

@WebMvcTest(CoachController::class)
@Import(SecurityConfig::class, CoachService::class, RealJwtTestConfig::class)
class CoachControllerTest {

  companion object {
    private val USER_ID: UUID = UUID.randomUUID()
    private const val AGENT_BODY =
      "{\"reply\":\"hi\",\"citations\":[],\"actions\":[],\"specialistsUsed\":[\"matching\"]}"
  }

  @Autowired
  private lateinit var mockMvc: MockMvc

  @MockitoBean
  private lateinit var agentBrain: AgentBrainClient

  @Autowired
  private lateinit var jwtService: JwtService

  private lateinit var authToken: String

  private fun authenticate() {
    val user = User("test@example.com", null, "Test User")
    user.id = USER_ID
    user.role = Role.USER
    authToken = jwtService.generateAccess(user)
  }

  @Test
  fun chatReturnsAgentBrainResponseVerbatim() {
    authenticate()
    whenever(agentBrain.chat(eq("find me a job"), eq(USER_ID), eq("Bearer $authToken")))
      .thenReturn(ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(AGENT_BODY))

    mockMvc.perform(
      post("/coach/chat")
        .header("Authorization", "Bearer $authToken")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"message\":\"find me a job\"}")
    )
      .andExpect(status().isOk())
      .andExpect(content().contentType(MediaType.APPLICATION_JSON))
      .andExpect(content().json(AGENT_BODY))
  }

  @Test
  fun chatForwardsAgentBrain401() {
    authenticate()
    whenever(agentBrain.chat(any(), any(), any()))
      .thenReturn(
        ResponseEntity.status(401)
          .contentType(MediaType.APPLICATION_JSON)
          .body("{\"code\":\"UNAUTHORIZED\",\"message\":\"bad token\"}")
      )

    mockMvc.perform(
      post("/coach/chat")
        .header("Authorization", "Bearer $authToken")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"message\":\"hi\"}")
    )
      .andExpect(status().isUnauthorized())
      .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
  }

  @Test
  fun chatWhenAgentBrainUnreachableIs502() {
    authenticate()
    whenever(agentBrain.chat(any(), any(), any()))
      .thenThrow(AgentUnavailableException(RuntimeException("refused")))

    mockMvc.perform(
      post("/coach/chat")
        .header("Authorization", "Bearer $authToken")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"message\":\"hi\"}")
    )
      .andExpect(status().isBadGateway())
      .andExpect(jsonPath("$.code").value("AGENT_UNAVAILABLE"))
      .andExpect(jsonPath("$.message").value("Coach agent is not reachable"))
  }

  @Test
  fun chatWithBlankMessageIs400() {
    authenticate()

    mockMvc.perform(
      post("/coach/chat")
        .header("Authorization", "Bearer $authToken")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"message\":\"   \"}")
    )
      .andExpect(status().isBadRequest())
  }

  @Test
  fun chatWithoutTokenIsForbidden() {
    mockMvc.perform(
      post("/coach/chat")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"message\":\"hi\"}")
    )
      .andExpect(status().isForbidden())
  }
}
