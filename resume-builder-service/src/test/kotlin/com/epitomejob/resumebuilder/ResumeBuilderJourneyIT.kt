package com.epitomejob.resumebuilder

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.sun.net.httpserver.HttpServer
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.core.io.ByteArrayResource
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.util.LinkedMultiValueMap
import org.springframework.util.MultiValueMap
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.Date
import java.util.UUID
import org.assertj.core.api.Assertions.assertThat

/**
 * Journey IT (Testcontainers 1.21.4 + docker-java.properties api.version=1.44,
 * same pattern as the monolith): parse → create → get → update → copilot →
 * export (pdf + docx) → save-to-profile, against real Postgres + Flyway V1.
 * The agent-brain and the monolith backend are JDK HttpServer stubs (the
 * agent-brain has no /resume-builder/copilot endpoint on live main yet —
 * the stub returns the contract shape so the wiring is proven here; the
 * gap is listed in the README). Save-to-profile uploads the PDF rendering
 * (the monolith accepts PDF only), and the /resumes stub returns a PDF
 * resume id.
 * Auth uses a real JJWT token minted with the test-profile secret.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
class ResumeBuilderJourneyIT {

  companion object {
    val JWT_SECRET: String =
      "test-only-secret-change-me-0123456789abcdef0123456789abcdef"
    val USER_ID: UUID = UUID.randomUUID()
    val OTHER_USER_ID: UUID = UUID.randomUUID()
    val STUB_RESUME_ID: UUID = UUID.randomUUID()

    @Container
    @JvmField
    val POSTGRES: PostgreSQLContainer<*> = PostgreSQLContainer("pgvector/pgvector:pg16")
      .withDatabaseName("epitomejob")
      .withUsername("epitomejob")
      .withPassword("epitomejob-dev")

    private lateinit var stub: HttpServer
    private var stubPort: Int = 0

    @BeforeAll
    @JvmStatic
    fun startStub() {
      stub = HttpServer.create(InetSocketAddress(0), 0)
      stub.createContext("/resume-builder/copilot") { exchange ->
        val bytes = "{\"suggestions\":[\"Sharpen the summary\"],\"score\":9}"
          .toByteArray(StandardCharsets.UTF_8)
        exchange.responseHeaders.set("Content-Type", "application/json")
        exchange.sendResponseHeaders(200, bytes.size.toLong())
        exchange.responseBody.use { out -> out.write(bytes) }
      }
      stub.createContext("/resumes") { exchange ->
        val response = "{\"id\":\"" + STUB_RESUME_ID + "\",\"fileName\":\"ada-resume.pdf\","
          + "\"fileType\":\"pdf\",\"fileSize\":123,\"status\":\"READY\","
          + "\"createdAt\":\"2026-11-14T00:00:00Z\",\"label\":\"Android Developer\"}"
        val bytes = response.toByteArray(StandardCharsets.UTF_8)
        exchange.responseHeaders.set("Content-Type", "application/json")
        exchange.sendResponseHeaders(201, bytes.size.toLong())
        exchange.responseBody.use { out -> out.write(bytes) }
      }
      stub.start()
      stubPort = stub.address.port
    }

    @AfterAll
    @JvmStatic
    fun stopStub() {
      stub.stop(0)
    }

    @DynamicPropertySource
    @JvmStatic
    fun properties(registry: DynamicPropertyRegistry) {
      registry.add("spring.datasource.url", POSTGRES::getJdbcUrl)
      registry.add("spring.datasource.username", POSTGRES::getUsername)
      registry.add("spring.datasource.password", POSTGRES::getPassword)
      registry.add("spring.jpa.hibernate.ddl-auto") { "validate" }
      registry.add("spring.flyway.enabled") { "true" }
      registry.add("spring.flyway.schemas") { "resume_builder" }
      registry.add("app.agent-brain.url") { "http://localhost:$stubPort" }
      registry.add("app.java-base-url") { "http://localhost:$stubPort" }
      registry.add("app.jwt.secret") { JWT_SECRET }
    }
  }

  @LocalServerPort
  private var port: Int = 0

  @Autowired
  private lateinit var rest: TestRestTemplate

  @Autowired
  private lateinit var mapper: ObjectMapper

  private fun token(userId: UUID): String {
    return Jwts.builder()
      .subject(userId.toString())
      .issuedAt(Date())
      .expiration(Date.from(Instant.now().plusSeconds(900)))
      .signWith(Keys.hmacShaKeyFor(JWT_SECRET.toByteArray(StandardCharsets.UTF_8)))
      .compact()
  }

  private fun authHeaders(userId: UUID): HttpHeaders {
    val headers = HttpHeaders()
    headers.set("Authorization", "Bearer " + token(userId))
    return headers
  }

  private fun url(path: String): String {
    return "http://localhost:$port$path"
  }

  @Test
  fun fullJourney() {
    // 1. Templates: 3, including Modern.
    val templates: ResponseEntity<String> = rest.exchange(
      url("/resume-builder/templates"),
      HttpMethod.GET, HttpEntity<String>(authHeaders(USER_ID)), String::class.java
    )
    assertThat(templates.statusCode.value).isEqualTo(200)
    assertThat(templates.body).contains("modern", "classic", "minimal")

    // 2. Parse the PDF fixture.
    val pdf: ByteArray = TestFixtures.pdfFixture()
    val multipart: MultiValueMap<String, Any> = LinkedMultiValueMap()
    multipart.add(
      "file",
      object : ByteArrayResource(pdf) {
        override fun getFilename(): String = "resume.pdf"
      }
    )
    val multipartHeaders = authHeaders(USER_ID)
    multipartHeaders.contentType = MediaType.MULTIPART_FORM_DATA
    val parsed: ResponseEntity<String> = rest.exchange(
      url("/resume-builder/parse"),
      HttpMethod.POST, HttpEntity(multipart, multipartHeaders), String::class.java
    )
    assertThat(parsed.statusCode.value).isEqualTo(200)
    val parsedState: JsonNode = mapper.readTree(parsed.body).path("state")
    assertThat(parsedState.path("contact").path("fullName").asText()).isEqualTo("Ada Lovelace")
    assertThat(parsedState.path("rawText").asText()).contains("Designed the first algorithm")

    // 3. Create a document from the parsed state.
    val jsonHeaders = authHeaders(USER_ID)
    jsonHeaders.contentType = MediaType.APPLICATION_JSON
    val createBody = mapper.writeValueAsString(
      mapper.createObjectNode()
        .put("title", "Ada Resume")
        .put("templateId", "modern")
        .set("state", parsedState)
    )
    val created: ResponseEntity<String> = rest.exchange(
      url("/resume-builder/documents"),
      HttpMethod.POST, HttpEntity(createBody, jsonHeaders), String::class.java
    )
    assertThat(created.statusCode.value).isEqualTo(201)
    val documentId = UUID.fromString(mapper.readTree(created.body).path("id").asText())

    // 4. Get it back; another user gets 404 (ownership).
    val got: ResponseEntity<String> = rest.exchange(
      url("/resume-builder/documents/$documentId"),
      HttpMethod.GET, HttpEntity<String>(authHeaders(USER_ID)), String::class.java
    )
    assertThat(got.statusCode.value).isEqualTo(200)
    assertThat(mapper.readTree(got.body).path("title").asText()).isEqualTo("Ada Resume")
    val forbidden: ResponseEntity<String> = rest.exchange(
      url("/resume-builder/documents/$documentId"),
      HttpMethod.GET, HttpEntity<String>(authHeaders(OTHER_USER_ID)), String::class.java
    )
    assertThat(forbidden.statusCode.value).isEqualTo(404)

    // 5. Full state replace via PUT.
    val updatedState: ObjectNode = (parsedState as ObjectNode).deepCopy()
    updatedState.put("summary", "Updated summary.")
    val updateBody = mapper.writeValueAsString(
      mapper.createObjectNode()
        .put("title", "Ada Resume v2")
        .set("state", updatedState)
    )
    val updated: ResponseEntity<String> = rest.exchange(
      url("/resume-builder/documents/$documentId"),
      HttpMethod.PUT, HttpEntity(updateBody, jsonHeaders), String::class.java
    )
    assertThat(updated.statusCode.value).isEqualTo(200)
    assertThat(mapper.readTree(updated.body).path("state").path("summary").asText())
      .isEqualTo("Updated summary.")

    // 6. Co-Pilot proxy returns the stubbed JSON verbatim.
    val copilot: ResponseEntity<String> = rest.exchange(
      url("/resume-builder/documents/$documentId/copilot"),
      HttpMethod.POST, HttpEntity("{\"prompt\":\"improve\"}", jsonHeaders), String::class.java
    )
    assertThat(copilot.statusCode.value).isEqualTo(200)
    assertThat(copilot.body).contains("Sharpen the summary")

    // 7. Export PDF and DOCX.
    val pdfExport: ResponseEntity<ByteArray> = rest.exchange(
      url("/resume-builder/documents/$documentId/export?format=pdf"),
      HttpMethod.POST, HttpEntity<String>(authHeaders(USER_ID)), ByteArray::class.java
    )
    assertThat(pdfExport.statusCode.value).isEqualTo(200)
    assertThat(pdfExport.headers.contentType).isEqualTo(MediaType.APPLICATION_PDF)
    assertThat(String(pdfExport.body, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-")
    val docxExport: ResponseEntity<ByteArray> = rest.exchange(
      url("/resume-builder/documents/$documentId/export?format=docx"),
      HttpMethod.POST, HttpEntity<String>(authHeaders(USER_ID)), ByteArray::class.java
    )
    assertThat(docxExport.statusCode.value).isEqualTo(200)
    assertThat(String(docxExport.body, 0, 2, StandardCharsets.US_ASCII)).isEqualTo("PK")

    // 8. Save to profile returns the stubbed monolith resume id.
    val saved: ResponseEntity<String> = rest.exchange(
      url("/resume-builder/documents/$documentId/save-to-profile"),
      HttpMethod.POST, HttpEntity("{\"label\":\"Android Developer\"}", jsonHeaders),
      String::class.java
    )
    assertThat(saved.statusCode.value).isEqualTo(200)
    assertThat(mapper.readTree(saved.body).path("resumeId").asText())
      .isEqualTo(STUB_RESUME_ID.toString())

    // 9. No token at all is 401.
    val noAuth: ResponseEntity<String> = rest.exchange(
      url("/resume-builder/templates"),
      HttpMethod.GET, HttpEntity<String>(HttpHeaders()), String::class.java
    )
    assertThat(noAuth.statusCode.value).isEqualTo(401)
  }
}
