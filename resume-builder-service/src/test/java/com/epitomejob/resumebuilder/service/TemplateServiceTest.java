package com.epitomejob.resumebuilder.service;

import com.epitomejob.resumebuilder.dto.ResumeBuilderDtos.TemplateDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TemplateServiceTest {

  private final ObjectMapper mapper = new ObjectMapper();
  private final TemplateService service = new TemplateService(mapper);

  private ObjectNode sampleState() {
    ObjectNode state = new ObjectMapper().createObjectNode();
    ObjectNode contact = mapper.createObjectNode();
    contact.put("fullName", "Ada <script>Lovelace</script>");
    contact.put("email", "ada@example.com");
    contact.put("phone", "555-1234");
    contact.put("linkedin", "linkedin.com/in/adalovelace");
    contact.put("location", "London");
    state.set("contact", contact);
    state.put("summary", "Pioneer & builder");
    state.set("experience", mapper.createArrayNode());
    state.set("education", mapper.createArrayNode());
    state.set("skills", mapper.createArrayNode());
    return state;
  }

  @Test
  void listsThreeTemplates() {
    assertThat(service.listTemplates()).hasSize(3);
    assertThat(service.listTemplates()).extracting(TemplateDto::id)
        .containsExactlyInAnyOrder("modern", "classic", "minimal");
  }

  @Test
  void eachTemplateHasHtmlAndLayout() {
    for (TemplateDto template : service.listTemplates()) {
      assertThat(template.name()).isNotBlank();
      assertThat(template.description()).isNotBlank();
      assertThat(template.html()).contains("{{fullName}}");
      assertThat(template.layout().path("sections")).hasSize(5);
    }
  }

  @Test
  void unknownTemplateIs404() {
    assertThatThrownBy(() -> service.getTemplate("neon"))
        .isInstanceOf(ResponseStatusException.class);
  }

  @Test
  void renderSubstitutesAndEscapesUserValues() {
    String html = service.renderHtml("modern", sampleState());
    assertThat(html).contains("Ada &lt;script&gt;Lovelace&lt;/script&gt;");
    assertThat(html).doesNotContain("<script>Lovelace</script>");
    assertThat(html).contains("Pioneer &amp; builder");
    assertThat(html).doesNotContain("{{fullName}}");
  }
}
