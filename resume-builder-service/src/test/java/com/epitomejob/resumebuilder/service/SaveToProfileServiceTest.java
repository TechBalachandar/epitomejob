package com.epitomejob.resumebuilder.service;

import com.epitomejob.resumebuilder.common.exception.AgentUnavailableException;
import com.epitomejob.resumebuilder.dto.ResumeBuilderDtos.SaveToProfileResponse;
import com.epitomejob.resumebuilder.model.ResumeDocument;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Save-to-profile tests against a real JDK HttpServer stub for POST
 * /resumes. The stub returns 201 with a fixed resume id, or 400 when the
 * multipart body contains the label "bad-label" (simulating a backend
 * validation rejection). The upload is the PDF rendering — the monolith
 * accepts PDF only — so the stub's canned response describes a PDF file.
 */
class SaveToProfileServiceTest {

  private static final UUID RESUME_ID = UUID.randomUUID();
  private static HttpServer stub;
  private static int stubPort;

  @BeforeAll
  static void startStub() throws IOException {
    stub = HttpServer.create(new InetSocketAddress(0), 0);
    stub.createContext("/resumes", exchange -> {
      String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
      int status = 201;
      String response = "{\"id\":\"" + RESUME_ID + "\",\"fileName\":\"ada-resume.pdf\","
          + "\"fileType\":\"pdf\",\"fileSize\":123,\"status\":\"READY\","
          + "\"createdAt\":\"2026-11-14T00:00:00Z\",\"label\":\"Android Developer\"}";
      if (body.contains("bad-label")) {
        status = 400;
        response = "{\"code\":\"BAD_REQUEST\",\"message\":\"Phase 0 accepts PDF only (DOCX comes in Phase 3)\"}";
      }
      byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().set("Content-Type", "application/json");
      exchange.sendResponseHeaders(status, bytes.length);
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

  private SaveToProfileService service(String baseUrl) {
    ObjectMapper mapper = new ObjectMapper();
    return new SaveToProfileService(RestClient.builder().baseUrl(baseUrl).build(),
        new ExportService(new TemplateService(mapper)), mapper);
  }

  private ResumeDocument doc() {
    return new ResumeDocument(UUID.randomUUID(), "Ada Resume", "modern",
        "{\"contact\":{\"fullName\":\"Ada Lovelace\"},\"summary\":\"\",\"experience\":[],"
            + "\"education\":[],\"skills\":[],\"rawText\":\"\"}");
  }

  private com.fasterxml.jackson.databind.JsonNode state() throws Exception {
    return new ObjectMapper().readTree(doc().getState());
  }

  @Test
  void returnsTheMonolithResumeId() throws Exception {
    SaveToProfileResponse response = service("http://localhost:" + stubPort)
        .saveToProfile(doc(), state(), "Android Developer", "Bearer token");
    assertThat(response.resumeId()).isEqualTo(RESUME_ID);
    assertThat(response.label()).isEqualTo("Android Developer");
  }

  @Test
  void backend400PassesThroughAsResponseStatus() throws Exception {
    assertThatThrownBy(() -> service("http://localhost:" + stubPort)
        .saveToProfile(doc(), state(), "bad-label", "Bearer token"))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value())
            .isEqualTo(400));
  }

  @Test
  void blankLabelIs400BeforeAnyCall() throws Exception {
    assertThatThrownBy(() -> service("http://localhost:" + stubPort)
        .saveToProfile(doc(), state(), "  ", "Bearer token"))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value())
            .isEqualTo(400));
  }

  @Test
  void unreachableBackendIs502() throws Exception {
    assertThatThrownBy(() -> service("http://localhost:1")
        .saveToProfile(doc(), state(), "Android Developer", "Bearer token"))
        .isInstanceOf(AgentUnavailableException.class);
  }
}
