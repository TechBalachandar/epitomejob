package com.epitomejob.integration

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.UUID

/**
 * W2 journey against real Postgres + real RustFS: jobs list → matches →
 * tracker save → interview session → answers → session COMPLETED.
 * Flyway V3 seeds the 18 jobs and 30 questions.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class W2JourneyIT {

  companion object {
    @Container
    @JvmStatic
    val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("pgvector/pgvector:pg16")
      .withDatabaseName("epitomejob")
      .withUsername("epitomejob")
      .withPassword("epitomejob-dev")

    @Container
    @JvmStatic
    val rustfs: GenericContainer<*> = GenericContainer("rustfs/rustfs:1.0.1")
      .withExposedPorts(9000, 9001)
      .withEnv("RUSTFS_ACCESS_KEY", "minioadmin")
      .withEnv("RUSTFS_SECRET_KEY", "minioadmin-dev")
      .withEnv("RUSTFS_ADDRESS", ":9000")
      .withEnv("RUSTFS_CONSOLE_ADDRESS", ":9001")
      .withEnv("RUSTFS_CONSOLE_ENABLE", "true")
      .waitingFor(Wait.forListeningPort())

    @DynamicPropertySource
    @JvmStatic
    fun properties(registry: DynamicPropertyRegistry) {
      registry.add("spring.datasource.url", postgres::getJdbcUrl)
      registry.add("spring.datasource.username", postgres::getUsername)
      registry.add("spring.datasource.password", postgres::getPassword)
      registry.add("app.minio.endpoint") { "http://localhost:${rustfs.getMappedPort(9000)}" }
      registry.add("app.minio.access-key") { "minioadmin" }
      registry.add("app.minio.secret-key") { "minioadmin-dev" }
      registry.add("app.minio.bucket") { "resumes" }
      registry.add("app.jwt.secret") { "test-secret-that-is-at-least-32-chars-long!!" }
    }
  }

  @Autowired
  private lateinit var rest: TestRestTemplate

  private fun bearer(token: String): HttpHeaders {
    val headers = HttpHeaders()
    headers.setBearerAuth(token)
    headers.setContentType(MediaType.APPLICATION_JSON)
    return headers
  }

  private fun jsonHeaders(): HttpHeaders {
    val headers = HttpHeaders()
    headers.setContentType(MediaType.APPLICATION_JSON)
    return headers
  }

  /** A 50+ word answer with STAR words, a digit, and a job skill token. */
  private fun answerText(): String {
    return "In my last role the situation was a slow checkout API and the task was to "
      + "fix it without downtime. My action was to profile the service, add caching "
      + "with Kafka events, and tune the database queries. The result was latency down "
      + "by 40 percent for 10000 users and the outcome was a faster checkout for everyone."
  }

  @Test
  @Suppress("UNCHECKED_CAST")
  fun w2Journey() {
    val email = "w2-" + UUID.randomUUID() + "@example.com"

    val register = rest.postForEntity(
      "/auth/register",
      HttpEntity(
        mapOf("email" to email, "fullName" to "W2 User", "password" to "Passw0rd!"),
        jsonHeaders()
      ),
      Map::class.java
    )
    assertEquals(HttpStatus.CREATED, register.statusCode)
    val token = register.body?.get("accessToken") as String
    assertNotNull(token)

    // Profile so matching has location / work type / salary to score on.
    val profile = rest.exchange(
      "/profiles/me", HttpMethod.PUT,
      HttpEntity(
        mapOf(
          "headline" to "Java Developer with Spring Boot and Kafka",
          "summary" to "Backend engineer in Reston",
          "location" to "Reston, VA",
          "workType" to "Hybrid",
          "salaryMin" to 140000,
          "salaryMax" to 180000
        ),
        bearer(token)
      ),
      Map::class.java
    )
    assertEquals(HttpStatus.OK, profile.statusCode)

    // 1. Jobs: 18 seeded.
    val jobs = rest.exchange("/jobs", HttpMethod.GET, HttpEntity(bearer(token)), Map::class.java)
    assertEquals(HttpStatus.OK, jobs.statusCode)
    assertEquals(18, jobs.body?.get("totalElements"))
    val content = jobs.body?.get("content") as List<Map<String, Any?>>
    assertEquals(18, content.size)
    val jobId = content[0]["id"] as String
    assertNotNull(jobId)

    // Filtered search narrows the list.
    val filtered = rest.exchange(
      "/jobs?query=Backend", HttpMethod.GET, HttpEntity(bearer(token)), Map::class.java
    )
    assertEquals(HttpStatus.OK, filtered.statusCode)
    assertTrue((filtered.body?.get("totalElements") as Int) >= 4)

    // 2. Matches: top 20, score desc, first match has reasons.
    val matches = rest.exchange(
      "/matches/me", HttpMethod.GET, HttpEntity(bearer(token)), List::class.java
    )
    assertEquals(HttpStatus.OK, matches.statusCode)
    assertEquals(18, matches.body?.size)
    val first = matches.body?.get(0) as Map<String, Any?>
    assertTrue((first["score"] as Int) > 0)
    assertEquals("PROFILE", first["source"])

    val oneMatch = rest.exchange(
      "/matches/$jobId", HttpMethod.GET, HttpEntity(bearer(token)), Map::class.java
    )
    assertEquals(HttpStatus.OK, oneMatch.statusCode)
    assertEquals(jobId, (oneMatch.body?.get("job") as Map<String, Any?>)["id"])

    // 3. Tracker: save, then duplicate is 409.
    val saved = rest.postForEntity(
      "/tracker",
      HttpEntity(mapOf("jobId" to jobId), bearer(token)), Map::class.java
    )
    assertEquals(HttpStatus.CREATED, saved.statusCode)
    assertEquals("SAVED", saved.body?.get("status"))
    val trackerId = saved.body?.get("id") as String
    assertNotNull(trackerId)

    val duplicate = rest.postForEntity(
      "/tracker",
      HttpEntity(mapOf("jobId" to jobId), bearer(token)), Map::class.java
    )
    assertEquals(HttpStatus.CONFLICT, duplicate.statusCode)

    val updated = rest.exchange(
      "/tracker/$trackerId", HttpMethod.PUT,
      HttpEntity(mapOf("status" to "APPLIED"), bearer(token)), Map::class.java
    )
    assertEquals(HttpStatus.OK, updated.statusCode)
    assertEquals("APPLIED", updated.body?.get("status"))

    // 4. Interview: 5 questions for the job's role, then a session.
    val questions = rest.exchange(
      "/interviews/questions?jobId=$jobId", HttpMethod.GET,
      HttpEntity(bearer(token)), List::class.java
    )
    assertEquals(HttpStatus.OK, questions.statusCode)
    assertEquals(5, questions.body?.size)
    val questionList = questions.body as List<Map<String, Any?>>

    val session = rest.postForEntity(
      "/interviews/sessions",
      HttpEntity(mapOf("jobId" to jobId), bearer(token)), Map::class.java
    )
    assertEquals(HttpStatus.CREATED, session.statusCode)
    assertEquals("IN_PROGRESS", session.body?.get("status"))
    val sessionId = session.body?.get("id") as String
    assertNotNull(sessionId)

    // 5. Answer every question; the session completes and averages scores.
    for (question in questionList) {
      val answer = rest.postForEntity(
        "/interviews/sessions/$sessionId/answers",
        HttpEntity(
          mapOf("questionId" to question["id"], "answerText" to answerText()),
          bearer(token)
        ),
        Map::class.java
      )
      assertEquals(HttpStatus.OK, answer.statusCode)
      assertTrue((answer.body?.get("overallScore") as Int) > 0)
      assertNotNull(answer.body?.get("feedback"))
    }

    val done = rest.exchange(
      "/interviews/sessions/$sessionId", HttpMethod.GET,
      HttpEntity(bearer(token)), Map::class.java
    )
    assertEquals(HttpStatus.OK, done.statusCode)
    assertEquals("COMPLETED", done.body?.get("status"))
    assertNotNull(done.body?.get("completedAt"))
    assertNotNull(done.body?.get("averageScore"))
    assertEquals(5, (done.body?.get("answers") as List<*>).size)

    // Re-answering a question replaces it (still 5 answers, still COMPLETED).
    val replaced = rest.postForEntity(
      "/interviews/sessions/$sessionId/answers",
      HttpEntity(
        mapOf("questionId" to questionList[0]["id"], "answerText" to answerText()),
        bearer(token)
      ),
      Map::class.java
    )
    assertEquals(HttpStatus.OK, replaced.statusCode)
    val again = rest.exchange(
      "/interviews/sessions/$sessionId", HttpMethod.GET,
      HttpEntity(bearer(token)), Map::class.java
    )
    assertEquals("COMPLETED", again.body?.get("status"))
    assertEquals(5, (again.body?.get("answers") as List<*>).size)
  }

  @Test
  fun questionsWithoutJobIdIs400() {
    val email = "w2-" + UUID.randomUUID() + "@example.com"
    val register = rest.postForEntity(
      "/auth/register",
      HttpEntity(
        mapOf("email" to email, "fullName" to "W2 User", "password" to "Passw0rd!"),
        jsonHeaders()
      ),
      Map::class.java
    )
    val token = register.body?.get("accessToken") as String

    val response = rest.exchange(
      "/interviews/questions", HttpMethod.GET,
      HttpEntity(bearer(token)), Map::class.java
    )
    assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
  }
}
