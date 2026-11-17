package com.epitomejob.resumebuilder.service

import com.epitomejob.resumebuilder.common.exception.AgentUnavailableException
import com.epitomejob.resumebuilder.dto.ResumeBuilderDtos.SaveToProfileResponse
import com.epitomejob.resumebuilder.model.ResumeDocument
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.springframework.web.client.RestClient
import org.springframework.web.server.ResponseStatusException
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.UUID
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy

/**
 * Save-to-profile tests against a real JDK HttpServer stub for POST
 * /resumes. The stub returns 201 with a fixed resume id, or 400 when the
 * multipart body contains the label "bad-label" (simulating a backend
 * validation rejection). The upload is the PDF rendering — the monolith
 * accepts PDF only — so the stub's canned response describes a PDF file.
 */
class SaveToProfileServiceTest {

  companion object {
    private val RESUME_ID: UUID = UUID.randomUUID()
    private lateinit var stub: HttpServer
    private var stubPort: Int = 0

    @BeforeAll
    @JvmStatic
    fun startStub() {
      stub = HttpServer.create(InetSocketAddress(0), 0)
      stub.createContext("/resumes") { exchange ->
        val body = String(exchange.requestBody.readAllBytes(), StandardCharsets.UTF_8)
        var status = 201
        var response = "{\"id\":\"" + RESUME_ID + "\",\"fileName\":\"ada-resume.pdf\"," +
          "\"fileType\":\"pdf\",\"fileSize\":123,\"status\":\"READY\"," +
          "\"createdAt\":\"2026-11-14T00:00:00Z\",\"label\":\"Android Developer\"}"
        if (body.contains("bad-label")) {
          status = 400
          response = "{\"code\":\"BAD_REQUEST\",\"message\":\"Phase 0 accepts PDF only (DOCX comes in Phase 3)\"}"
        }
        val bytes = response.toByteArray(StandardCharsets.UTF_8)
        exchange.responseHeaders.set("Content-Type", "application/json")
        exchange.sendResponseHeaders(status, bytes.size.toLong())
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
  }

  private fun service(baseUrl: String): SaveToProfileService {
    val mapper = ObjectMapper()
    return SaveToProfileService(
      RestClient.builder().baseUrl(baseUrl).build(),
      ExportService(TemplateService(mapper)), mapper
    )
  }

  private fun doc(): ResumeDocument {
    return ResumeDocument(
      UUID.randomUUID(), "Ada Resume", "modern",
      "{\"contact\":{\"fullName\":\"Ada Lovelace\"},\"summary\":\"\",\"experience\":[]," +
        "\"education\":[],\"skills\":[],\"rawText\":\"\"}"
    )
  }

  private fun state(): JsonNode {
    return ObjectMapper().readTree(doc().state)
  }

  @Test
  fun returnsTheMonolithResumeId() {
    val response: SaveToProfileResponse = service("http://localhost:$stubPort")
      .saveToProfile(doc(), state(), "Android Developer", "Bearer token")
    assertThat(response.resumeId).isEqualTo(RESUME_ID)
    assertThat(response.label).isEqualTo("Android Developer")
  }

  @Test
  fun backend400PassesThroughAsResponseStatus() {
    assertThatThrownBy {
      service("http://localhost:$stubPort")
        .saveToProfile(doc(), state(), "bad-label", "Bearer token")
    }
      .isInstanceOfSatisfying(ResponseStatusException::class.java) { ex ->
        assertThat(ex.statusCode.value()).isEqualTo(400)
      }
  }

  @Test
  fun blankLabelIs400BeforeAnyCall() {
    assertThatThrownBy {
      service("http://localhost:$stubPort")
        .saveToProfile(doc(), state(), "  ", "Bearer token")
    }
      .isInstanceOfSatisfying(ResponseStatusException::class.java) { ex ->
        assertThat(ex.statusCode.value()).isEqualTo(400)
      }
  }

  @Test
  fun unreachableBackendIs502() {
    assertThatThrownBy {
      service("http://localhost:1")
        .saveToProfile(doc(), state(), "Android Developer", "Bearer token")
    }.isInstanceOf(AgentUnavailableException::class.java)
  }
}
