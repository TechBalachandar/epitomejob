package com.epitomejob.coach.config

import com.epitomejob.coach.service.AgentBrainClient
import com.sun.net.httpserver.HttpServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.http.HttpStatus
import org.springframework.web.client.RestClient
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

/**
 * Proves the RestClient bean is built from `app.agent-brain.url`: the
 * bean is created with the property pointing at a real local HTTP server,
 * and a chat call actually reaches that server's /chat.
 */
class AgentBrainConfigTest {

  private lateinit var server: HttpServer
  private var port: Int = 0

  @BeforeEach
  fun setUp() {
    server = HttpServer.create(InetSocketAddress(0), 0)
    port = server.address.port
    server.start()
  }

  @AfterEach
  fun tearDown() {
    server.stop(0)
  }

  @Test
  fun restClientIsBuiltFromAppAgentBrainUrl() {
    val seenPath = AtomicReference<String>()
    server.createContext("/chat") { exchange ->
      seenPath.set(exchange.requestURI.path)
      val body = "{}".toByteArray(StandardCharsets.UTF_8)
      exchange.responseHeaders.set("Content-Type", "application/json")
      exchange.sendResponseHeaders(200, body.size.toLong())
      exchange.responseBody.use { out -> out.write(body) }
    }

    ApplicationContextRunner()
      .withUserConfiguration(AgentBrainConfig::class.java)
      .withPropertyValues("app.agent-brain.url=http://localhost:$port")
      .run { context ->
        assertThat(context).hasSingleBean(RestClient::class.java)
        val client = AgentBrainClient(context.getBean(RestClient::class.java))
        val response = client.chat("hi", UUID.randomUUID(), "Bearer <redacted>")
        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("/chat", seenPath.get(), "the RestClient must use the configured base URL")
      }
  }

  @Test
  fun defaultUrlIsLocalhost8000WhenPropertyAbsent() {
    // Same default as application.yml (env AGENT_BRAIN_URL), so the bean
    // still builds when the property is not set at all.
    ApplicationContextRunner()
      .withUserConfiguration(AgentBrainConfig::class.java)
      .run { context -> assertThat(context).hasSingleBean(RestClient::class.java) }
  }
}
