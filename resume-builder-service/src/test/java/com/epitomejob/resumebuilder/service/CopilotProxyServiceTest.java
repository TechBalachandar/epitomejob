package com.epitomejob.resumebuilder.service;

import com.epitomejob.resumebuilder.common.exception.AgentUnavailableException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Proxy tests against a real JDK HttpServer stub — no RestClient mock
 * chaining, no Mockito at all. The stub returns 200, or 401 when the
 * request body contains "force-401", or 500 for "force-500".
 */
class CopilotProxyServiceTest {

  private static final UUID USER_ID = UUID.randomUUID();
  private static HttpServer stub;
  private static int stubPort;

  @BeforeAll
  static void startStub() throws IOException {
    stub = HttpServer.create(new InetSocketAddress(0), 0);
    stub.createContext("/resume-builder/copilot", exchange -> {
      String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
      int status = 200;
      String response = "{\"suggestions\":[\"Sharpen the summary\"],\"score\":9}";
      if (body.contains("force-401")) {
        status = 401;
        response = "{\"code\":\"UNAUTHORIZED\",\"message\":\"bad token\"}";
      } else if (body.contains("force-500")) {
        status = 500;
        response = "{\"code\":\"COPILOT_FAILED\",\"message\":\"boom\"}";
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
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken(USER_ID.toString(), null, List.of()));
  }

  @AfterAll
  static void stopStub() {
    stub.stop(0);
    SecurityContextHolder.clearContext();
  }

  private CopilotProxyService service(String baseUrl) {
    return new CopilotProxyService(RestClient.builder().baseUrl(baseUrl).build(),
        new ObjectMapper());
  }

  private ObjectNode requestWith(String prompt) {
    return new ObjectMapper().createObjectNode().put("prompt", prompt);
  }

  @Test
  void returnsAgentBrainJsonVerbatim() {
    ResponseEntity<String> response = service("http://localhost:" + stubPort)
        .copilot(UUID.randomUUID(), new ObjectMapper().createObjectNode(),
            requestWith("improve my summary"), "Bearer token");
    assertThat(response.getStatusCode().value()).isEqualTo(200);
    assertThat(response.getBody()).contains("Sharpen the summary");
  }

  @Test
  void forwardsAgentBrain401Verbatim() {
    ResponseEntity<String> response = service("http://localhost:" + stubPort)
        .copilot(UUID.randomUUID(), new ObjectMapper().createObjectNode(),
            requestWith("force-401"), "Bearer token");
    assertThat(response.getStatusCode().value()).isEqualTo(401);
    assertThat(response.getBody()).contains("bad token");
  }

  @Test
  void forwardsAgentBrain500Verbatim() {
    ResponseEntity<String> response = service("http://localhost:" + stubPort)
        .copilot(UUID.randomUUID(), new ObjectMapper().createObjectNode(),
            requestWith("force-500"), "Bearer token");
    assertThat(response.getStatusCode().value()).isEqualTo(500);
    assertThat(response.getBody()).contains("COPILOT_FAILED");
  }

  @Test
  void unreachableAgentBrainIs502() {
    assertThatThrownBy(() -> service("http://localhost:1")
        .copilot(UUID.randomUUID(), new ObjectMapper().createObjectNode(),
            requestWith("hello"), "Bearer token"))
        .isInstanceOf(AgentUnavailableException.class);
  }
}
