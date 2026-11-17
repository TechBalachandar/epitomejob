package com.epitomejob.resumebuilder.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import org.junit.jupiter.api.Test
import org.assertj.core.api.Assertions.assertThat

class ResumeStateParserTest {

  // Note: Java text blocks end with a trailing newline; Kotlin
  // trimIndent() drops it. No assertion depends on the trailing newline
  // (the rawText assertion compares against SAMPLE itself).
  private val SAMPLE: String = """
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
      """.trimIndent()

  private val parser = ResumeStateParser(ObjectMapper())

  @Test
  fun parsesContactSummaryExperienceEducationAndSkills() {
    val state: ObjectNode = parser.parse(SAMPLE)

    assertThat(state.path("contact").path("fullName").asText()).isEqualTo("Ada Lovelace")
    assertThat(state.path("contact").path("email").asText()).isEqualTo("ada@example.com")
    assertThat(state.path("contact").path("phone").asText()).contains("555")
    assertThat(state.path("contact").path("linkedin").asText())
      .contains("linkedin.com/in/adalovelace")
    assertThat(state.path("summary").asText()).contains("Analytical engine pioneer")
    assertThat(state.path("experience")).hasSize(2)
    assertThat(state.path("experience").get(0).path("title").asText()).isEqualTo("Senior Engineer")
    assertThat(state.path("experience").get(0).path("company").asText())
      .isEqualTo("Analytical Engines Ltd")
    assertThat(state.path("experience").get(0).path("startDate").asText()).isEqualTo("Jan 2020")
    assertThat(state.path("experience").get(0).path("endDate").asText()).isEqualTo("Present")
    assertThat(state.path("experience").get(0).path("bullets")).hasSize(2)
    assertThat(state.path("experience").get(1).path("company").asText())
      .isEqualTo("Difference Engine Co")
    assertThat(state.path("education")).hasSize(1)
    assertThat(state.path("education").get(0).path("degree").asText()).isEqualTo("BSc Mathematics")
    assertThat(state.path("education").get(0).path("school").asText())
      .isEqualTo("University of London")
    assertThat(state.path("education").get(0).path("year").asText()).isEqualTo("2015")
    assertThat(state.path("skills").toString()).contains("Java", "Spring Boot", "Python")
  }

  @Test
  fun rawTextAlwaysPreservesTheFullInput() {
    val state: ObjectNode = parser.parse(SAMPLE)
    assertThat(state.path("rawText").asText()).isEqualTo(SAMPLE)
  }

  @Test
  fun unparseableTextStillProducesAStateWithRawText() {
    val state: ObjectNode = parser.parse("just some words\nwith no structure at all\n")
    assertThat(state.path("contact").path("fullName").asText()).isEmpty()
    assertThat(state.path("experience")).isEmpty()
    assertThat(state.path("rawText").asText()).contains("just some words")
  }

  @Test
  fun companyFirstHeaderIsNotSwapped() {
    // Resumes often list the company first: "Company | Title | Location |
    // Dates". The parser must not put the company in the title field and
    // the title (plus location) in the company field (issue #12).
    val text: String = """
        Balachandar Jeldi
        balachandar@example.com
        EXPERIENCE
        Zillion Technologies/Navy Federal | Lead Engineer | Remote | Jan 2021 – Present
        • Built mobile banking apps
        Junior Engineer | Difference Engine Co 2018 - 2019
        • Built prototype gears
        """.trimIndent()
    val state: ObjectNode = parser.parse(text)

    assertThat(state.path("experience")).hasSize(2)
    assertThat(state.path("experience").get(0).path("title").asText())
      .isEqualTo("Lead Engineer")
    assertThat(state.path("experience").get(0).path("company").asText())
      .isEqualTo("Zillion Technologies/Navy Federal")
    assertThat(state.path("experience").get(0).path("startDate").asText())
      .isEqualTo("Jan 2021")
    assertThat(state.path("experience").get(0).path("endDate").asText())
      .isEqualTo("Present")
    // Title-first headers still parse the old way.
    assertThat(state.path("experience").get(1).path("title").asText())
      .isEqualTo("Junior Engineer")
    assertThat(state.path("experience").get(1).path("company").asText())
      .isEqualTo("Difference Engine Co")
  }

  @Test
  fun executiveSummaryHeadingIsNotTheContactName() {
    // A name on a "Name | Title" line plus an "EXECUTIVE SUMMARY" heading:
    // the name must come from the first segment of the pipe line, and the
    // heading must be treated as the summary heading — never as the name.
    val text: String = """
        Balachandar Jeldi | Senior Software Engineer
        balachandar@example.com | (980) 425-3125
        Reston, VA
        EXECUTIVE SUMMARY
        Seasoned Android Engineer with 16+ years of experience.
        EXPERIENCE
        Lead Engineer at Zillion Technologies Jan 2021 – Present
        • Built mobile banking apps
        """.trimIndent()
    val state: ObjectNode = parser.parse(text)

    assertThat(state.path("contact").path("fullName").asText())
      .isEqualTo("Balachandar Jeldi")
    assertThat(state.path("contact").path("fullName").asText())
      .isNotEqualTo("EXECUTIVE SUMMARY")
    assertThat(state.path("summary").asText()).contains("Seasoned Android Engineer")
    assertThat(state.path("experience")).hasSize(1)
    assertThat(state.path("experience").get(0).path("title").asText())
      .isEqualTo("Lead Engineer")
  }

