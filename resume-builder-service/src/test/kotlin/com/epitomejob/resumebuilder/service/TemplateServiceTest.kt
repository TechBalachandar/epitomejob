package com.epitomejob.resumebuilder.service

import com.epitomejob.resumebuilder.dto.ResumeBuilderDtos.TemplateDto
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import org.junit.jupiter.api.Test
import org.springframework.web.server.ResponseStatusException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy

class TemplateServiceTest {

  private val mapper = ObjectMapper()
  private val service = TemplateService(mapper)

  private fun sampleState(): ObjectNode {
    val state = ObjectMapper().createObjectNode()
    val contact = mapper.createObjectNode()
    contact.put("fullName", "Ada <script>Lovelace</script>")
    contact.put("email", "ada@example.com")
    contact.put("phone", "555-1234")
    contact.put("linkedin", "linkedin.com/in/adalovelace")
    contact.put("location", "London")
    state.set("contact", contact)
    state.put("summary", "Pioneer & builder")
    state.set("experience", mapper.createArrayNode())
    state.set("education", mapper.createArrayNode())
    state.set("skills", mapper.createArrayNode())
    return state
  }

  @Test
  fun listsThreeTemplates() {
    assertThat(service.listTemplates()).hasSize(3)
    assertThat(service.listTemplates()).extracting { it.id }
      .containsExactlyInAnyOrder("modern", "classic", "minimal")
  }

  @Test
  fun eachTemplateHasHtmlAndLayout() {
    for (template: TemplateDto in service.listTemplates()) {
      assertThat(template.name).isNotBlank()
      assertThat(template.description).isNotBlank()
      assertThat(template.html).contains("{{fullName}}")
      assertThat(template.layout.path("sections")).hasSize(5)
    }
  }

  @Test
  fun unknownTemplateIs404() {
    assertThatThrownBy { service.getTemplate("neon") }
      .isInstanceOf(ResponseStatusException::class.java)
  }

  @Test
  fun renderSubstitutesAndEscapesUserValues() {
    val html = service.renderHtml("modern", sampleState())
    assertThat(html).contains("Ada &lt;script&gt;Lovelace&lt;/script&gt;")
    assertThat(html).doesNotContain("<script>Lovelace</script>")
    assertThat(html).contains("Pioneer &amp; builder")
    assertThat(html).doesNotContain("{{fullName}}")
  }
}
