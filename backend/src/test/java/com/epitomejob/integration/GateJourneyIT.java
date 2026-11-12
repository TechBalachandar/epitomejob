package com.epitomejob.integration;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Phase 0 gate, end to end, against real Postgres + real RustFS.
 * This is the journey the Postman collection covers — here it runs in CI.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GateJourneyIT {

  @Container
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
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

  private byte[] realPdfBytes() throws Exception {
    try (PDDocument doc = new PDDocument();
         ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      PDPage page = new PDPage();
      doc.addPage(page);
      try (PDPageContentStream content = new PDPageContentStream(doc, page)) {
        content.beginText();
        content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
        content.newLineAtOffset(100, 700);
        content.showText("Balachandar Jeldi - Java Developer");
        content.endText();
      }
      doc.save(out);
      return out.toByteArray();
    }
  }

  private HttpHeaders bearer(String token) {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(token);
    headers.setContentType(MediaType.APPLICATION_JSON);
    return headers;
  }

  @Test
  void fullGateJourney() throws Exception {
    String email = "user-" + UUID.randomUUID() + "@example.com";

    // 1. Register + login
    var register = rest.postForEntity("/auth/register",
        new HttpEntity<>(Map.of("email", email, "fullName", "Gate User", "password", "Passw0rd!"),
            jsonHeaders()),
        Map.class);
    assertEquals(HttpStatus.CREATED, register.getStatusCode());
    String token = (String) register.getBody().get("accessToken");
    assertNotNull(token);

    var login = rest.postForEntity("/auth/login",
        new HttpEntity<>(Map.of("email", email, "password", "Passw0rd!"), jsonHeaders()),
        Map.class);
    assertEquals(HttpStatus.OK, login.getStatusCode());
    token = (String) login.getBody().get("accessToken");

    // 2. Profile
    var profile = rest.exchange("/profiles/me", HttpMethod.PUT,
        new HttpEntity<>(Map.of("headline", "Java Developer", "location", "Reston, VA",
            "workType", "REMOTE"), bearer(token)),
        Map.class);
    assertEquals(HttpStatus.OK, profile.getStatusCode());
    assertEquals(60, profile.getBody().get("completeness"));

    // 3. Resume upload (real PDF, real RustFS)
    var file = new ByteArrayResource(realPdfBytes()) {
      @Override
      public String getFilename() {
        return "resume.pdf";
      }
    };
    var parts = new LinkedMultiValueMap<String, Object>();
    parts.add("file", file);
    HttpHeaders uploadHeaders = new HttpHeaders();
    uploadHeaders.setContentType(MediaType.MULTIPART_FORM_DATA);
    uploadHeaders.setBearerAuth(token);
    var upload = rest.postForEntity("/resumes", new HttpEntity<>(parts, uploadHeaders), Map.class);
    assertEquals(HttpStatus.CREATED, upload.getStatusCode());
    assertEquals("READY", upload.getBody().get("status"));
    String resumeId = (String) upload.getBody().get("id");
    assertNotNull(resumeId);

    // 4. Approval: propose then approve
    var propose = rest.postForEntity("/approvals",
        new HttpEntity<>(Map.of("action", "CREATE_APPLICATION",
            "payload", "{\"jobId\":\"job-1\",\"resumeId\":\"" + resumeId + "\"}"),
            bearer(token)),
        Map.class);
    assertEquals(HttpStatus.CREATED, propose.getStatusCode());
    assertEquals("PENDING", propose.getBody().get("status"));
    String approvalId = (String) propose.getBody().get("id");
    assertNotNull(approvalId);

    var approve = rest.postForEntity("/approvals/" + approvalId + "/approve",
        new HttpEntity<>(bearer(token)), Map.class);
    assertEquals(HttpStatus.OK, approve.getStatusCode());
    assertEquals("APPROVED", approve.getBody().get("status"));

    // 5. No pending approvals left
    var pending = rest.exchange("/approvals/pending", HttpMethod.GET,
        new HttpEntity<>(bearer(token)), List.class);
    assertEquals(HttpStatus.OK, pending.getStatusCode());
    assertTrue(pending.getBody().isEmpty());
  }

  @Test
  void unauthenticatedProfileIsForbidden() {
    var response = rest.getForEntity("/profiles/me", Map.class);
    assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
  }

  @Test
  void healthIsUp() {
    var response = rest.getForEntity("/actuator/health", Map.class);
    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertEquals("UP", response.getBody().get("status"));
  }

  private HttpHeaders jsonHeaders() {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    return headers;
  }
}
