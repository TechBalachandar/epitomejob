package com.epitomejob.resumebuilder.service;

import com.epitomejob.resumebuilder.dto.ResumeBuilderDtos.CreateDocumentRequest;
import com.epitomejob.resumebuilder.dto.ResumeBuilderDtos.DocumentResponse;
import com.epitomejob.resumebuilder.dto.ResumeBuilderDtos.TemplateDto;
import com.epitomejob.resumebuilder.dto.ResumeBuilderDtos.UpdateDocumentRequest;
import com.epitomejob.resumebuilder.model.ResumeDocument;
import com.epitomejob.resumebuilder.repository.ResumeDocumentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * STUB TRACE (Nov 13 rule): every when(...) below is reachable on its
 * test's code path — create calls getTemplate then save; get/update call
 * findByIdAndUserId (update also getTemplate + save); the validation-first
 * tests stub NOTHING.
 */
@ExtendWith(MockitoExtension.class)
class ResumeDocumentServiceTest {

  private static final UUID USER_ID = UUID.randomUUID();
  private static final UUID DOC_ID = UUID.randomUUID();
  private static final String STATE_JSON = "{\"summary\":\"Pioneer\"}";

  @Mock
  private ResumeDocumentRepository documents;
  @Mock
  private TemplateService templates;
  @Mock
  private ResumeStateParser parser;

  private final ObjectMapper mapper = new ObjectMapper();
  private ResumeDocumentService service;

  @BeforeEach
  void setUp() {
    service = new ResumeDocumentService(documents, templates, parser, mapper);
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken(USER_ID.toString(), null, List.of()));
  }

  private TemplateDto templateDto(String id) {
    return new TemplateDto(id, "Modern", "desc", "<html>{{fullName}}</html>",
        mapper.createObjectNode());
  }

  private ResumeDocument doc() {
    return new ResumeDocument(USER_ID, "Ada Resume", "modern", STATE_JSON);
  }

  @Test
  void createWithStateSavesAndReturnsIt() {
    when(templates.getTemplate("modern")).thenReturn(templateDto("modern"));
    when(documents.save(any())).thenAnswer(inv -> inv.getArgument(0));
    ObjectNode state = mapper.createObjectNode().put("summary", "Pioneer");

    DocumentResponse created = service.create(
        new CreateDocumentRequest("Ada Resume", "modern", state));

    assertThat(created.title()).isEqualTo("Ada Resume");
    assertThat(created.templateId()).isEqualTo("modern");
    assertThat(created.state().path("summary").asText()).isEqualTo("Pioneer");
  }

  @Test
  void createWithoutStateUsesEmptyState() {
    when(templates.getTemplate("modern")).thenReturn(templateDto("modern"));
    when(parser.emptyState()).thenReturn(mapper.createObjectNode().put("summary", ""));
    when(documents.save(any())).thenAnswer(inv -> inv.getArgument(0));

    DocumentResponse created = service.create(
        new CreateDocumentRequest(null, "modern", null));

    assertThat(created.title()).isEqualTo("Untitled Resume");
    assertThat(created.state().path("summary").asText()).isEmpty();
  }

  @Test
  void createWithoutTemplateIdIs400WithNoStubs() {
    assertThatThrownBy(() -> service.create(new CreateDocumentRequest("x", null, null)))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST));
  }

  @Test
  void createWithUnknownTemplateIs404() {
    when(templates.getTemplate("neon")).thenThrow(
        new ResponseStatusException(HttpStatus.NOT_FOUND, "Template not found"));
    assertThatThrownBy(() -> service.create(
        new CreateDocumentRequest("x", "neon", mapper.createObjectNode())))
        .isInstanceOf(ResponseStatusException.class);
  }

  @Test
  void getReturnsOwnedDocument() {
    when(documents.findByIdAndUserId(DOC_ID, USER_ID)).thenReturn(Optional.of(doc()));
    DocumentResponse found = service.get(DOC_ID);
    assertThat(found.title()).isEqualTo("Ada Resume");
    assertThat(found.state().path("summary").asText()).isEqualTo("Pioneer");
  }

  @Test
  void getMissingDocumentIs404() {
    when(documents.findByIdAndUserId(DOC_ID, USER_ID)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.get(DOC_ID))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND));
  }

  @Test
  void updateReplacesStateAndTemplate() {
    when(documents.findByIdAndUserId(DOC_ID, USER_ID)).thenReturn(Optional.of(doc()));
    when(templates.getTemplate("classic")).thenReturn(templateDto("classic"));
    when(documents.save(any())).thenAnswer(inv -> inv.getArgument(0));
    ObjectNode newState = mapper.createObjectNode().put("summary", "Updated");

    DocumentResponse updated = service.update(DOC_ID,
        new UpdateDocumentRequest("Ada v2", "classic", newState));

    assertThat(updated.title()).isEqualTo("Ada v2");
    assertThat(updated.templateId()).isEqualTo("classic");
    assertThat(updated.state().path("summary").asText()).isEqualTo("Updated");
  }

  @Test
  void updateWithoutStateIs400WithNoStubs() {
    assertThatThrownBy(() -> service.update(DOC_ID,
        new UpdateDocumentRequest("x", null, null)))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST));
  }
}
