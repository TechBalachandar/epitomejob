package com.epitomejob.coach.controller;

import com.epitomejob.coach.service.AgentBrainClient;
import com.epitomejob.coach.service.CoachService;
import com.epitomejob.common.exception.AgentUnavailableException;
import com.epitomejob.common.security.SecurityConfig;
import com.epitomejob.identity.service.JwtService;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @WebMvcTest slice for POST /coach/chat. The agent client is mocked; the
 * real CoachService runs so the user-id resolution is exercised. The real
 * HTTP mapping (passthrough / 401 / 502) is covered in AgentBrainClientTest.
 */
@WebMvcTest(CoachController.class)
@Import({SecurityConfig.class, CoachService.class})
class CoachControllerTest {

  private static final UUID USER_ID = UUID.randomUUID();
  private static final String AGENT_BODY =
      "{\"reply\":\"hi\",\"citations\":[],\"actions\":[],\"specialistsUsed\":[\"matching\"]}";

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private AgentBrainClient agentBrain;

  @MockitoBean
  private JwtService jwt;

  private void authenticate() {
    Claims claims = mock(Claims.class);
    when(claims.getSubject()).thenReturn(USER_ID.toString());
    when(claims.get("role", String.class)).thenReturn("ROLE_USER");
    when(jwt.parse("test-token")).thenReturn(claims);
  }

  @Test
  void chatReturnsAgentBrainResponseVerbatim() throws Exception {
    authenticate();
    when(agentBrain.chat(eq("find me a job"), eq(USER_ID), eq("Bearer test-token")))
        .thenReturn(ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(AGENT_BODY));

    mockMvc.perform(post("/coach/chat")
            .header("Authorization", "Bearer test-token")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"message\":\"find me a job\"}"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(content().json(AGENT_BODY));
  }

  @Test
  void chatForwardsAgentBrain401() throws Exception {
    authenticate();
    when(agentBrain.chat(any(), any(), any()))
        .thenReturn(ResponseEntity.status(401)
            .contentType(MediaType.APPLICATION_JSON)
            .body("{\"code\":\"UNAUTHORIZED\",\"message\":\"bad token\"}"));

    mockMvc.perform(post("/coach/chat")
            .header("Authorization", "Bearer test-token")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"message\":\"hi\"}"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
  }

  @Test
  void chatWhenAgentBrainUnreachableIs502() throws Exception {
    authenticate();
    when(agentBrain.chat(any(), any(), any()))
        .thenThrow(new AgentUnavailableException(new RuntimeException("refused")));

    mockMvc.perform(post("/coach/chat")
            .header("Authorization", "Bearer test-token")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"message\":\"hi\"}"))
        .andExpect(status().isBadGateway())
        .andExpect(jsonPath("$.code").value("AGENT_UNAVAILABLE"))
        .andExpect(jsonPath("$.message").value("Coach agent is not reachable"));
  }

  @Test
  void chatWithBlankMessageIs400() throws Exception {
    authenticate();

    mockMvc.perform(post("/coach/chat")
            .header("Authorization", "Bearer test-token")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"message\":\"   \"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void chatWithoutTokenIsForbidden() throws Exception {
    mockMvc.perform(post("/coach/chat")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"message\":\"hi\"}"))
        .andExpect(status().isForbidden());
  }
}
