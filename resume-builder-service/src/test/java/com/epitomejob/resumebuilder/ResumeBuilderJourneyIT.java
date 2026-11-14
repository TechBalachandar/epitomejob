package com.epitomejob.resumebuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

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

  static final String JWT_SECRET =
      "test-only-secret-change-me-0123456789abcdef0123456789abcdef";
  static final UUID USER_ID = UUID.randomUUID();
  static final UUID OTHER_USER_ID = UUID.randomUUID();
  static final UUID STUB_RESUME_ID = UUID.randomUUID();

  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("pgvector/pgvector:pg16")
          .withDatabaseName("epitomejob")
          .withUsername("epitomejob")
          .withPassword("epitomejob-dev");

  private static HttpServer stub;
  private static int stubPort;

  @LocalServerPort
  private int port;

  @Autowired
  private TestRestTemplate rest;
  @Autowired
  private ObjectMapper mapper;

  @BeforeAll
  static void startStub() throws IOException {
    stub = HttpServer.create(new InetSocketAddress(0), 0);
    stub.createContext("/resume-builder/copilot", exchange -> {
      byte[] bytes = "{\"suggestions\":[\"Sharpen the summary\"],\"score\":9}"
          .getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().set("Content-Type", "application/json");
      exchange.sendResponseHeaders(200, bytes.length);
      try (OutputStream out = exchange.getResponseBody()) {
        out.write(bytes);
      }
    });
    stub.createContext("/resumes", exchange -> {
      String response = "{\"id\":\"" + STUB_RESUME_ID + "\",\"fileName\":\"ada-resume.pdf\","
          + "\"fileType\":\"pdf\",\"fileSize\":123,\"status\":\"READY\","
          + "\"createdAt\":\"2026-11-14T00:00:00Z\",\"label\":\"Android Developer\"}";
      byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().set("Content-Type", "application/json");
      exchange.sendResponseHeaders(201, bytes.length);
      try (OutputStream out = exchange.getResponseBody()) {
        out.write(bytes);
      }
    });
    stub.start();
    stubPort = stub.getAddress().getPort();
  }

  @AfterAll
  static void stopStub() {
    stub.stop(0);
  }

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    registry.add("spring.flyway.enabled", () -> "true");
    registry.add("spring.flyway.schemas", () -> "resume_builder");
    registry.add("app.agent-brain.url", () -> "http://localhost:" + stubPort);
    registry.add("app.java-base-url", () -> "http://localhost:" + stubPort);
    registry.add("app.jwt.secret", () -> JWT_SECRET);
  }

  private String token(UUID userId) {
    return Jwts.builder()
        .subject(userId.toString())
        .issuedAt(new Date())
        .expiration(Date.from(Instant.now().plusSeconds(900)))
        .signWith(Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8)))
        .compact();
  }

  private HttpHeaders authHeaders(UUID userId) {
    HttpHeaders headers = new HttpHeaders();
    headers.set("Authorization", "Bearer " + token(userId));
    return headers;
  }

  private String url(String path) {
    return "http://localhost:" + port + path;
  }

  @Test
  void fullJourney() throws Exception {
    // 1. Templates: 3, including Modern.
    ResponseEntity<String> templates = rest.exchange(url("/resume-builder/templates"),
        HttpMethod.GET, new HttpEntity<>(authHeaders(USER_ID)), String.class);
    assertThat(templates.getStatusCode().value()).isEqualTo(200);
    assertThat(templates.getBody()).contains("modern", "classic", "minimal");

    // 2. Parse the PDF fixture.
    byte[] pdf = com.epitomejob.resumebuilder.service.ResumeParseServiceTest.pdfFixture();
    MultiValueMap<String, Object> multipart = new LinkedMultiValueMap<>();
    multipart.add("file", new ByteArrayResource(pdf) {
      @Override
      public String getFilename() {
        return "resume.pdf";
      }
    });
    HttpHeaders multipartHeaders = authHeaders(USER_ID);
    multipartHeaders.setContentType(MediaType.MULTIPART_FORM_DATA);
    ResponseEntity<String> parsed = rest.exchange(url("/resume-builder/parse"),
        HttpMethod.POST, new HttpEntity<>(multipart, multipartHeaders), String.class);
    assertThat(parsed.getStatusCode().value()).isEqualTo(200);
    JsonNode parsedState = mapper.readTree(parsed.getBody()).path("state");
    assertThat(parsedState.path("contact").path("fullName").asText()).isEqualTo("Ada Lovelace");
    assertThat(parsedState.path("rawText").asText()).contains("Designed the first algorithm");

    // 3. Create a document from the parsed state.
    HttpHeaders jsonHeaders = authHeaders(USER_ID);
    jsonHeaders.setContentType(MediaType.APPLICATION_JSON);
    String createBody = mapper.writeValueAsString(mapper.createObjectNode()
        .put("title", "Ada Resume")
        .put("templateId", "modern")
        .set("state", parsedState));
    ResponseEntity<String> created = rest.exchange(url("/resume-builder/documents"),
        HttpMethod.POST, new HttpEntity<>(createBody, jsonHeaders), String.class);
    assertThat(created.getStatusCode().value()).isEqualTo(201);
    UUID documentId = UUID.fromString(mapper.readTree(created.getBody()).path("id").asText());

    // 4. Get it back; another user gets 404 (ownership).
    ResponseEntity<String> got = rest.exchange(
        url("/resume-builder/documents/" + documentId),
        HttpMethod.GET, new HttpEntity<>(authHeaders(USER_ID)), String.class);
    assertThat(got.getStatusCode().value()).isEqualTo(200);
    assertThat(mapper.readTree(got.getBody()).path("title").asText()).isEqualTo("Ada Resume");
    ResponseEntity<String> forbidden = rest.exchange(
        url("/resume-builder/documents/" + documentId),
        HttpMethod.GET, new HttpEntity<>(authHeaders(OTHER_USER_ID)), String.class);
    assertThat(forbidden.getStatusCode().value()).isEqualTo(404);

    // 5. Full state replace via PUT.
    ObjectNode updatedState = ((ObjectNode) parsedState).deepCopy();
    updatedState.put("summary", "Updated summary.");
    String updateBody = mapper.writeValueAsString(mapper.createObjectNode()
        .put("title", "Ada Resume v2")
        .set("state", updatedState));
    ResponseEntity<String> updated = rest.exchange(
        url("/resume-builder/documents/" + documentId),
        HttpMethod.PUT, new HttpEntity<>(updateBody, jsonHeaders), String.class);
    assertThat(updated.getStatusCode().value()).isEqualTo(200);
    assertThat(mapper.readTree(updated.getBody()).path("state").path("summary").asText())
        .isEqualTo("Updated summary.");

    // 6. Co-Pilot proxy returns the stubbed JSON verbatim.
    ResponseEntity<String> copilot = rest.exchange(
        url("/resume-builder/documents/" + documentId + "/copilot"),
        HttpMethod.POST, new HttpEntity<>("{\"prompt\":\"improve\"}", jsonHeaders), String.class);
    assertThat(copilot.getStatusCode().value()).isEqualTo(200);
    assertThat(copilot.getBody()).contains("Sharpen the summary");

    // 7. Export PDF and DOCX.
    ResponseEntity<byte[]> pdfExport = rest.exchange(
        url("/resume-builder/documents/" + documentId + "/export?format=pdf"),
        HttpMethod.POST, new HttpEntity<>(authHeaders(USER_ID)), byte[].class);
    assertThat(pdfExport.getStatusCode().value()).isEqualTo(200);
    assertThat(pdfExport.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
    assertThat(new String(pdfExport.getBody(), 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
    ResponseEntity<byte[]> docxExport = rest.exchange(
        url("/resume-builder/documents/" + documentId + "/export?format=docx"),
        HttpMethod.POST, new HttpEntity<>(authHeaders(USER_ID)), byte[].class);
    assertThat(docxExport.getStatusCode().value()).isEqualTo(200);
    assertThat(new String(docxExport.getBody(), 0, 2, StandardCharsets.US_ASCII)).isEqualTo("PK");

    // 8. Save to profile returns the stubbed monolith resume id.
    ResponseEntity<String> saved = rest.exchange(
        url("/resume-builder/documents/" + documentId + "/save-to-profile"),
        HttpMethod.POST, new HttpEntity<>("{\"label\":\"Android Developer\"}", jsonHeaders),
        String.class);
    assertThat(saved.getStatusCode().value()).isEqualTo(200);
    assertThat(mapper.readTree(saved.getBody()).path("resumeId").asText())
        .isEqualTo(STUB_RESUME_ID.toString());

    // 9. No token at all is 401.
    ResponseEntity<String> noAuth = rest.exchange(url("/resume-builder/templates"),
        HttpMethod.GET, new HttpEntity<>(new HttpHeaders()), String.class);
    assertThat(noAuth.getStatusCode().value()).isEqualTo(401);
  }
}
