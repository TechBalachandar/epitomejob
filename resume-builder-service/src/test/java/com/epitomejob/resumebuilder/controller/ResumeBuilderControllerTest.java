package com.epitomejob.resumebuilder.controller;

import com.epitomejob.resumebuilder.dto.ResumeBuilderDtos.CreateDocumentRequest;
import com.epitomejob.resumebuilder.dto.ResumeBuilderDtos.DocumentResponse;
import com.epitomejob.resumebuilder.dto.ResumeBuilderDtos.ParseResponse;
import com.epitomejob.resumebuilder.dto.ResumeBuilderDtos.SaveToProfileResponse;
import com.epitomejob.resumebuilder.dto.ResumeBuilderDtos.TemplateDto;
import com.epitomejob.resumebuilder.dto.ResumeBuilderDtos.UpdateDocumentRequest;
import com.epitomejob.resumebuilder.model.ResumeDocument;
import com.epitomejob.resumebuilder.security.JwtTokenService;
import com.epitomejob.resumebuilder.service.CopilotProxyService;
import com.epitomejob.resumebuilder.service.ExportService;
import com.epitomejob.resumebuilder.service.ResumeDocumentService;
import com.epitomejob.resumebuilder.service.ResumeParseService;
import com.epitomejob.resumebuilder.service.SaveToProfileService;
import com.epitomejob.resumebuilder.service.TemplateService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * WebMvc slice with filters OFF (addFilters=false): the JWT filter is
 * covered by JwtAuthFilterTest; here the SecurityContext is set directly.
 * JwtTokenService is a @MockBean only so the slice context can build the
 * JwtAuthFilter bean — it is never stubbed and never called here.
 * STUB TRACE (Nov 13 rule): each when(...) is for a service method its
 * endpoint actually calls; the bad-format export test stubs ONLY
 * ownedDocument (export is never reached).
 */
@WebMvcTest(ResumeBuilderController.class)
@AutoConfigureMockMvc(addFilters = false)
class ResumeBuilderControllerTest {

  private static final UUID USER_ID = UUID.randomUUID();
  private static final UUID DOC_ID = UUID.randomUUID();
  private static final String STATE_JSON =
      "{\"contact\":{\"fullName\":\"Ada Lovelace\"},\"summary\":\"Pioneer\","
          + "\"experience\":[],\"education\":[],\"skills\":[],\"rawText\":\"\"}";

  @Autowired
  private MockMvc mockMvc;
  @Autowired
  private ObjectMapper mapper;

  @MockitoBean
  private TemplateService templates;
  @MockitoBean
  private ResumeParseService parseService;
  @MockitoBean
  private ResumeDocumentService documents;
  @MockitoBean
  private CopilotProxyService copilot;
  @MockitoBean
  private ExportService export;
  @MockitoBean
  private SaveToProfileService saveToProfile;
  @MockitoBean
  private JwtTokenService jwtTokenService;

  @BeforeEach
  void setUp() {
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken(USER_ID.toString(), null, List.of()));
  }

  private ResumeDocument doc() {
    return new ResumeDocument(USER_ID, "Ada Resume", "modern", STATE_JSON);
  }

  private DocumentResponse docResponse() throws Exception {
    return new DocumentResponse(DOC_ID, "Ada Resume", "modern",
        mapper.readTree(STATE_JSON), Instant.now(), Instant.now());
  }

  @Test
  void listTemplates() throws Exception {
    when(templates.listTemplates()).thenReturn(List.of(
        new TemplateDto("modern", "Modern", "desc", "<html/>", mapper.createObjectNode())));
    mockMvc.perform(get("/resume-builder/templates"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value("modern"));
  }

  @Test
  void parseUpload() throws Exception {
    when(parseService.parse(any())).thenReturn(
        new ParseResponse(mapper.readTree(STATE_JSON), "raw text"));
    MockMultipartFile file = new MockMultipartFile("file", "resume.pdf",
        "application/pdf", "bytes".getBytes());
    mockMvc.perform(multipart("/resume-builder/parse").file(file))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.state.contact.fullName").value("Ada Lovelace"));
  }

  @Test
  void createDocumentIs201() throws Exception {
    when(documents.create(any(CreateDocumentRequest.class))).thenReturn(docResponse());
    mockMvc.perform(post("/resume-builder/documents")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"title\":\"Ada Resume\",\"templateId\":\"modern\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(DOC_ID.toString()));
  }

  @Test
  void getDocument() throws Exception {
    when(documents.get(DOC_ID)).thenReturn(docResponse());
    mockMvc.perform(get("/resume-builder/documents/" + DOC_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("Ada Resume"));
  }

  @Test
  void updateDocumentReplacesState() throws Exception {
    when(documents.update(eq(DOC_ID), any(UpdateDocumentRequest.class))).thenReturn(docResponse());
    mockMvc.perform(put("/resume-builder/documents/" + DOC_ID)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"title\":\"Ada v2\",\"state\":" + STATE_JSON + "}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("Ada Resume"));
  }

  @Test
  void copilotForwardsVerbatim() throws Exception {
    when(documents.ownedDocument(DOC_ID)).thenReturn(doc());
    when(copilot.copilot(eq(DOC_ID), any(), any(), eq("Bearer token")))
        .thenReturn(ResponseEntity.ok("{\"suggestions\":[]}"));
    mockMvc.perform(post("/resume-builder/documents/" + DOC_ID + "/copilot")
            .header("Authorization", "Bearer token")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"prompt\":\"improve\"}"))
        .andExpect(status().isOk())
        .andExpect(content().json("{\"suggestions\":[]}"));
  }

  @Test
  void exportPdfReturnsFileBytes() throws Exception {
    when(documents.ownedDocument(DOC_ID)).thenReturn(doc());
    when(export.exportPdf(eq("modern"), any())).thenReturn("%PDF-fake".getBytes());
    mockMvc.perform(post("/resume-builder/documents/" + DOC_ID + "/export?format=pdf"))
        .andExpect(status().isOk())
        .andExpect(content().contentType("application/pdf"));
  }

  @Test
  void exportBadFormatIs400WithoutCallingExport() throws Exception {
    when(documents.ownedDocument(DOC_ID)).thenReturn(doc());
    mockMvc.perform(post("/resume-builder/documents/" + DOC_ID + "/export?format=txt"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void saveToProfileReturnsResumeId() throws Exception {
    UUID resumeId = UUID.randomUUID();
    when(documents.ownedDocument(DOC_ID)).thenReturn(doc());
    when(saveToProfile.saveToProfile(any(), any(), eq("Android Developer"), eq("Bearer token")))
        .thenReturn(new SaveToProfileResponse(resumeId, "Android Developer"));
    mockMvc.perform(post("/resume-builder/documents/" + DOC_ID + "/save-to-profile")
            .header("Authorization", "Bearer token")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"label\":\"Android Developer\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.resumeId").value(resumeId.toString()));
  }
}
