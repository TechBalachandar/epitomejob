package com.epitomejob.resumebuilder.service

import com.epitomejob.resumebuilder.common.constants.AppConstants
import com.epitomejob.resumebuilder.dto.ResumeBuilderDtos.TemplateDto
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import org.springframework.core.io.ClassPathResource
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.io.IOException
import java.nio.charset.StandardCharsets

/**
 * Template registry: three templates stored as classpath HTML resources
 * (one `.html` file each under `templates/`) plus a JSON layout descriptor each. Rendering is a
 * plain placeholder substitution over the filled state — no template
 * engine dependency — with HTML-escaping of every user value.
 */
@Service
class TemplateService(private val mapper: ObjectMapper) {

  fun listTemplates(): List<TemplateDto> {
    return AppConstants.Templates.ALL_IDS.map { getTemplate(it) }
  }

  fun getTemplate(templateId: String?): TemplateDto {
    if (templateId == null || !AppConstants.Templates.ALL_IDS.contains(templateId)) {
      throw ResponseStatusException(HttpStatus.NOT_FOUND, AppConstants.Errors.TEMPLATE_NOT_FOUND)
    }
    return when (templateId) {
      AppConstants.Templates.MODERN_ID -> TemplateDto(templateId, "Modern",
          "Clean single-column layout with a coloured header and skill chips.",
          loadHtml(templateId), layout("single-column", "sans-serif", "#1a5276"))
      AppConstants.Templates.CLASSIC_ID -> TemplateDto(templateId, "Classic",
          "Traditional serif layout with centred header and ruled sections.",
          loadHtml(templateId), layout("single-column", "serif", "#000000"))
      AppConstants.Templates.MINIMAL_ID -> TemplateDto(templateId, "Minimal",
          "Quiet layout with light rules, small caps headings and no colour.",
          loadHtml(templateId), layout("single-column", "sans-serif", "#555555"))
      else -> throw ResponseStatusException(HttpStatus.NOT_FOUND,
          AppConstants.Errors.TEMPLATE_NOT_FOUND)
    }
  }

  /** Fills a template's HTML with the document state. Unknown/missing fields render empty. */
  fun renderHtml(templateId: String, state: JsonNode): String {
    val template = getTemplate(templateId)
    val contact = state.path("contact")
    return template.html
        .replace("{{fullName}}", escape(contact.path("fullName").asText("")))
        .replace("{{email}}", escape(contact.path("email").asText("")))
        .replace("{{phone}}", escape(contact.path("phone").asText("")))
        .replace("{{linkedin}}", escape(contact.path("linkedin").asText("")))
        .replace("{{location}}", escape(contact.path("location").asText("")))
        .replace("{{summary}}", escape(state.path("summary").asText("")))
        .replace("{{experienceHtml}}", experienceHtml(state.path("experience")))
        .replace("{{educationHtml}}", educationHtml(state.path("education")))
        .replace("{{skillsHtml}}", skillsHtml(state.path("skills")))
  }

  private fun experienceHtml(experience: JsonNode): String {
    val out = StringBuilder()
    if (experience.isArray) {
      for (job in experience) {
        out.append("<div class=\"job\"><h3>")
            .append(escape(job.path("title").asText(""))).append(" — ")
            .append(escape(job.path("company").asText(""))).append("</h3><p class=\"dates\">")
            .append(escape(job.path("startDate").asText(""))).append(" – ")
            .append(escape(job.path("endDate").asText(""))).append("</p><ul>")
        val bullets = job.path("bullets")
        if (bullets.isArray) {
          for (bullet in bullets) {
            out.append("<li>").append(escape(bullet.asText(""))).append("</li>")
          }
        }
        out.append("</ul></div>")
      }
    }
    return out.toString()
  }

  private fun educationHtml(education: JsonNode): String {
    val out = StringBuilder()
    if (education.isArray) {
      for (item in education) {
        out.append("<p>").append(escape(item.path("degree").asText(""))).append(", ")
            .append(escape(item.path("school").asText(""))).append(" (")
            .append(escape(item.path("year").asText(""))).append(")</p>")
      }
    }
    return out.toString()
  }

  private fun skillsHtml(skills: JsonNode): String {
    val out = StringBuilder()
    if (skills.isArray) {
      for (skill in skills) {
        out.append("<span class=\"skill\">").append(escape(skill.asText(""))).append("</span> ")
      }
    }
    return out.toString().trim()
  }

  private fun layout(columns: String, fontFamily: String, accentColor: String): ObjectNode {
    val layout = mapper.createObjectNode()
    layout.put("columns", columns)
    layout.put("fontFamily", fontFamily)
    layout.put("accentColor", accentColor)
    val sections = mapper.createArrayNode()
    sections.add("contact")
    sections.add("summary")
    sections.add("experience")
    sections.add("education")
    sections.add("skills")
    layout.set("sections", sections)
    return layout
  }

  private fun loadHtml(templateId: String): String {
    val resource = AppConstants.Templates.RESOURCE_PREFIX + templateId +
        AppConstants.Templates.RESOURCE_SUFFIX
    try {
      ClassPathResource(resource).inputStream.use { input ->
        return String(input.readAllBytes(), StandardCharsets.UTF_8)
      }
    } catch (ex: IOException) {
      throw IllegalStateException("Template resource missing: $resource", ex)
    }
  }

  companion object {
    /** Minimal HTML escaping — no extra dependency for five characters. */
    @JvmStatic
    fun escape(value: String?): String {
      if (value == null) {
        return ""
      }
      return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
          .replace("\"", "&quot;").replace("'", "&#39;")
    }
  }
}
