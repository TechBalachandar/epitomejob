package com.epitomejob.resumebuilder.controller

import com.epitomejob.resumebuilder.dto.ResumeBuilderDtos.CreateDocumentRequest
import com.epitomejob.resumebuilder.dto.ResumeBuilderDtos.DocumentResponse
import com.epitomejob.resumebuilder.dto.ResumeBuilderDtos.ParseResponse
import com.epitomejob.resumebuilder.dto.ResumeBuilderDtos.SaveToProfileResponse
import com.epitomejob.resumebuilder.dto.ResumeBuilderDtos.TemplateDto
import com.epitomejob.resumebuilder.dto.ResumeBuilderDtos.UpdateDocumentRequest
import com.epitomejob.resumebuilder.model.ResumeDocument
import com.epitomejob.resumebuilder.security.JwtTokenService
import com.epitomejob.resumebuilder.service.CopilotProxyService
import com.epitomejob.resumebuilder.service.ExportService
import com.epitomejob.resumebuilder.service.ResumeDocumentService
import com.epitomejob.resumebuilder.service.ResumeParseService
import com.epitomejob.resumebuilder.service.SaveToProfileService
import com.epitomejob.resumebuilder.service.TemplateService
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.mock.web.MockMultipartFile
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import java.util.UUID

/**
 * WebMvc slice with filters OFF (addFilters=false): the JWT filter is
 * covered by JwtAuthFilterTest; here the SecurityContext is set directly.
 * JwtTokenService is a @MockitoBean only so the slice context can build the
 * JwtAuthFilter bean — it is never stubbed and never called here.
 * STUB TRACE (Nov 13 rule): each when(...) is for a service method its
 * endpoint actually calls; the bad-format export test stubs ONLY
 * ownedDocument (export is never reached).
 *
 * Matcher note: the mocked services are Kotlin classes with non-null
 * parameters, so plain Mockito `any()`/`eq()` would NPE (they return null) —
 * the stubs use mockito-kotlin's `any()`/`eq()` (non-null dummies / the value
 * itself) with the exact values the controller passes (the same
 * MockMultipartFile instance, the same owned document instance, JsonNodes
 * structurally equal to the request/state JSON).
 */
@WebMvcTest(ResumeBuilderController::class)
@AutoConfigureMockMvc(addFilters = false)
class ResumeBuilderControllerTest {

  companion object {
    private val USER_ID: UUID = UUID.randomUUID()
    private val DOC_ID: UUID = UUID.randomUUID()
    private const val STATE_JSON: String =
      "{\"contact\":{\"fullName\":\"Ada Lovelace\"},\"summary\":\"Pioneer\","
        + "\"experience\":[],\"education\":[],\"skills\":[],\"rawText\":\"\"}"
  }

  @Autowired
  private lateinit var mockMvc: MockMvc

  @Autowired
  private lateinit var mapper: ObjectMapper

  @MockitoBean
  private lateinit var templates: TemplateService

  @MockitoBean
  private lateinit var parseService: ResumeParseService

  @MockitoBean
  private lateinit var documents: ResumeDocumentService

  @MockitoBean
  private lateinit var copilot: CopilotProxyService

  @MockitoBean
  private lateinit var export: ExportService

  @MockitoBean
  private lateinit var saveToProfile: SaveToProfileService

  @MockitoBean
  private lateinit var jwtTokenService: JwtTokenService

  @BeforeEach
  fun setUp() {
    SecurityContextHolder.getContext().authentication =
      UsernamePasswordAuthenticationToken(USER_ID.toString(), null, emptyList<GrantedAuthority>())
  }

  private fun doc(): ResumeDocument {
    return ResumeDocument(USER_ID, "Ada Resume", "modern", STATE_JSON)
  }

  private fun docResponse(): DocumentResponse {
    return DocumentResponse(
      DOC_ID, "Ada Resume", "modern",
      mapper.readTree(STATE_JSON), Instant.now(), Instant.now()
    )
  }

