package com.epitomejob.interview.controller;

import com.epitomejob.common.security.SecurityConfig;
import com.epitomejob.identity.service.JwtService;
import com.epitomejob.interview.dto.AnswerDto;
import com.epitomejob.interview.dto.QuestionDto;
import com.epitomejob.interview.dto.SessionDto;
import com.epitomejob.interview.service.InterviewService;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InterviewController.class)
@Import(SecurityConfig.class)
class InterviewControllerTest {

  private static final UUID USER_ID = UUID.randomUUID();
  private static final UUID JOB_ID = UUID.randomUUID();
  private static final UUID SESSION_ID = UUID.randomUUID();
  private static final UUID QUESTION_ID = UUID.randomUUID();

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private InterviewService service;

  @MockitoBean
  private JwtService jwt;

  private void authenticate() {
    Claims claims = mock(Claims.class);
    when(claims.getSubject()).thenReturn(USER_ID.toString());
    when(claims.get("role", String.class)).thenReturn("ROLE_USER");
    when(jwt.parse("test-token")).thenReturn(claims);
  }

  private SessionDto sessionDto() {
    return new SessionDto(SESSION_ID, JOB_ID, "Senior Software Engineer, Backend (Java)",
        "IN_PROGRESS", Instant.now(), null, List.of(), null);
  }

  @Test
  void questionsReturnsRoleQuestions() throws Exception {
    authenticate();
    when(service.questionsForJob(JOB_ID)).thenReturn(List.of(
        new QuestionDto(QUESTION_ID, "Explain REST APIs.", "Technical", "Backend")));

    mockMvc.perform(get("/interviews/questions?jobId=" + JOB_ID)
            .header("Authorization", "Bearer <redacted>"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].category").value("Technical"));
  }

  @Test
  void questionsWithoutJobIdIs400() throws Exception {
    authenticate();

    mockMvc.perform(get("/interviews/questions")
            .header("Authorization", "Bearer <redacted>"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void createSessionReturns201() throws Exception {
    authenticate();
    when(service.createSession(JOB_ID)).thenReturn(sessionDto());

    mockMvc.perform(post("/interviews/sessions")
            .header("Authorization", "Bearer <redacted>")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"jobId\":\"" + JOB_ID + "\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
  }

  @Test
  void submitAnswerReturnsAnswer() throws Exception {
    authenticate();
    AnswerDto answer = new AnswerDto(UUID.randomUUID(), QUESTION_ID, "Explain REST APIs.",
        "My answer.", new AnswerDto.AnswerScores(7, 8, 6, 9), 75,
        List.of("Strongly relevant to the question."));
    when(service.submitAnswer(eq(SESSION_ID), eq(QUESTION_ID), any())).thenReturn(answer);

    mockMvc.perform(post("/interviews/sessions/" + SESSION_ID + "/answers")
            .header("Authorization", "Bearer <redacted>")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"questionId\":\"" + QUESTION_ID + "\",\"answerText\":\"My answer.\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.overallScore").value(75))
        .andExpect(jsonPath("$.scores.relevance").value(8));
  }

  @Test
  void submitEmptyAnswerIs400() throws Exception {
    authenticate();

    mockMvc.perform(post("/interviews/sessions/" + SESSION_ID + "/answers")
            .header("Authorization", "Bearer <redacted>")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"questionId\":\"" + QUESTION_ID + "\",\"answerText\":\"  \"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void getSessionReturnsSession() throws Exception {
    authenticate();
    when(service.getSession(SESSION_ID)).thenReturn(sessionDto());

    mockMvc.perform(get("/interviews/sessions/" + SESSION_ID)
            .header("Authorization", "Bearer <redacted>"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.jobTitle").value("Senior Software Engineer, Backend (Java)"));
  }

  @Test
  void getMissingSessionIs404() throws Exception {
    authenticate();
    when(service.getSession(SESSION_ID))
        .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Interview session not found"));

    mockMvc.perform(get("/interviews/sessions/" + SESSION_ID)
            .header("Authorization", "Bearer <redacted>"))
        .andExpect(status().isNotFound());
  }

  @Test
  void listMineReturnsSessions() throws Exception {
    authenticate();
    when(service.listMine()).thenReturn(List.of(sessionDto()));

    mockMvc.perform(get("/interviews/sessions/me")
            .header("Authorization", "Bearer <redacted>"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].status").value("IN_PROGRESS"));
  }
}
