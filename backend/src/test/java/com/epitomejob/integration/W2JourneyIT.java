package com.epitomejob.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W2 journey against real Postgres + real RustFS: jobs list → matches →
 * tracker save → interview session → answers → session COMPLETED.
 * Flyway V3 seeds the 18 jobs and 30 questions.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class W2JourneyIT {

  @Container
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16")
      .withDatabaseName("epitomejob")
      .withUsername("epitomejob")
      .withPassword("epitomejob-dev");

  @Container
  static GenericContainer<?> rustfs = new GenericContainer<>("rustfs/rustfs:1.0.1")
      .withExposedPorts(9000, 9001)
      .withEnv("RUSTFS_ACCESS_KEY", "minioadmin")
      .withEnv("RUSTFS_SECRET_KEY", "minioadmin-dev")
      .withEnv("RUSTFS_ADDRESS", ":9000")
      .withEnv("RUSTFS_CONSOLE_ADDRESS", ":9001")
      .withEnv("RUSTFS_CONSOLE_ENABLE", "true")
      .waitingFor(Wait.forListeningPort());

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
    registry.add("app.minio.endpoint", () -> "http://localhost:" + rustfs.getMappedPort(9000));
    registry.add("app.minio.access-key", () -> "minioadmin");
    registry.add("app.minio.secret-key", () -> "minioadmin-dev");
    registry.add("app.minio.bucket", () -> "resumes");
    registry.add("app.jwt.secret", () -> "test-secret-that-is-at-least-32-chars-long!!");
  }

  @Autowired
  private TestRestTemplate rest;

  private HttpHeaders bearer(String token) {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(token);
    headers.setContentType(MediaType.APPLICATION_JSON);
    return headers;
  }

  private HttpHeaders jsonHeaders() {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    return headers;
  }

  /** A 50+ word answer with STAR words, a digit, and a job skill token. */
  private String answerText() {
    return "In my last role the situation was a slow checkout API and the task was to "
        + "fix it without downtime. My action was to profile the service, add caching "
        + "with Kafka events, and tune the database queries. The result was latency down "
        + "by 40 percent for 10000 users and the outcome was a faster checkout for everyone.";
  }

  @Test
  void w2Journey() {
    String email = "w2-" + UUID.randomUUID() + "@example.com";

    var register = rest.postForEntity("/auth/register",
        new HttpEntity<>(Map.of("email", email, "fullName", "W2 User", "password", "Passw0rd!"),
            jsonHeaders()),
        Map.class);
    assertEquals(HttpStatus.CREATED, register.getStatusCode());
    String token = (String) register.getBody().get("accessToken");
    assertNotNull(token);

    // Profile so matching has location / work type / salary to score on.
    var profile = rest.exchange("/profiles/me", HttpMethod.PUT,
        new HttpEntity<>(Map.of("headline", "Java Developer with Spring Boot and Kafka",
            "summary", "Backend engineer in Reston", "location", "Reston, VA",
            "workType", "Hybrid", "salaryMin", 140000, "salaryMax", 180000),
            bearer(token)),
        Map.class);
    assertEquals(HttpStatus.OK, profile.getStatusCode());

    // 1. Jobs: 18 seeded.
    var jobs = rest.exchange("/jobs", HttpMethod.GET, new HttpEntity<>(bearer(token)), Map.class);
    assertEquals(HttpStatus.OK, jobs.getStatusCode());
    assertEquals(18, jobs.getBody().get("totalElements"));
    List<Map<String, Object>> content = (List<Map<String, Object>>) jobs.getBody().get("content");
    assertEquals(18, content.size());
    String jobId = (String) content.get(0).get("id");
    assertNotNull(jobId);

    // Filtered search narrows the list.
    var filtered = rest.exchange("/jobs?query=Backend", HttpMethod.GET,
        new HttpEntity<>(bearer(token)), Map.class);
    assertEquals(HttpStatus.OK, filtered.getStatusCode());
    assertTrue((Integer) filtered.getBody().get("totalElements") >= 4);

    // 2. Matches: top 20, score desc, first match has reasons.
    var matches = rest.exchange("/matches/me", HttpMethod.GET,
        new HttpEntity<>(bearer(token)), List.class);
    assertEquals(HttpStatus.OK, matches.getStatusCode());
    assertEquals(18, matches.getBody().size());
    Map<String, Object> first = (Map<String, Object>) matches.getBody().get(0);
    assertTrue((Integer) first.get("score") > 0);
    assertEquals("PROFILE", first.get("source"));

    var oneMatch = rest.exchange("/matches/" + jobId, HttpMethod.GET,
        new HttpEntity<>(bearer(token)), Map.class);
    assertEquals(HttpStatus.OK, oneMatch.getStatusCode());
    assertEquals(jobId, ((Map<String, Object>) oneMatch.getBody().get("job")).get("id"));

    // 3. Tracker: save, then duplicate is 409.
    var saved = rest.postForEntity("/tracker",
        new HttpEntity<>(Map.of("jobId", jobId), bearer(token)), Map.class);
    assertEquals(HttpStatus.CREATED, saved.getStatusCode());
    assertEquals("SAVED", saved.getBody().get("status"));
    String trackerId = (String) saved.getBody().get("id");
    assertNotNull(trackerId);

    var duplicate = rest.postForEntity("/tracker",
        new HttpEntity<>(Map.of("jobId", jobId), bearer(token)), Map.class);
    assertEquals(HttpStatus.CONFLICT, duplicate.getStatusCode());

    var updated = rest.exchange("/tracker/" + trackerId, HttpMethod.PUT,
        new HttpEntity<>(Map.of("status", "APPLIED"), bearer(token)), Map.class);
    assertEquals(HttpStatus.OK, updated.getStatusCode());
    assertEquals("APPLIED", updated.getBody().get("status"));

    // 4. Interview: 5 questions for the job's role, then a session.
    var questions = rest.exchange("/interviews/questions?jobId=" + jobId, HttpMethod.GET,
        new HttpEntity<>(bearer(token)), List.class);
    assertEquals(HttpStatus.OK, questions.getStatusCode());
    assertEquals(5, questions.getBody().size());
    List<Map<String, Object>> questionList = (List<Map<String, Object>>) questions.getBody();

    var session = rest.postForEntity("/interviews/sessions",
        new HttpEntity<>(Map.of("jobId", jobId), bearer(token)), Map.class);
    assertEquals(HttpStatus.CREATED, session.getStatusCode());
    assertEquals("IN_PROGRESS", session.getBody().get("status"));
    String sessionId = (String) session.getBody().get("id");
    assertNotNull(sessionId);

    // 5. Answer every question; the session completes and averages scores.
    for (Map<String, Object> question : questionList) {
      var answer = rest.postForEntity("/interviews/sessions/" + sessionId + "/answers",
          new HttpEntity<>(Map.of("questionId", question.get("id"),
              "answerText", answerText()), bearer(token)),
          Map.class);
      assertEquals(HttpStatus.OK, answer.getStatusCode());
      assertTrue((Integer) answer.getBody().get("overallScore") > 0);
      assertNotNull(answer.getBody().get("feedback"));
    }

    var done = rest.exchange("/interviews/sessions/" + sessionId, HttpMethod.GET,
        new HttpEntity<>(bearer(token)), Map.class);
    assertEquals(HttpStatus.OK, done.getStatusCode());
    assertEquals("COMPLETED", done.getBody().get("status"));
    assertNotNull(done.getBody().get("completedAt"));
    assertNotNull(done.getBody().get("averageScore"));
    assertEquals(5, ((List<?>) done.getBody().get("answers")).size());

    // Re-answering a question replaces it (still 5 answers, still COMPLETED).
    var replaced = rest.postForEntity("/interviews/sessions/" + sessionId + "/answers",
        new HttpEntity<>(Map.of("questionId", questionList.get(0).get("id"),
            "answerText", answerText()), bearer(token)),
        Map.class);
    assertEquals(HttpStatus.OK, replaced.getStatusCode());
    var again = rest.exchange("/interviews/sessions/" + sessionId, HttpMethod.GET,
        new HttpEntity<>(bearer(token)), Map.class);
    assertEquals("COMPLETED", again.getBody().get("status"));
    assertEquals(5, ((List<?>) again.getBody().get("answers")).size());
  }

  @Test
  void questionsWithoutJobIdIs400() {
    String email = "w2-" + UUID.randomUUID() + "@example.com";
    var register = rest.postForEntity("/auth/register",
        new HttpEntity<>(Map.of("email", email, "fullName", "W2 User", "password", "Passw0rd!"),
            jsonHeaders()),
        Map.class);
    String token = (String) register.getBody().get("accessToken");

    var response = rest.exchange("/interviews/questions", HttpMethod.GET,
        new HttpEntity<>(bearer(token)), Map.class);
    assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
  }
}
