package com.epitomejob.resumebuilder.service

import com.epitomejob.resumebuilder.common.exception.AgentUnavailableException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.springframework.http.ResponseEntity
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.client.RestClient
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.UUID
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy

/**
 * Proxy tests against a real JDK HttpServer stub — no RestClient mock
 * chaining, no Mockito at all. The stub returns 200, or 401 when the
 * request body contains "force-401", or 500 for "force-500".
 */
class CopilotProxyServiceTest {

  companion object {
    private val USER_ID: UUID = UUID.randomUUID()
    private lateinit var stub: HttpServer
    private var stubPort: Int = 0

    @BeforeAll
    @JvmStatic
    fun startStub() {
      stub = HttpServer.create(InetSocketAddress(0), 0)
      stub.createContext("/resume-builder/copilot") { exchange ->
        val body = String(exchange.requestBody.readAllBytes(), StandardCharsets.UTF_8)
        var status = 200
        var response = "{\"suggestions\":[\"Sharpen the summary\"],\"score\":9}"
        if (body.contains("force-401")) {
          status = 401
          response = "{\"code\":\"UNAUTHORIZED\",\"message\":\"bad token\"}"
        } else if (body.contains("force-500")) {
          status = 500
          response = "{\"code\":\"COPILOT_FAILED\",\"message\":\"boom\"}"
        }
        val bytes = response.toByteArray(StandardCharsets.UTF_8)
        exchange.responseHeaders.set("Content-Type", "application/json")
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.use { out -> out.write(bytes) }
      }
      stub.start()
      stubPort = stub.address.port
      SecurityContextHolder.getContext().authentication =
        UsernamePasswordAuthenticationToken(USER_ID.toString(), null, emptyList<GrantedAuthority>())
    }

    @AfterAll
    @JvmStatic
    fun stopStub() {
      stub.stop(0)
      SecurityContextHolder.clearContext()
    }
  }

  private fun service(baseUrl: String): CopilotProxyService {
    return CopilotProxyService(RestClient.builder().baseUrl(baseUrl).build(), ObjectMapper())
  }

  private fun requestWith(prompt: String): ObjectNode {
    return ObjectMapper().createObjectNode().put("prompt", prompt)
  }

  @Test
  fun returnsAgentBrainJsonVerbatim() {
    val response: ResponseEntity<String> = service("http://localhost:$stubPort")
      .copilot(
        UUID.randomUUID(), ObjectMapper().createObjectNode(),
        requestWith("improve my summary"), "Bearer token"
      )
    assertThat(response.statusCode.value()).isEqualTo(200)
    assertThat(response.body).contains("Sharpen the summary")
  }

  @Test
  fun forwardsAgentBrain401Verbatim() {
    val response: ResponseEntity<String> = service("http://localhost:$stubPort")
      .copilot(
        UUID.randomUUID(), ObjectMapper().createObjectNode(),
        requestWith("force-401"), "Bearer token"
      )
    assertThat(response.statusCode.value()).isEqualTo(401)
    assertThat(response.body).contains("bad token")
  }

  @Test
  fun forwardsAgentBrain500Verbatim() {
    val response: ResponseEntity<String> = service("http://localhost:$stubPort")
      .copilot(
        UUID.randomUUID(), ObjectMapper().createObjectNode(),
        requestWith("force-500"), "Bearer token"
      )
    assertThat(response.statusCode.value()).isEqualTo(500)
    assertThat(response.body).contains("COPILOT_FAILED")
  }

  @Test
  fun unreachableAgentBrainIs502() {
    assertThatThrownBy {
      service("http://localhost:1")
        .copilot(
          UUID.randomUUID(), ObjectMapper().createObjectNode(),
          requestWith("hello"), "Bearer token"
        )
    }.isInstanceOf(AgentUnavailableException::class.java)
  }
}
