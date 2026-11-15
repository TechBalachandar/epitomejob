package com.epitomejob.resumebuilder.service

import com.epitomejob.resumebuilder.TestFixtures
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.mock.web.MockMultipartFile
import org.springframework.web.server.ResponseStatusException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy

class ResumeParseServiceTest {

  private val service = ResumeParseService(ResumeStateParser(ObjectMapper()))

  @Test
  fun parsesPdfIntoStructuredState() {
    val file = MockMultipartFile(
      "file", "resume.pdf",
      "application/pdf", TestFixtures.pdfFixture()
    )
    val state = service.parse(file).state as ObjectNode
    assertThat(state.path("contact").path("fullName").asText()).isEqualTo("Ada Lovelace")
    assertThat(state.path("contact").path("email").asText()).isEqualTo("ada@example.com")
    assertThat(state.path("experience")).hasSize(1)
    assertThat(state.path("experience").get(0).path("company").asText())
      .isEqualTo("Analytical Engines Ltd")
    assertThat(service.parse(file).rawText).contains("Designed the first algorithm")
  }

  @Test
  fun parsesDocxIntoStructuredState() {
    val file = MockMultipartFile(
      "file", "resume.docx",
      "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
      TestFixtures.docxFixture()
    )
    val state = service.parse(file).state as ObjectNode
    assertThat(state.path("contact").path("fullName").asText()).isEqualTo("Ada Lovelace")
    assertThat(state.path("skills").toString()).contains("Java")
  }

  @Test
  fun parsesEmptyDocWithoutThrowing() {
    val file = MockMultipartFile(
      "file", "resume.doc",
      "application/msword", TestFixtures.docFixture()
    )
    assertThat(service.parse(file).state).isNotNull()
    assertThat(service.parse(file).rawText).isBlank()
  }

  @Test
  fun rejectsEmptyFile() {
    val file = MockMultipartFile(
      "file", "resume.pdf",
      "application/pdf", ByteArray(0)
    )
    assertThatThrownBy { service.parse(file) }
      .isInstanceOf(ResponseStatusException::class.java)
      .satisfies { ex ->
        assertThat((ex as ResponseStatusException).statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
      }
  }

  @Test
  fun rejectsUnsupportedExtension() {
    val file = MockMultipartFile(
      "file", "resume.txt",
      "text/plain", "hello".toByteArray()
    )
    assertThatThrownBy { service.parse(file) }
      .isInstanceOf(ResponseStatusException::class.java)
      .satisfies { ex ->
        assertThat((ex as ResponseStatusException).statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
      }
  }

  @Test
  fun corruptPdfIsA400NotA500() {
    val file = MockMultipartFile(
      "file", "resume.pdf",
      "application/pdf", "not a pdf at all".toByteArray()
    )
    assertThatThrownBy { service.parse(file) }
      .isInstanceOf(ResponseStatusException::class.java)
      .satisfies { ex ->
        assertThat((ex as ResponseStatusException).statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
      }
  }
}
