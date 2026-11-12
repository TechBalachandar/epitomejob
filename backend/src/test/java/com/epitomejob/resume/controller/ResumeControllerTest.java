package com.epitomejob.resume.controller;

import com.epitomejob.common.security.SecurityConfig;
import com.epitomejob.identity.service.JwtService;
import com.epitomejob.resume.model.Resume;
import com.epitomejob.resume.service.ResumeService;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
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
    when(service.upload(any())).thenReturn(resume());
    var file = new MockMultipartFile("file", "resume.pdf", "application/pdf", "fake-pdf".getBytes());

    mockMvc.perform(multipart("/resumes").file(file)
            .header("Authorization", "Bearer test-token"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.fileName").value("resume.pdf"))
        .andExpect(jsonPath("$.status").value("READY"))
        .andExpect(jsonPath("$.blobPath").doesNotExist())
        .andExpect(jsonPath("$.parsedText").doesNotExist());
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
}