  @Test
  fun listTemplates() {
    whenever(templates.listTemplates()).thenReturn(
      listOf(
        TemplateDto("modern", "Modern", "desc", "<html/>", mapper.createObjectNode())
      )
    )
    mockMvc.perform(get("/resume-builder/templates"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$[0].id").value("modern"))
  }

  @Test
  fun parseUpload() {
    val file = MockMultipartFile(
      "file", "resume.pdf",
      "application/pdf", "bytes".toByteArray()
    )
    whenever(parseService.parse(eq(file))).thenReturn(
      ParseResponse(mapper.readTree(STATE_JSON), "raw text")
    )
    mockMvc.perform(multipart("/resume-builder/parse").file(file))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.state.contact.fullName").value("Ada Lovelace"))
  }

  @Test
  fun createDocumentIs201() {
    whenever(
      documents.create(eq(CreateDocumentRequest("Ada Resume", "modern", null)))
    ).thenReturn(docResponse())
    mockMvc.perform(
      post("/resume-builder/documents")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"title\":\"Ada Resume\",\"templateId\":\"modern\"}")
    )
      .andExpect(status().isCreated())
      .andExpect(jsonPath("$.id").value(DOC_ID.toString()))
  }

  @Test
  fun getDocument() {
    whenever(documents.get(DOC_ID)).thenReturn(docResponse())
    mockMvc.perform(get("/resume-builder/documents/$DOC_ID"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.title").value("Ada Resume"))
  }

  @Test
  fun updateDocumentReplacesState() {
    whenever(
      documents.update(
        eq(DOC_ID),
        eq(UpdateDocumentRequest("Ada v2", null, mapper.readTree(STATE_JSON)))
      )
    ).thenReturn(docResponse())
    mockMvc.perform(
      put("/resume-builder/documents/$DOC_ID")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"title\":\"Ada v2\",\"state\":$STATE_JSON}")
    )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.title").value("Ada Resume"))
  }

  @Test
  fun copilotForwardsVerbatim() {
    whenever(documents.ownedDocument(DOC_ID)).thenReturn(doc())
    whenever(
      copilot.copilot(
        eq(DOC_ID),
        eq(mapper.readTree(STATE_JSON)),
        eq(mapper.readTree("{\"prompt\":\"improve\"}")),
        eq("Bearer token")
      )
    ).thenReturn(ResponseEntity.ok("{\"suggestions\":[]}"))
    mockMvc.perform(
      post("/resume-builder/documents/$DOC_ID/copilot")
        .header("Authorization", "Bearer token")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"prompt\":\"improve\"}")
    )
      .andExpect(status().isOk())
      .andExpect(content().json("{\"suggestions\":[]}"))
  }

  @Test
  fun exportPdfReturnsFileBytes() {
    whenever(documents.ownedDocument(DOC_ID)).thenReturn(doc())
    whenever(
      export.exportPdf(eq("modern"), eq(mapper.readTree(STATE_JSON)))
    ).thenReturn("%PDF-fake".toByteArray())
    mockMvc.perform(post("/resume-builder/documents/$DOC_ID/export?format=pdf"))
      .andExpect(status().isOk())
      .andExpect(content().contentType("application/pdf"))
  }

  @Test
  fun exportBadFormatIs400WithoutCallingExport() {
    whenever(documents.ownedDocument(DOC_ID)).thenReturn(doc())
    mockMvc.perform(post("/resume-builder/documents/$DOC_ID/export?format=txt"))
      .andExpect(status().isBadRequest())
  }

  @Test
  fun saveToProfileReturnsResumeId() {
    val resumeId = UUID.randomUUID()
    val document = doc()
    whenever(documents.ownedDocument(DOC_ID)).thenReturn(document)
    whenever(
      saveToProfile.saveToProfile(
        eq(document),
        eq(mapper.readTree(STATE_JSON)),
        eq("Android Developer"),
        eq("Bearer token")
      )
    ).thenReturn(SaveToProfileResponse(resumeId, "Android Developer"))
    mockMvc.perform(
      post("/resume-builder/documents/$DOC_ID/save-to-profile")
        .header("Authorization", "Bearer token")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"label\":\"Android Developer\"}")
    )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.resumeId").value(resumeId.toString()))
  }
}
