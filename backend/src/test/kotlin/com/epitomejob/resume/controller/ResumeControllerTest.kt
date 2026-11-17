package com.epitomejob.resume.controller

import com.epitomejob.common.exception.ResumeNotReadyException
import com.epitomejob.common.security.SecurityConfig
import com.epitomejob.identity.service.JwtService
import com.epitomejob.resume.model.Resume
import com.epitomejob.resume.service.ResumeService
import io.jsonwebtoken.Claims
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

@WebMvcTest(ResumeController::class)
@Import(SecurityConfig::class)
class ResumeControllerTest {

  companion object {
    private val USER_ID: UUID = UUID.randomUUID()
  }

  @Autowired
  private lateinit var mockMvc: MockMvc

  @MockitoBean
  private lateinit var service: ResumeService

  @MockitoBean
  private lateinit var jwt: JwtService

  private fun authenticate() {
    val claims = mock(Claims::class.java)
    Mockito.`when`(claims.subject).thenReturn(USER_ID.toString())
    Mockito.`when`(claims.get("role", String::class.java)).thenReturn("ROLE_USER")
    Mockito.`when`(jwt.parse("test-token")).thenReturn(claims)
  }

  private fun resume(): Resume {
    val resume = Resume(USER_ID, "u1/f.pdf", "resume.pdf", "pdf", 100)
    resume.status = "READY"
    return resume
  }

  @Test
  fun uploadReturns201WithMetadataOnly() {
    authenticate()
    Mockito.`when`(service.upload(any(MultipartFile::class.java), any())).thenReturn(resume())
    val file = MockMultipartFile("file", "resume.pdf", "application/pdf", "fake-pdf".toByteArray())

    mockMvc.perform(
      multipart("/resumes").file(file)
        .header("Authorization", "Bearer test-token")
    )
      .andExpect(status().isCreated())
      .andExpect(jsonPath("$.fileName").value("resume.pdf"))
      .andExpect(jsonPath("$.status").value("READY"))
      .andExpect(jsonPath("$.label").value("resume.pdf"))
      .andExpect(jsonPath("$.blobPath").doesNotExist())
      .andExpect(jsonPath("$.parsedText").doesNotExist())
  }

  @Test
  fun uploadWithLabelReturnsLabel() {
    authenticate()
    val labelled = resume()
    labelled.label = "Android Developer"
    Mockito.`when`(service.upload(any(MultipartFile::class.java), eq("Android Developer")))
      .thenReturn(labelled)
    val file = MockMultipartFile("file", "resume.pdf", "application/pdf", "fake-pdf".toByteArray())

    mockMvc.perform(
      multipart("/resumes").file(file).param("label", "Android Developer")
        .header("Authorization", "Bearer test-token")
    )
      .andExpect(status().isCreated())
      .andExpect(jsonPath("$.label").value("Android Developer"))

    verify(service).upload(any(MultipartFile::class.java), eq("Android Developer"))
  }

  @Test
  fun listMineReturnsLabels() {
    authenticate()
    val labelled = resume()
    labelled.label = "Android Developer"
    Mockito.`when`(service.listMine()).thenReturn(mutableListOf(labelled))

    mockMvc.perform(get("/resumes/me").header("Authorization", "Bearer test-token"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$[0].fileName").value("resume.pdf"))
      .andExpect(jsonPath("$[0].label").value("Android Developer"))
  }

  @Test
  fun renameLabelReturnsUpdatedResume() {
    authenticate()
    val renamed = resume()
    renamed.label = "Java Backend"
    Mockito.`when`(service.renameLabel(eq(renamed.id), eq("Java Backend"))).thenReturn(renamed)

    mockMvc.perform(
      put("/resumes/" + renamed.id)
        .header("Authorization", "Bearer test-token")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"label\":\"Java Backend\"}")
    )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.label").value("Java Backend"))
  }

  @Test
  fun renameLabelMissingIs404() {
    authenticate()
    val id = UUID.randomUUID()
    Mockito.`when`(service.renameLabel(eq(id), any())).thenThrow(
      ResponseStatusException(HttpStatus.NOT_FOUND, "Resume not found")
    )

    mockMvc.perform(
      put("/resumes/" + id)
        .header("Authorization", "Bearer test-token")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"label\":\"Java Backend\"}")
    )
      .andExpect(status().isNotFound())
  }

  @Test
  fun renameLabelBlankIs400() {
    authenticate()
    // @Valid rejects a blank label before the service is ever called: no stub.
    mockMvc.perform(
      put("/resumes/" + UUID.randomUUID())
        .header("Authorization", "Bearer test-token")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"label\":\"   \"}")
    )
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
  }

  @Test
  fun renameLabelTooLongIs400() {
    authenticate()
    // @Valid rejects an over-80 label before the service is ever called: no stub.
    mockMvc.perform(
      put("/resumes/" + UUID.randomUUID())
        .header("Authorization", "Bearer test-token")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"label\":\"" + "x".repeat(81) + "\"}")
    )
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
  }

  @Test
  fun listMineReturnsResumes() {
    authenticate()
    Mockito.`when`(service.listMine()).thenReturn(mutableListOf(resume()))

    mockMvc.perform(get("/resumes/me").header("Authorization", "Bearer test-token"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$[0].fileName").value("resume.pdf"))
  }

  @Test
  fun uploadWithoutTokenIsForbidden() {
    val file = MockMultipartFile("file", "resume.pdf", "application/pdf", "fake-pdf".toByteArray())
    mockMvc.perform(multipart("/resumes").file(file))
      .andExpect(status().isForbidden())
  }

  @Test
  fun getTextReturnsResumeIdAndText() {
    authenticate()
    val resume = resume()
    resume.parsedText = "Java Developer with Spring Boot"
    Mockito.`when`(service.getParsedText(resume.id)).thenReturn(resume)

    mockMvc.perform(get("/resumes/" + resume.id + "/text").header("Authorization", "Bearer test-token"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.resumeId").value(resume.id.toString()))
      .andExpect(jsonPath("$.text").value("Java Developer with Spring Boot"))
  }

  @Test
  fun getTextNotFoundIs404() {
    authenticate()
    Mockito.`when`(service.getParsedText(any())).thenThrow(
      ResponseStatusException(HttpStatus.NOT_FOUND, "Resume not found")
    )

    mockMvc.perform(get("/resumes/" + UUID.randomUUID() + "/text").header("Authorization", "Bearer test-token"))
      .andExpect(status().isNotFound())
  }

  @Test
  fun getTextNotReadyIs400WithCode() {
    authenticate()
    Mockito.`when`(service.getParsedText(any())).thenThrow(ResumeNotReadyException())

    mockMvc.perform(get("/resumes/" + UUID.randomUUID() + "/text").header("Authorization", "Bearer test-token"))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value("RESUME_NOT_READY"))
  }
}
