package com.epitomejob.resumebuilder.service;

import com.epitomejob.resumebuilder.common.constants.AppConstants;
import com.epitomejob.resumebuilder.dto.ResumeBuilderDtos.TemplateDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Template registry: three templates stored as classpath HTML resources
 * (templates/*.html) plus a JSON layout descriptor each. Rendering is a
 * plain placeholder substitution over the filled state — no template
 * engine dependency — with HTML-escaping of every user value.
 */
@Service
public class TemplateService {

  private final ObjectMapper mapper;

  public TemplateService(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  public List<TemplateDto> listTemplates() {
    return AppConstants.Templates.ALL_IDS.stream().map(this::getTemplate).toList();
  }

  public TemplateDto getTemplate(String templateId) {
    if (templateId == null || !AppConstants.Templates.ALL_IDS.contains(templateId)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, AppConstants.Errors.TEMPLATE_NOT_FOUND);
    }
    return switch (templateId) {
      case AppConstants.Templates.MODERN_ID -> new TemplateDto(templateId, "Modern",
          "Clean single-column layout with a coloured header and skill chips.",
          loadHtml(templateId), layout("single-column", "sans-serif", "#1a5276"));
      case AppConstants.Templates.CLASSIC_ID -> new TemplateDto(templateId, "Classic",
          "Traditional serif layout with centred header and ruled sections.",
          loadHtml(templateId), layout("single-column", "serif", "#000000"));
      case AppConstants.Templates.MINIMAL_ID -> new TemplateDto(templateId, "Minimal",
          "Quiet layout with light rules, small caps headings and no colour.",
          loadHtml(templateId), layout("single-column", "sans-serif", "#555555"));
      default -> throw new ResponseStatusException(HttpStatus.NOT_FOUND,
          AppConstants.Errors.TEMPLATE_NOT_FOUND);
    };
  }

  /** Fills a template's HTML with the document state. Unknown/missing fields render empty. */
  public String renderHtml(String templateId, JsonNode state) {
    TemplateDto template = getTemplate(templateId);
    JsonNode contact = state.path("contact");
    String html = template.html()
        .replace("{{fullName}}", escape(contact.path("fullName").asText("")))
        .replace("{{email}}", escape(contact.path("email").asText("")))
        .replace("{{phone}}", escape(contact.path("phone").asText("")))
        .replace("{{linkedin}}", escape(contact.path("linkedin").asText("")))
        .replace("{{location}}", escape(contact.path("location").asText("")))
        .replace("{{summary}}", escape(state.path("summary").asText("")))
        .replace("{{experienceHtml}}", experienceHtml(state.path("experience")))
        .replace("{{educationHtml}}", educationHtml(state.path("education")))
        .replace("{{skillsHtml}}", skillsHtml(state.path("skills")));
    return html;
  }

  private String experienceHtml(JsonNode experience) {
    StringBuilder out = new StringBuilder();
    if (experience.isArray()) {
      for (JsonNode job : experience) {
        out.append("<div class=\"job\"><h3>")
            .append(escape(job.path("title").asText(""))).append(" — ")
            .append(escape(job.path("company").asText(""))).append("</h3><p class=\"dates\">")
            .append(escape(job.path("startDate").asText(""))).append(" – ")
            .append(escape(job.path("endDate").asText(""))).append("</p><ul>");
        JsonNode bullets = job.path("bullets");
        if (bullets.isArray()) {
          for (JsonNode bullet : bullets) {
            out.append("<li>").append(escape(bullet.asText(""))).append("</li>");
          }
        }
        out.append("</ul></div>");
      }
    }
    return out.toString();
  }

  private String educationHtml(JsonNode education) {
    StringBuilder out = new StringBuilder();
    if (education.isArray()) {
      for (JsonNode item : education) {
        out.append("<p>").append(escape(item.path("degree").asText(""))).append(", ")
            .append(escape(item.path("school").asText(""))).append(" (")
            .append(escape(item.path("year").asText(""))).append(")</p>");
      }
    }
    return out.toString();
  }

  private String skillsHtml(JsonNode skills) {
    StringBuilder out = new StringBuilder();
    if (skills.isArray()) {
      for (JsonNode skill : skills) {
        out.append("<span class=\"skill\">").append(escape(skill.asText(""))).append("</span> ");
      }
    }
    return out.toString().trim();
  }

  private ObjectNode layout(String columns, String fontFamily, String accentColor) {
    ObjectNode layout = mapper.createObjectNode();
    layout.put("columns", columns);
    layout.put("fontFamily", fontFamily);
    layout.put("accentColor", accentColor);
    ArrayNode sections = mapper.createArrayNode();
    sections.add("contact");
    sections.add("summary");
    sections.add("experience");
    sections.add("education");
    sections.add("skills");
    layout.set("sections", sections);
    return layout;
  }

  private static String loadHtml(String templateId) {
    String resource = AppConstants.Templates.RESOURCE_PREFIX + templateId
        + AppConstants.Templates.RESOURCE_SUFFIX;
    try (InputStream in = new ClassPathResource(resource).getInputStream()) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException ex) {
      throw new IllegalStateException("Template resource missing: " + resource, ex);
    }
  }

  /** Minimal HTML escaping — no extra dependency for five characters. */
  static String escape(String value) {
    if (value == null) {
      return "";
    }
    return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;").replace("'", "&#39;");
  }
}
