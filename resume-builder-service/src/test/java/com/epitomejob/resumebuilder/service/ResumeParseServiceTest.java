package com.epitomejob.resumebuilder.service;

import com.epitomejob.resumebuilder.TestFixtures;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResumeParseServiceTest {

  private final ResumeParseService service = new ResumeParseService(new ResumeStateParser(new ObjectMapper()));

  @Test
  void parsesPdfIntoStructuredState() throws Exception {
    MockMultipartFile file = new MockMultipartFile("file", "resume.pdf",
        "application/pdf", TestFixtures.pdfFixture());
    ObjectNode state = (ObjectNode) service.parse(file).state();
    assertThat(state.path("contact").path("fullName").asText()).isEqualTo("Ada Lovelace");
    assertThat(state.path("contact").path("email").asText()).isEqualTo("ada@example.com");
    assertThat(state.path("experience")).hasSize(1);
    assertThat(state.path("experience").get(0).path("company").asText())
        .isEqualTo("Analytical Engines Ltd");
    assertThat(service.parse(file).rawText()).contains("Designed the first algorithm");
  }

  @Test
  void parsesDocxIntoStructuredState() throws Exception {
    MockMultipartFile file = new MockMultipartFile("file", "resume.docx",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document", TestFixtures.docxFixture());
    ObjectNode state = (ObjectNode) service.parse(file).state();
    assertThat(state.path("contact").path("fullName").asText()).isEqualTo("Ada Lovelace");
    assertThat(state.path("skills").toString()).contains("Java");
  }

  @Test
  void parsesEmptyDocWithoutThrowing() throws Exception {
    MockMultipartFile file = new MockMultipartFile("file", "resume.doc",
        "application/msword", TestFixtures.docFixture());
    assertThat(service.parse(file).state()).isNotNull();
    assertThat(service.parse(file).rawText()).isBlank();
  }

  @Test
  void rejectsEmptyFile() {
    MockMultipartFile file = new MockMultipartFile("file", "resume.pdf",
        "application/pdf", new byte[0]);
    assertThatThrownBy(() -> service.parse(file))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST));
  }

  @Test
  void rejectsUnsupportedExtension() {
    MockMultipartFile file = new MockMultipartFile("file", "resume.txt",
        "text/plain", "hello".getBytes());
    assertThatThrownBy(() -> service.parse(file))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST));
  }

  @Test
  void corruptPdfIsA400NotA500() {
    MockMultipartFile file = new MockMultipartFile("file", "resume.pdf",
        "application/pdf", "not a pdf at all".getBytes());
    assertThatThrownBy(() -> service.parse(file))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST));
  }
}
