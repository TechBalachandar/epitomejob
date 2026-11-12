package com.epitomejob.interview.service;

import com.epitomejob.interview.dto.AnswerDto;
import com.epitomejob.interview.model.Answer;
import com.epitomejob.interview.model.InterviewSession;
import com.epitomejob.interview.model.Question;
import com.epitomejob.interview.repository.AnswerRepository;
import com.epitomejob.interview.repository.InterviewSessionRepository;
import com.epitomejob.interview.repository.QuestionRepository;
import com.epitomejob.job.model.Job;
import com.epitomejob.job.repository.JobRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InterviewServiceTest {

  private static final UUID USER_ID = UUID.randomUUID();
  private static final UUID JOB_ID = UUID.randomUUID();
  private static final UUID SESSION_ID = UUID.randomUUID();

  @Mock
  private QuestionRepository questions;
  @Mock
  private InterviewSessionRepository sessions;
  @Mock
  private AnswerRepository answers;
  @Mock
  private JobRepository jobs;

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  private void authenticate() {
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken(USER_ID.toString(), null, List.of()));
  }

  private InterviewService service() {
    return new InterviewService(questions, sessions, answers, jobs);
  }

  private Job job() {
    return new Job("Senior Software Engineer, Backend (Java)", "Northwind Health Systems",
        "Reston, VA", "Hybrid", 145000, 175000, true,
        List.of("Java 21", "Spring Boot", "Kafka"), "Backend APIs.", "Backend",
        Instant.parse("2026-11-10T09:00:00Z"));
  }

  private Question question(int n) {
    return new Question("Backend", "Technical", "Question " + n,
        List.of("java", "kafka", "spring"), n);
  }

  /** n words of filler, optionally ending with a period. */
  private String words(int n) {
    return "word ".repeat(Math.max(0, n)).trim();
  }

  @Test
  void structureScoresStarWordsLengthAndSentences() {
    InterviewService svc = service();
    // STAR words + 49 words + 2 sentences + in 40-250 range = 3+3+2+2 = 10.
    String answer = "Situation task action result. Second sentence here. " + words(42);
    assertEquals(10, svc.scoreStructure(answer));
    assertEquals(0, svc.scoreStructure("short"));
  }

  @Test
  void relevanceIsKeywordHitRate() {
    InterviewService svc = service();
    assertEquals(7, svc.scoreRelevance("I used java and kafka daily.",
        List.of("java", "kafka", "azure")));
    assertEquals(0, svc.scoreRelevance("nothing relevant here", List.of("java", "kafka")));
    assertEquals(0, svc.scoreRelevance("anything", List.of()));
  }

  @Test
  void specificityNeedsDigitSkillTokenAndLength() {
    InterviewService svc = service();
    String rich = "We cut latency by 40 percent for 10000 users using kafka. " + words(55);
    assertEquals(10, svc.scoreSpecificity(rich, List.of("Kafka")));
    assertEquals(0, svc.scoreSpecificity("short answer", List.of("Kafka")));
    assertEquals(4, svc.scoreSpecificity("we served 5 users", List.of("Kafka")));
  }

  @Test
  void concisenessBands() {
    InterviewService svc = service();
    assertEquals(4, svc.scoreConciseness(words(10)));
    assertEquals(7, svc.scoreConciseness(words(30)));
    assertEquals(10, svc.scoreConciseness(words(100)));
    assertEquals(7, svc.scoreConciseness(words(250)));
    assertEquals(4, svc.scoreConciseness(words(400)));
  }

  @Test
  void overallIsMeanScaledTo100() {
    assertEquals(100, service().overallScore(10, 10, 10, 10));
    assertEquals(75, service().overallScore(7, 8, 6, 9));
    assertEquals(0, service().overallScore(0, 0, 0, 0));
  }

  @Test
  void feedbackUsesVerbatimMissLinesThenPositive() {
    InterviewService svc = service();
    List<String> feedback = svc.buildFeedback("short",
        List.of("java", "kafka"), 0, 0, 0, 4);
    assertEquals("Add structure: walk through Situation → Task → Action → Result.", feedback.get(0));
    assertEquals("Tie your answer to the question: mention java, kafka.", feedback.get(1));
    assertEquals("Add a concrete number or outcome (e.g. users impacted, latency reduced).", feedback.get(2));
    assertEquals("Aim for 40–200 words — shorter and sharper.", feedback.get(3));
    assertEquals("Good start — add one concrete example and tighten the structure.", feedback.get(4));
  }

  @Test
  void feedbackPicksStrongestDimensionAsPositive() {
    InterviewService svc = service();
    List<String> feedback = svc.buildFeedback("x", List.of("java"), 10, 3, 2, 10);
    assertTrue(feedback.contains("Clear structure — easy to follow."));
    assertTrue(feedback.contains("Tie your answer to the question: mention java."));
  }

  @Test
  void submitAnswerRejectsEmptyAnswerAndForeignQuestion() {
    authenticate();
    InterviewSession session = new InterviewSession(USER_ID, JOB_ID);
    when(sessions.findById(SESSION_ID)).thenReturn(Optional.of(session));
    when(jobs.findById(JOB_ID)).thenReturn(Optional.of(job()));
    List<Question> roleQuestions = List.of(question(1), question(2));
    when(questions.findByRoleOrderBySortOrderAsc("Backend")).thenReturn(roleQuestions);

    ResponseStatusException empty = assertThrows(ResponseStatusException.class,
        () -> service().submitAnswer(SESSION_ID, roleQuestions.get(0).getId(), "  "));
    assertEquals(400, empty.getStatusCode().value());

    ResponseStatusException foreign = assertThrows(ResponseStatusException.class,
        () -> service().submitAnswer(SESSION_ID, UUID.randomUUID(), "an answer"));
    assertEquals(400, foreign.getStatusCode().value());
  }

  @Test
  void submitAnswerForOtherUsersSessionIs404() {
    authenticate();
    InterviewSession other = new InterviewSession(UUID.randomUUID(), JOB_ID);
    when(sessions.findById(SESSION_ID)).thenReturn(Optional.of(other));

    ResponseStatusException ex = assertThrows(ResponseStatusException.class,
        () -> service().submitAnswer(SESSION_ID, UUID.randomUUID(), "answer"));
    assertEquals(404, ex.getStatusCode().value());
  }

  @Test
  void answeringAllRoleQuestionsCompletesSession() {
    authenticate();
    InterviewSession session = new InterviewSession(USER_ID, JOB_ID);
    when(sessions.findById(SESSION_ID)).thenReturn(Optional.of(session));
    Job j = job();
    when(jobs.findById(JOB_ID)).thenReturn(Optional.of(j));
    List<Question> roleQuestions = List.of(question(1), question(2));
    when(questions.findByRoleOrderBySortOrderAsc("Backend")).thenReturn(roleQuestions);
    Question q2 = roleQuestions.get(1);
    when(answers.findBySessionIdAndQuestionId(SESSION_ID, q2.getId()))
        .thenReturn(Optional.empty());
    when(answers.save(any())).thenAnswer(inv -> inv.getArgument(0));
    Answer first = new Answer(SESSION_ID, roleQuestions.get(0).getId(), "first",
        5, 5, 5, 5, 50, List.of("Good start — add one concrete example and tighten the structure."));
    Answer second = new Answer(SESSION_ID, q2.getId(), "second",
        5, 5, 5, 5, 50, List.of("Good start — add one concrete example and tighten the structure."));
    when(answers.findBySessionId(SESSION_ID)).thenReturn(List.of(first, second));
    when(sessions.save(any())).thenAnswer(inv -> inv.getArgument(0));

    AnswerDto dto = service().submitAnswer(SESSION_ID, q2.getId(),
        "Situation task action result with java and kafka for 5 users. " + words(40));

    assertEquals(q2.getId(), dto.questionId());
    assertEquals("COMPLETED", session.getStatus());
    assertTrue(session.getCompletedAt() != null);
  }

  @Test
  void questionsFallBackToGeneralRole() {
    authenticate();
    Job j = job();
    when(jobs.findById(JOB_ID)).thenReturn(Optional.of(j));
    Question general = new Question("General", "Behavioral", "General question",
        List.of("situation", "action"), 1);
    when(questions.findByRoleOrderBySortOrderAsc("Backend")).thenReturn(List.of());
    when(questions.findByRoleOrderBySortOrderAsc("General")).thenReturn(List.of(general));

    List<Question> found = service().roleQuestions("Backend");
    assertEquals(1, found.size());
    assertEquals("General", found.get(0).getRole());
  }
}
