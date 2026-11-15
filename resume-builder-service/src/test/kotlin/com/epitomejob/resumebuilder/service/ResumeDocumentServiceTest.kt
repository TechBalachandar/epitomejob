package com.epitomejob.resumebuilder.service

import com.epitomejob.resumebuilder.dto.ResumeBuilderDtos.CreateDocumentRequest
import com.epitomejob.resumebuilder.dto.ResumeBuilderDtos.DocumentResponse
import com.epitomejob.resumebuilder.dto.ResumeBuilderDtos.TemplateDto
import com.epitomejob.resumebuilder.dto.ResumeBuilderDtos.UpdateDocumentRequest
import com.epitomejob.resumebuilder.model.ResumeDocument
import com.epitomejob.resumebuilder.repository.ResumeDocumentRepository
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.any
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.http.HttpStatus
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.server.ResponseStatusException
import java.util.Optional
import java.util.UUID
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy

/**
 * STUB TRACE (Nov 13 rule): every when(...) below is reachable on its
 * test's code path — create calls getTemplate then save; get/update call
 * findByIdAndUserId (update also getTemplate + save); the validation-first
 * tests stub NOTHING.
 *
 * Matcher note: `save` is a Java Spring Data method (platform parameter
 * type), so plain `any()` is safe there — no Kotlin null-check is inserted
 * for a Java parameter.
 */
@ExtendWith(MockitoExtension::class)
class ResumeDocumentServiceTest {

  companion object {
    private val USER_ID: UUID = UUID.randomUUID()
    private val DOC_ID: UUID = UUID.randomUUID()
    private const val STATE_JSON: String = "{\"summary\":\"Pioneer\"}"
  }

  @Mock
  private lateinit var documents: ResumeDocumentRepository

  @Mock
  private lateinit var templates: TemplateService

  @Mock
  private lateinit var parser: ResumeStateParser

  private val mapper = ObjectMapper()
  private lateinit var service: ResumeDocumentService

  @BeforeEach
  fun setUp() {
    service = ResumeDocumentService(documents, templates, parser, mapper)
    SecurityContextHolder.getContext().authentication =
      UsernamePasswordAuthenticationToken(USER_ID.toString(), null, emptyList<GrantedAuthority>())
  }

  private fun templateDto(id: String): TemplateDto {
    return TemplateDto(id, "Modern", "desc", "<html>{{fullName}}</html>", mapper.createObjectNode())
  }

  private fun doc(): ResumeDocument {
    return ResumeDocument(USER_ID, "Ada Resume", "modern", STATE_JSON)
  }

  @Test
  fun createWithStateSavesAndReturnsIt() {
    Mockito.`when`(templates.getTemplate("modern")).thenReturn(templateDto("modern"))
    Mockito.`when`(documents.save(any<ResumeDocument>()))
      .thenAnswer { inv -> inv.getArgument<ResumeDocument>(0) }
    val state: ObjectNode = mapper.createObjectNode().put("summary", "Pioneer")

    val created: DocumentResponse = service.create(
      CreateDocumentRequest("Ada Resume", "modern", state)
    )

    assertThat(created.title).isEqualTo("Ada Resume")
    assertThat(created.templateId).isEqualTo("modern")
    assertThat(created.state.path("summary").asText()).isEqualTo("Pioneer")
  }

  @Test
  fun createWithoutStateUsesEmptyState() {
    Mockito.`when`(templates.getTemplate("modern")).thenReturn(templateDto("modern"))
    Mockito.`when`(parser.emptyState()).thenReturn(mapper.createObjectNode().put("summary", ""))
    Mockito.`when`(documents.save(any<ResumeDocument>()))
      .thenAnswer { inv -> inv.getArgument<ResumeDocument>(0) }

    val created: DocumentResponse = service.create(
      CreateDocumentRequest(null, "modern", null)
    )

    assertThat(created.title).isEqualTo("Untitled Resume")
    assertThat(created.state.path("summary").asText()).isEmpty()
  }

  @Test
  fun createWithoutTemplateIdIs400WithNoStubs() {
    assertThatThrownBy { service.create(CreateDocumentRequest("x", null, null)) }
      .isInstanceOf(ResponseStatusException::class.java)
      .satisfies { ex ->
        assertThat((ex as ResponseStatusException).statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
      }
  }

  @Test
  fun createWithUnknownTemplateIs404() {
    Mockito.`when`(templates.getTemplate("neon")).thenThrow(
      ResponseStatusException(HttpStatus.NOT_FOUND, "Template not found")
    )
    assertThatThrownBy {
      service.create(CreateDocumentRequest("x", "neon", mapper.createObjectNode()))
    }.isInstanceOf(ResponseStatusException::class.java)
  }

  @Test
  fun getReturnsOwnedDocument() {
    Mockito.`when`(documents.findByIdAndUserId(DOC_ID, USER_ID)).thenReturn(Optional.of(doc()))
    val found: DocumentResponse = service.get(DOC_ID)
    assertThat(found.title).isEqualTo("Ada Resume")
    assertThat(found.state.path("summary").asText()).isEqualTo("Pioneer")
  }

  @Test
  fun getMissingDocumentIs404() {
    Mockito.`when`(documents.findByIdAndUserId(DOC_ID, USER_ID)).thenReturn(Optional.empty())
    assertThatThrownBy { service.get(DOC_ID) }
      .isInstanceOf(ResponseStatusException::class.java)
      .satisfies { ex ->
        assertThat((ex as ResponseStatusException).statusCode).isEqualTo(HttpStatus.NOT_FOUND)
      }
  }

  @Test
  fun updateReplacesStateAndTemplate() {
    Mockito.`when`(documents.findByIdAndUserId(DOC_ID, USER_ID)).thenReturn(Optional.of(doc()))
    Mockito.`when`(templates.getTemplate("classic")).thenReturn(templateDto("classic"))
    Mockito.`when`(documents.save(any<ResumeDocument>()))
      .thenAnswer { inv -> inv.getArgument<ResumeDocument>(0) }
    val newState: ObjectNode = mapper.createObjectNode().put("summary", "Updated")

    val updated: DocumentResponse = service.update(
      DOC_ID,
      UpdateDocumentRequest("Ada v2", "classic", newState)
    )

    assertThat(updated.title).isEqualTo("Ada v2")
    assertThat(updated.templateId).isEqualTo("classic")
    assertThat(updated.state.path("summary").asText()).isEqualTo("Updated")
  }

  @Test
  fun updateWithoutStateIs400WithNoStubs() {
    assertThatThrownBy {
      service.update(DOC_ID, UpdateDocumentRequest("x", null, null))
    }
      .isInstanceOf(ResponseStatusException::class.java)
      .satisfies { ex ->
        assertThat((ex as ResponseStatusException).statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
      }
  }
}
