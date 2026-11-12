package com.epitomejob.coach.service;

import com.epitomejob.common.exception.AgentUnavailableException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AgentBrainClient against a real (tiny, in-process) HTTP server — no mocks,
 * so the verbatim status + body forwarding and the 502 mapping are proven
 * over real HTTP. The JDK HttpServer binds an ephemeral port per test.
 */
class AgentBrainClientTest {

  private static final UUID USER_ID = UUID.randomUUID();
  private static final String AGENT_BODY =
      "{\"reply\":\"hi\",\"citations\":[],\"actions\":[],\"specialistsUsed\":[\"matching\"]}";

  private HttpServer server;
  private AgentBrainClient client;

  @BeforeEach
  void setUp() throws Exception {
    server = HttpServer.create(new InetSocketAddress(0), 0);
    int port = server.getAddress().getPort();
    client = new AgentBrainClient(
        RestClient.builder().baseUrl("http://localhost:" + port).build());
    server.start();
  }

  @AfterEach
  void tearDown() {
    server.stop(0);
  }

  private void respond(int status, String body) {
    server.createContext("/chat", exchange -> {
      byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().set("Content-Type", "application/json");
      exchange.sendResponseHeaders(status, bytes.length);
      try (var out = exchange.getResponseBody()) {
        out.write(bytes);
      }
    });
  }

  @Test
  void chatForwardsMessageUserIdAndTokenAndReturnsBodyVerbatim() throws Exception {
    AtomicReference<String> seenAuth = new AtomicReference<>();
    AtomicReference<String> seenBody = new AtomicReference<>();
    AtomicReference<String> seenMethod = new AtomicReference<>();
    server.createContext("/chat", exchange -> {
      seenMethod.set(exchange.getRequestMethod());
      seenAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
      seenBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
      byte[] bytes = AGENT_BODY.getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().set("Content-Type", "application/json");
      exchange.sendResponseHeaders(200, bytes.length);
      try (var out = exchange.getResponseBody()) {
        out.write(bytes);
      }
    });

    var response = client.chat("find me a job", USER_ID, "Bearer test-token");

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertEquals(AGENT_BODY, response.getBody());
    assertEquals("POST", seenMethod.get());
    assertEquals("Bearer test-token", seenAuth.get());
    assertTrue(seenBody.get().contains("\"message\":\"find me a job\""),
        "outbound body must carry the message, was: " + seenBody.get());
    assertTrue(seenBody.get().contains("\"userId\":\"" + USER_ID + "\""),
        "outbound body must carry the authenticated user id, was: " + seenBody.get());
  }

  @Test
  void chatForwardsAgentBrain401WithBody() {
    respond(401, "{\"code\":\"UNAUTHORIZED\",\"message\":\"bad token\"}");

    var response = client.chat("hi", USER_ID, "Bearer test-token");

    assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    assertEquals("{\"code\":\"UNAUTHORIZED\",\"message\":\"bad token\"}", response.getBody());
  }

  @Test
  void chatForwardsAgentBrain500WithBody() {
    respond(500, "{\"code\":\"INTERNAL\",\"message\":\"boom\"}");

    var response = client.chat("hi", USER_ID, "Bearer test-token");

    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
    assertEquals("{\"code\":\"INTERNAL\",\"message\":\"boom\"}", response.getBody());
  }

  @Test
  void chatWhenAgentBrainUnreachableThrowsAgentUnavailable() {
    // Port 1 never listens: connection refused → 502 mapping (via the exception).
    var deadClient = new AgentBrainClient(
        RestClient.builder().baseUrl("http://localhost:1").build());

    assertThrows(AgentUnavailableException.class,
        () -> deadClient.chat("hi", USER_ID, "Bearer test-token"));
  }
}
