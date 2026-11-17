package com.epitomejob.integration

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.core.io.ByteArrayResource
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.util.LinkedMultiValueMap
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.io.ByteArrayOutputStream
import java.util.UUID

/**
 * The Phase 0 gate, end to end, against real Postgres + real RustFS.
 * This is the journey the Postman collection covers — here it runs in CI.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GateJourneyIT {

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

  private fun realPdfBytes(): ByteArray {
    PDDocument().use { doc ->
      ByteArrayOutputStream().use { out ->
        val page = PDPage()
        doc.addPage(page)
        PDPageContentStream(doc, page).use { content ->
          content.beginText()
          content.setFont(PDType1Font(Standard14Fonts.FontName.HELVETICA), 12f)
          content.newLineAtOffset(100f, 700f)
          content.showText("Balachandar Jeldi - Java Developer")
          content.endText()
        }
        doc.save(out)
        return out.toByteArray()
      }
    }
  }

  private fun bearer(token: String): HttpHeaders {
    val headers = HttpHeaders()
    headers.setBearerAuth(token)
    headers.setContentType(MediaType.APPLICATION_JSON)
    return headers
  }

  @Test
  fun fullGateJourney() {
    val email = "user-" + UUID.randomUUID() + "@example.com"

    // 1. Register + login
    val register = rest.postForEntity(
      "/auth/register",
      HttpEntity(
        mapOf("email" to email, "fullName" to "Gate User", "password" to "Passw0rd!"),
        jsonHeaders()
      ),
      Map::class.java
    )
    assertEquals(HttpStatus.CREATED, register.statusCode)
    var token = register.body?.get("accessToken") as String
    assertNotNull(token)

    val login = rest.postForEntity(
      "/auth/login",
      HttpEntity(mapOf("email" to email, "password" to "Passw0rd!"), jsonHeaders()),
      Map::class.java
    )
    assertEquals(HttpStatus.OK, login.statusCode)
    token = login.body?.get("accessToken") as String

    // 2. Profile
    val profile = rest.exchange(
      "/profiles/me", HttpMethod.PUT,
      HttpEntity(
        mapOf("headline" to "Java Developer", "location" to "Reston, VA", "workType" to "REMOTE"),
        bearer(token)
      ),
      Map::class.java
    )
    assertEquals(HttpStatus.OK, profile.statusCode)
    assertEquals(60, profile.body?.get("completeness"))

    // 3. Resume upload (real PDF, real RustFS)
    val file = object : ByteArrayResource(realPdfBytes()) {
      override fun getFilename(): String = "resume.pdf"
    }
    val parts = LinkedMultiValueMap<String, Any>()
    parts.add("file", file)
    val uploadHeaders = HttpHeaders()
    uploadHeaders.contentType = MediaType.MULTIPART_FORM_DATA
    uploadHeaders.setBearerAuth(token)
    val upload = rest.postForEntity("/resumes", HttpEntity(parts, uploadHeaders), Map::class.java)
    assertEquals(HttpStatus.CREATED, upload.statusCode)
    assertEquals("READY", upload.body?.get("status"))
    val resumeId = upload.body?.get("id") as String
    assertNotNull(resumeId)

    // 4. Approval: propose then approve
    val propose = rest.postForEntity(
      "/approvals",
      HttpEntity(
        mapOf(
          "action" to "CREATE_APPLICATION",
          "payload" to "{\"jobId\":\"job-1\",\"resumeId\":\"$resumeId\"}"
        ),
        bearer(token)
      ),
      Map::class.java
    )
    assertEquals(HttpStatus.CREATED, propose.statusCode)
    assertEquals("PENDING", propose.body?.get("status"))
    val approvalId = propose.body?.get("id") as String
    assertNotNull(approvalId)

    val approve = rest.postForEntity(
      "/approvals/$approvalId/approve",
      HttpEntity<Void>(bearer(token)), Map::class.java
    )
    assertEquals(HttpStatus.OK, approve.statusCode)
    assertEquals("APPROVED", approve.body?.get("status"))

    // 5. No pending approvals left
    val pending = rest.exchange(
      "/approvals/pending", HttpMethod.GET,
      HttpEntity<Void>(bearer(token)), List::class.java
    )
    assertEquals(HttpStatus.OK, pending.statusCode)
    assertTrue(pending.body?.isEmpty() ?: false)
  }

  @Test
  fun unauthenticatedProfileIsForbidden() {
    val response = rest.getForEntity("/profiles/me", Map::class.java)
    assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
  }

  @Test
  fun healthIsUp() {
    val response = rest.getForEntity("/actuator/health", Map::class.java)
    assertEquals(HttpStatus.OK, response.statusCode)
    assertEquals("UP", response.body?.get("status"))
  }

  private fun jsonHeaders(): HttpHeaders {
    val headers = HttpHeaders()
    headers.setContentType(MediaType.APPLICATION_JSON)
    return headers
  }
}