  @Test
  fun emptyStateHasTheFullShape() {
    val state: ObjectNode = parser.emptyState()
    assertThat(state.path("contact").path("fullName").asText()).isEmpty()
    assertThat(state.path("summary").asText()).isEmpty()
    assertThat(state.path("experience")).isEmpty()
    assertThat(state.path("education")).isEmpty()
    assertThat(state.path("skills")).isEmpty()
    assertThat(state.path("rawText").asText()).isEmpty()
  }

  @Test
  fun allHeadingVariantsAreRecognised() {
    // Every heading variant in Section.headingOf must map to its section.
    // A heading alone is not enough for EXPERIENCE (needs a job header),
    // EDUCATION (needs a comma line) or SKILLS (needs a list line), so
    // each variant is followed by a marker line for its section.
    val summaryVariants = listOf("SUMMARY", "PROFILE", "OBJECTIVE", "ABOUT",
        "EXECUTIVE SUMMARY", "PROFESSIONAL SUMMARY", "CAREER SUMMARY",
        "PROFESSIONAL PROFILE", "CAREER PROFILE",
        "PROFESSIONAL OBJECTIVE", "CAREER OBJECTIVE")
    for (heading in summaryVariants) {
      val state = parser.parse("Ada Lovelace\nada@example.com\n$heading\nSeasoned engineer here.\n")
      assertThat(state.path("summary").asText()).contains("Seasoned engineer")
    }
    val experienceVariants = listOf("EXPERIENCE", "WORK EXPERIENCE", "WORK HISTORY",
        "EMPLOYMENT", "PROFESSIONAL EXPERIENCE", "EMPLOYMENT HISTORY")
    for (heading in experienceVariants) {
      val state = parser.parse(
          "Ada Lovelace\nada@example.com\n$heading\nEngineer at Acme Jan 2020 – Present\n• Did things\n")
      assertThat(state.path("experience")).hasSize(1)
      assertThat(state.path("experience").get(0).path("title").asText()).isEqualTo("Engineer")
    }
    val educationVariants = listOf("EDUCATION", "EDUCATION & CERTIFICATIONS",
        "EDUCATION AND CERTIFICATIONS")
    for (heading in educationVariants) {
      val state = parser.parse(
          "Ada Lovelace\nada@example.com\n$heading\nBSc Maths, University, 2015\n")
      assertThat(state.path("education")).hasSize(1)
    }
    val skillsVariants = listOf("SKILLS", "TECHNICAL SKILLS", "CORE SKILLS",
        "SKILLS & TECHNOLOGIES")
    for (heading in skillsVariants) {
      val state = parser.parse("Ada Lovelace\nada@example.com\n$heading\nJava, Python\n")
      assertThat(state.path("skills").toString()).contains("Java")
    }
    // Colon suffix and mixed case are normalised before matching.
    val colon = parser.parse("Ada Lovelace\nada@example.com\nExperience:\nEngineer at Acme Jan 2020 – Present\n")
    assertThat(colon.path("experience")).hasSize(1)
    val lower = parser.parse("Ada Lovelace\nada@example.com\nexperience\nEngineer at Acme Jan 2020 – Present\n")
    assertThat(lower.path("experience")).hasSize(1)
    // A line that is not a heading maps to no section.
    val plain = parser.parse("Ada Lovelace\nada@example.com\nJust a regular line\nMore text\n")
    assertThat(plain.path("summary").asText()).isEmpty()
    assertThat(plain.path("experience")).isEmpty()
  }

  @Test
  fun contactParsingEdgeCases() {
    // A name with digits and a lowercase name are not names; a line with
    // a comma and no year is a location; scanning stops after 8 lines.
    val text = """
        Ada Lovelace
        ada@example.com
        123 Main Street
        just some words here
        London, UK
        SUMMARY
        Hello.
        """.trimIndent()
    val state = parser.parse(text)
    assertThat(state.path("contact").path("fullName").asText()).isEqualTo("Ada Lovelace")
    assertThat(state.path("contact").path("location").asText()).isEqualTo("London, UK")
    // More than 8 lines before any heading: scanning stops, later lines ignored.
    val manyLines = buildString {
      append("Ada Lovelace\n")
      for (i in 1..10) append("Filler line number $i\n")
      append("ada@example.com\n")
    }
    val late = parser.parse(manyLines)
    assertThat(late.path("contact").path("email").asText()).isEmpty()
  }

  @Test
  fun experienceContinuationLinesAndBulletBeforeAnyJob() {
    // A bullet before any job header is dropped; a non-bullet line under a
    // job is kept as a bullet; a job header via " | " and via " — " both work.
    val text = """
        Ada Lovelace
        ada@example.com
        EXPERIENCE
        • Orphan bullet with no job yet
        Senior Engineer | Acme Corp | Jan 2020 – Present
        A continuation line under the job
        • A real bullet
        Lead Engineer — Beta Ltd Jan 2021 – Present
        • Another bullet
        """.trimIndent()
    val state = parser.parse(text)
    assertThat(state.path("experience")).hasSize(2)
    val first = state.path("experience").get(0)
    assertThat(first.path("bullets").toString())
        .contains("A continuation line under the job", "A real bullet")
    assertThat(first.path("bullets").toString()).doesNotContain("Orphan bullet")
    assertThat(state.path("experience").get(1).path("title").asText())
      .isEqualTo("Lead Engineer")
  }
}
