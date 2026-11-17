package com.epitomejob.coach.service

import com.epitomejob.common.exception.AgentUnavailableException
import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.web.client.RestClient
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

/**
 * AgentBrainClient against a real (tiny, in-process) HTTP server — no mocks,
 * so the verbatim status + body forwarding and the 502 mapping are proven
 * over real HTTP. The JDK HttpServer binds an ephemeral port per test.
 */
class AgentBrainClientTest {

  companion object {
    private val USER_ID: UUID = UUID.randomUUID()
    private const val AGENT_BODY =
      "{\"reply\":\"hi\",\"citations\":[],\"actions\":[],\"specialistsUsed\":[\"matching\"]}"
  }

  private lateinit var server: HttpServer
  private lateinit var client: AgentBrainClient

  @BeforeEach
  fun setUp() {
    server = HttpServer.create(InetSocketAddress(0), 0)
    val port = server.address.port
    client = AgentBrainClient(
      RestClient.builder().baseUrl("http://localhost:$port").build()
    )
    server.start()
  }

  @AfterEach
  fun tearDown() {
    server.stop(0)
  }

  private fun respond(status: Int, body: String) {
    server.createContext("/chat") { exchange ->
      val bytes = body.toByteArray(StandardCharsets.UTF_8)
      exchange.responseHeaders.set("Content-Type", "application/json")
      exchange.sendResponseHeaders(status, bytes.size.toLong())
      exchange.responseBody.use { out -> out.write(bytes) }
    }
  }

  @Test
  fun chatForwardsMessageUserIdAndTokenAndReturnsBodyVerbatim() {
    val seenAuth = AtomicReference<String>()
    val seenBody = AtomicReference<String>()
    val seenMethod = AtomicReference<String>()
    server.createContext("/chat") { exchange ->
      seenMethod.set(exchange.requestMethod)
      seenAuth.set(exchange.requestHeaders.getFirst("Authorization"))
      seenBody.set(String(exchange.requestBody.readAllBytes(), StandardCharsets.UTF_8))
      val bytes = AGENT_BODY.toByteArray(StandardCharsets.UTF_8)
      exchange.responseHeaders.set("Content-Type", "application/json")
      exchange.sendResponseHeaders(200, bytes.size.toLong())
      exchange.responseBody.use { out -> out.write(bytes) }
    }

    val response = client.chat("find me a job", USER_ID, "Bearer <redacted>")

    assertEquals(HttpStatus.OK, response.statusCode)
    assertEquals(AGENT_BODY, response.body)
    assertEquals("POST", seenMethod.get())
    assertEquals("Bearer <redacted>", seenAuth.get())
    assertTrue(
      seenBody.get().contains("\"message\":\"find me a job\""),
      "outbound body must carry the message, was: " + seenBody.get()
    )
    assertTrue(
      seenBody.get().contains("\"userId\":\"$USER_ID\""),
      "outbound body must carry the authenticated user id, was: " + seenBody.get()
    )
  }

  @Test
  fun chatForwardsAgentBrain401WithBody() {
    respond(401, "{\"code\":\"UNAUTHORIZED\",\"message\":\"bad token\"}")

    val response = client.chat("hi", USER_ID, "Bearer <redacted>")

    assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    assertEquals("{\"code\":\"UNAUTHORIZED\",\"message\":\"bad token\"}", response.body)
  }

  @Test
  fun chatForwardsAgentBrain500WithBody() {
    respond(500, "{\"code\":\"INTERNAL\",\"message\":\"boom\"}")

    val response = client.chat("hi", USER_ID, "Bearer <redacted>")

    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode)
    assertEquals("{\"code\":\"INTERNAL\",\"message\":\"boom\"}", response.body)
  }

  @Test
  fun chatWhenAgentBrainUnreachableThrowsAgentUnavailable() {
    // Port 1 never listens: connection refused → 502 mapping (via the exception).
    val deadClient = AgentBrainClient(
      RestClient.builder().baseUrl("http://localhost:1").build()
    )

    assertThrows(AgentUnavailableException::class.java) {
      deadClient.chat("hi", USER_ID, "Bearer <redacted>")
    }
  }
}
