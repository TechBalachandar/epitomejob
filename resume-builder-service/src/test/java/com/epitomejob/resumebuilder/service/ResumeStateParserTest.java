package com.epitomejob.resumebuilder.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ResumeStateParserTest {

  private static final String SAMPLE = """
      Ada Lovelace
      ada@example.com | (555) 123-4567 | linkedin.com/in/adalovelace
      London, UK
      SUMMARY
      Analytical engine pioneer with 10 years of experience building computing machines.
      EXPERIENCE
      Senior Engineer at Analytical Engines Ltd Jan 2020 – Present
      • Designed the first algorithm for a machine
      • Led a team of 4 engineers
      Junior Engineer | Difference Engine Co 2018 - 2019
      • Built prototype gears
      EDUCATION
      BSc Mathematics, University of London, 2015
      SKILLS
      Java, Spring Boot, Kotlin • Python, SQL
      """;

  private final ResumeStateParser parser = new ResumeStateParser(new ObjectMapper());

  @Test
  void parsesContactSummaryExperienceEducationAndSkills() {
    ObjectNode state = parser.parse(SAMPLE);

    assertThat(state.path("contact").path("fullName").asText()).isEqualTo("Ada Lovelace");
    assertThat(state.path("contact").path("email").asText()).isEqualTo("ada@example.com");
    assertThat(state.path("contact").path("phone").asText()).contains("555");
    assertThat(state.path("contact").path("linkedin").asText()).contains("linkedin.com/in/adalovelace");
    assertThat(state.path("summary").asText()).contains("Analytical engine pioneer");
    assertThat(state.path("experience")).hasSize(2);
    assertThat(state.path("experience").get(0).path("title").asText()).isEqualTo("Senior Engineer");
    assertThat(state.path("experience").get(0).path("company").asText()).isEqualTo("Analytical Engines Ltd");
    assertThat(state.path("experience").get(0).path("startDate").asText()).isEqualTo("Jan 2020");
    assertThat(state.path("experience").get(0).path("endDate").asText()).isEqualTo("Present");
    assertThat(state.path("experience").get(0).path("bullets")).hasSize(2);
    assertThat(state.path("experience").get(1).path("company").asText()).isEqualTo("Difference Engine Co");
    assertThat(state.path("education")).hasSize(1);
    assertThat(state.path("education").get(0).path("degree").asText()).isEqualTo("BSc Mathematics");
    assertThat(state.path("education").get(0).path("school").asText()).isEqualTo("University of London");
    assertThat(state.path("education").get(0).path("year").asText()).isEqualTo("2015");
    assertThat(state.path("skills").toString()).contains("Java", "Spring Boot", "Python");
  }

  @Test
  void rawTextAlwaysPreservesTheFullInput() {
    ObjectNode state = parser.parse(SAMPLE);
    assertThat(state.path("rawText").asText()).isEqualTo(SAMPLE);
  }

  @Test
  void unparseableTextStillProducesAStateWithRawText() {
    ObjectNode state = parser.parse("just some words\nwith no structure at all\n");
    assertThat(state.path("contact").path("fullName").asText()).isEmpty();
    assertThat(state.path("experience")).isEmpty();
    assertThat(state.path("rawText").asText()).contains("just some words");
  }

  @Test
  void companyFirstHeaderIsNotSwapped() {
    // Resumes often list the company first: "Company | Title | Location |
    // Dates". The parser must not put the company in the title field and
    // the title (plus location) in the company field (issue #12).
    String text = """
        Balachandar Jeldi
        balachandar@example.com
        EXPERIENCE
        Zillion Technologies/Navy Federal | Lead Engineer | Remote | Jan 2021 – Present
        • Built mobile banking apps
        Junior Engineer | Difference Engine Co 2018 - 2019
        • Built prototype gears
        """;
    ObjectNode state = parser.parse(text);

    assertThat(state.path("experience")).hasSize(2);
    assertThat(state.path("experience").get(0).path("title").asText())
        .isEqualTo("Lead Engineer");
    assertThat(state.path("experience").get(0).path("company").asText())
        .isEqualTo("Zillion Technologies/Navy Federal");
    assertThat(state.path("experience").get(0).path("startDate").asText())
        .isEqualTo("Jan 2021");
    assertThat(state.path("experience").get(0).path("endDate").asText())
        .isEqualTo("Present");
    // Title-first headers still parse the old way.
    assertThat(state.path("experience").get(1).path("title").asText())
        .isEqualTo("Junior Engineer");
    assertThat(state.path("experience").get(1).path("company").asText())
        .isEqualTo("Difference Engine Co");
  }

  @Test
  void executiveSummaryHeadingIsNotTheContactName() {
    // A name on a "Name | Title" line plus an "EXECUTIVE SUMMARY" heading:
    // the name must come from the first segment of the pipe line, and the
    // heading must be treated as the summary heading — never as the name.
    String text = """
        Balachandar Jeldi | Senior Software Engineer
        balachandar@example.com | (980) 425-3125
        Reston, VA
        EXECUTIVE SUMMARY
        Seasoned Android Engineer with 16+ years of experience.
        EXPERIENCE
        Lead Engineer at Zillion Technologies Jan 2021 – Present
        • Built mobile banking apps
        """;
    ObjectNode state = parser.parse(text);

    assertThat(state.path("contact").path("fullName").asText())
        .isEqualTo("Balachandar Jeldi");
    assertThat(state.path("contact").path("fullName").asText())
        .isNotEqualTo("EXECUTIVE SUMMARY");
    assertThat(state.path("summary").asText()).contains("Seasoned Android Engineer");
    assertThat(state.path("experience")).hasSize(1);
    assertThat(state.path("experience").get(0).path("title").asText())
        .isEqualTo("Lead Engineer");
  }

  @Test
  void emptyStateHasTheFullShape() {
    ObjectNode state = parser.emptyState();
    assertThat(state.path("contact").path("fullName").asText()).isEmpty();
    assertThat(state.path("summary").asText()).isEmpty();
    assertThat(state.path("experience")).isEmpty();
    assertThat(state.path("education")).isEmpty();
    assertThat(state.path("skills")).isEmpty();
    assertThat(state.path("rawText").asText()).isEmpty();
  }
}
