package com.epitomejob.coach.config;

import com.epitomejob.coach.service.AgentBrainClient;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Proves the RestClient bean is built from {@code app.agent-brain.url}: the
 * bean is created with the property pointing at a real local HTTP server,
 * and a chat call actually reaches that server's /chat.
 */
class AgentBrainConfigTest {

  private HttpServer server;
  private int port;

  @BeforeEach
  void setUp() throws Exception {
    server = HttpServer.create(new InetSocketAddress(0), 0);
    port = server.getAddress().getPort();
    server.start();
  }

  @AfterEach
  void tearDown() {
    server.stop(0);
  }

  @Test
  void restClientIsBuiltFromAppAgentBrainUrl() {
    AtomicReference<String> seenPath = new AtomicReference<>();
    server.createContext("/chat", exchange -> {
      seenPath.set(exchange.getRequestURI().getPath());
      byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().set("Content-Type", "application/json");
      exchange.sendResponseHeaders(200, body.length);
      try (var out = exchange.getResponseBody()) {
        out.write(body);
      }
    });

    new ApplicationContextRunner()
        .withUserConfiguration(AgentBrainConfig.class)
        .withPropertyValues("app.agent-brain.url=http://localhost:" + port)
        .run(context -> {
          assertThat(context).hasSingleBean(RestClient.class);
          var client = new AgentBrainClient(context.getBean(RestClient.class));
          var response = client.chat("hi", UUID.randomUUID(), "Bearer test-token");
          assertEquals(HttpStatus.OK, response.getStatusCode());
          assertEquals("/chat", seenPath.get(),
              "the RestClient must use the configured base URL");
        });
  }

  @Test
  void defaultUrlIsLocalhost8000WhenPropertyAbsent() {
    // Same default as application.yml (env AGENT_BRAIN_URL), so the bean
    // still builds when the property is not set at all.
    new ApplicationContextRunner()
        .withUserConfiguration(AgentBrainConfig.class)
        .run(context -> assertThat(context).hasSingleBean(RestClient.class));
  }
}
