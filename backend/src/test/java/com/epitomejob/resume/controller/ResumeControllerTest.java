package com.epitomejob.resume.controller;

import com.epitomejob.common.exception.ResumeNotReadyException;
import com.epitomejob.common.security.SecurityConfig;
import com.epitomejob.identity.service.JwtService;
import com.epitomejob.resume.model.Resume;
import com.epitomejob.resume.service.ResumeService;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ResumeController.class)
@Import(SecurityConfig.class)
class ResumeControllerTest {

  private static final UUID USER_ID = UUID.randomUUID();

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private ResumeService service;

  @MockitoBean
  private JwtService jwt;

  private void authenticate() {
    Claims claims = mock(Claims.class);
    when(claims.getSubject()).thenReturn(USER_ID.toString());
    when(claims.get("role", String.class)).thenReturn("ROLE_USER");
    when(jwt.parse("test-token")).thenReturn(claims);
  }

  private Resume resume() {
    Resume resume = new Resume(USER_ID, "u1/f.pdf", "resume.pdf", "pdf", 100);
    resume.setStatus("READY");
    return resume;
  }

  @Test
  void uploadReturns201WithMetadataOnly() throws Exception {
    authenticate();
    when(service.upload(any(MultipartFile.class), any())).thenReturn(resume());
    var file = new MockMultipartFile("file", "resume.pdf", "application/pdf", "fake-pdf".getBytes());

    mockMvc.perform(multipart("/resumes").file(file)
            .header("Authorization", "Bearer test-token"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.fileName").value("resume.pdf"))
        .andExpect(jsonPath("$.status").value("READY"))
        .andExpect(jsonPath("$.label").value("resume.pdf"))
        .andExpect(jsonPath("$.blobPath").doesNotExist())
        .andExpect(jsonPath("$.parsedText").doesNotExist());
  }

  @Test
  void uploadWithLabelReturnsLabel() throws Exception {
    authenticate();
    Resume labelled = resume();
    labelled.setLabel("Android Developer");
    when(service.upload(any(MultipartFile.class), eq("Android Developer"))).thenReturn(labelled);
    var file = new MockMultipartFile("file", "resume.pdf", "application/pdf", "fake-pdf".getBytes());

    mockMvc.perform(multipart("/resumes").file(file).param("label", "Android Developer")
            .header("Authorization", "Bearer test-token"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.label").value("Android Developer"));

    verify(service).upload(any(MultipartFile.class), eq("Android Developer"));
  }

  @Test
  void listMineReturnsLabels() throws Exception {
    authenticate();
    Resume labelled = resume();
    labelled.setLabel("Android Developer");
    when(service.listMine()).thenReturn(List.of(labelled));

    mockMvc.perform(get("/resumes/me").header("Authorization", "Bearer test-token"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].fileName").value("resume.pdf"))
        .andExpect(jsonPath("$[0].label").value("Android Developer"));
  }

  @Test
  void renameLabelReturnsUpdatedResume() throws Exception {
    authenticate();
    Resume renamed = resume();
    renamed.setLabel("Java Backend");
    when(service.renameLabel(eq(renamed.getId()), eq("Java Backend"))).thenReturn(renamed);

    mockMvc.perform(put("/resumes/" + renamed.getId())
            .header("Authorization", "Bearer test-token")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"label":"Java Backend"}"""))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.label").value("Java Backend"));
  }

  @Test
  void renameLabelMissingIs404() throws Exception {
    authenticate();
    UUID id = UUID.randomUUID();
    when(service.renameLabel(eq(id), any())).thenThrow(
        new ResponseStatusException(HttpStatus.NOT_FOUND, "Resume not found"));

    mockMvc.perform(put("/resumes/" + id)
            .header("Authorization", "Bearer test-token")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"label":"Java Backend"}"""))
        .andExpect(status().isNotFound());
  }

  @Test
  void renameLabelBlankIs400() throws Exception {
    authenticate();
    // @Valid rejects a blank label before the service is ever called: no stub.
    mockMvc.perform(put("/resumes/" + UUID.randomUUID())
            .header("Authorization", "Bearer test-token")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"label":"   "}"""))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  @Test
  void renameLabelTooLongIs400() throws Exception {
    authenticate();
    // @Valid rejects an over-80 label before the service is ever called: no stub.
    mockMvc.perform(put("/resumes/" + UUID.randomUUID())
            .header("Authorization", "Bearer test-token")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"label\":\"" + "x".repeat(81) + "\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  @Test
  void listMineReturnsResumes() throws Exception {
    authenticate();
    when(service.listMine()).thenReturn(List.of(resume()));

    mockMvc.perform(get("/resumes/me").header("Authorization", "Bearer test-token"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].fileName").value("resume.pdf"));
  }

  @Test
  void uploadWithoutTokenIsForbidden() throws Exception {
    var file = new MockMultipartFile("file", "resume.pdf", "application/pdf", "fake-pdf".getBytes());
    mockMvc.perform(multipart("/resumes").file(file))
        .andExpect(status().isForbidden());
  }

  @Test
  void getTextReturnsResumeIdAndText() throws Exception {
    authenticate();
    Resume resume = resume();
    resume.setParsedText("Java Developer with Spring Boot");
    when(service.getParsedText(resume.getId())).thenReturn(resume);

    mockMvc.perform(get("/resumes/" + resume.getId() + "/text")
            .header("Authorization", "Bearer test-token"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.resumeId").value(resume.getId().toString()))
        .andExpect(jsonPath("$.text").value("Java Developer with Spring Boot"));
  }

  @Test
  void getTextNotFoundIs404() throws Exception {
    authenticate();
    when(service.getParsedText(any())).thenThrow(
        new ResponseStatusException(HttpStatus.NOT_FOUND, "Resume not found"));

    mockMvc.perform(get("/resumes/" + UUID.randomUUID() + "/text")
            .header("Authorization", "Bearer test-token"))
        .andExpect(status().isNotFound());
  }

  @Test
  void getTextNotReadyIs400WithCode() throws Exception {
    authenticate();
    when(service.getParsedText(any())).thenThrow(new ResumeNotReadyException());

    mockMvc.perform(get("/resumes/" + UUID.randomUUID() + "/text")
            .header("Authorization", "Bearer test-token"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("RESUME_NOT_READY"));
  }
}
