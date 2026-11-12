package com.epitomejob.approval.controller;

import com.epitomejob.approval.model.Approval;
import com.epitomejob.approval.service.ApprovalService;
import com.epitomejob.common.security.SecurityConfig;
import com.epitomejob.identity.service.JwtService;
import io.jsonwebtoken.Claims;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({ApprovalController.class, ApprovalSseController.class})
@Import(SecurityConfig.class)
class ApprovalControllerTest {

  private static final UUID USER_ID = UUID.randomUUID();
  private static final UUID APPROVAL_ID = UUID.randomUUID();

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private ApprovalService service;

  @MockitoBean
  private JwtService jwt;

  private void authenticate() {
    Claims claims = mock(Claims.class);
    when(claims.getSubject()).thenReturn(USER_ID.toString());
    when(claims.get("role", String.class)).thenReturn("ROLE_USER");
    when(jwt.parse("test-token")).thenReturn(claims);
  }

  private Approval approval(String status) {
    Approval approval = new Approval(USER_ID, "CREATE_APPLICATION", "{\"jobId\":\"job-1\"}",
        "token-hash", Instant.now().plusSeconds(3600));
    approval.setStatus(status);
    return approval;
  }

  @Test
  void proposeReturns201Pending() throws Exception {
    authenticate();
    when(service.propose(any(), any())).thenReturn(approval("PENDING"));

    mockMvc.perform(post("/approvals")
            .header("Authorization", "Bearer test-token")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"action":"CREATE_APPLICATION","payload":"{\\"jobId\\":\\"job-1\\"}"}"""))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("PENDING"))
        .andExpect(jsonPath("$.tokenHash").doesNotExist());
  }

  @Test
  void proposeWithBlankActionReturns400() throws Exception {
    authenticate();

    mockMvc.perform(post("/approvals")
            .header("Authorization", "Bearer test-token")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"action":"","payload":"{}"}"""))
        .andExpect(status().isBadRequest());
  }

  @Test
  void approveReturnsApproved() throws Exception {
    authenticate();
    when(service.decide(APPROVAL_ID, true)).thenReturn(approval("APPROVED"));

    mockMvc.perform(post("/approvals/" + APPROVAL_ID + "/approve")
            .header("Authorization", "Bearer test-token"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("APPROVED"));
  }

  @Test
  void rejectReturnsRejected() throws Exception {
    authenticate();
    when(service.decide(APPROVAL_ID, false)).thenReturn(approval("REJECTED"));

    mockMvc.perform(post("/approvals/" + APPROVAL_ID + "/reject")
            .header("Authorization", "Bearer test-token"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("REJECTED"));
  }

  @Test
  void pendingReturnsList() throws Exception {
    authenticate();
    when(service.pendingMine()).thenReturn(List.of(approval("PENDING")));

    mockMvc.perform(get("/approvals/pending")
            .header("Authorization", "Bearer test-token"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].status").value("PENDING"));
  }

  @Test
  void streamSendsPendingListAndCompletes() throws Exception {
    authenticate();
    when(service.pendingMine()).thenReturn(List.of(approval("PENDING")));

    MvcResult result = mockMvc.perform(get("/approvals/stream")
            .header("Authorization", "Bearer test-token"))
        .andExpect(request().asyncStarted())
        .andReturn();

    mockMvc.perform(asyncDispatch(result))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
        .andExpect(content().string(Matchers.containsString("pending")));
  }

  @Test
  void approveWithoutTokenIsForbidden() throws Exception {
    mockMvc.perform(post("/approvals/" + APPROVAL_ID + "/approve"))
        .andExpect(status().isForbidden());
  }
}
