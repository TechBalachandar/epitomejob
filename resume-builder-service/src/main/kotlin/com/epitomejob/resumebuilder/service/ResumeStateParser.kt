package com.epitomejob.resumebuilder.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import org.springframework.stereotype.Service
import java.util.regex.Pattern

/**
 * Best-effort structured extraction from plain resume text. Anything that
 * cannot be structured is preserved verbatim in `rawText` — nothing
 * the user uploaded is ever silently dropped.
 *
 * Heuristics (line-based, deterministic — no LLM): the contact block is
 * read from the top lines; sections are found by heading lines
 * (SUMMARY / EXPERIENCE / EDUCATION / SKILLS and common variants);
 * experience items start at a line carrying a date range or a
 * "Title at Company" / "Title | Company" header, with •/-/* lines as
 * bullets; education lines split on commas; skills split on commas,
 * semicolons and bullets.
 */
@Service
class ResumeStateParser(private val mapper: ObjectMapper) {

  /** Parses raw text into the resume-state JSON shape (see README §State). */
  fun parse(rawText: String?): ObjectNode {
    val text = rawText ?: ""
    val lines = text.lines().map { it.trim() }
    val state = mapper.createObjectNode()
    state.set("contact", parseContact(lines))
    state.put("summary", parseSectionText(lines, Section.SUMMARY))
    state.set("experience", parseExperience(lines))
    state.set("education", parseEducation(lines))
    state.set("skills", parseSkills(lines))
    state.put("rawText", text)
    return state
  }

  /** An empty state for a blank document (POST /documents without state). */
  fun emptyState(): ObjectNode {
    val state = mapper.createObjectNode()
    val contact = mapper.createObjectNode()
    contact.put("fullName", "")
    contact.put("email", "")
    contact.put("phone", "")
    contact.put("linkedin", "")
    contact.put("location", "")
    state.set("contact", contact)
    state.put("summary", "")
    state.set("experience", mapper.createArrayNode())
    state.set("education", mapper.createArrayNode())
    state.set("skills", mapper.createArrayNode())
    state.put("rawText", "")
    return state
  }

  private fun parseContact(lines: List<String>): ObjectNode {
    val contact = mapper.createObjectNode()
    var name = ""
    var email = ""
    var phone = ""
    var linkedin = ""
    var location = ""
    var scanned = 0
    for (line in lines) {
      if (line.isEmpty()) {
        continue
      }
      if (scanned >= 8 || Section.headingOf(line) != null) {
        break
      }
      scanned++
      val emailMatcher = EMAIL.matcher(line)
      if (email.isEmpty() && emailMatcher.find()) {
        email = emailMatcher.group()
      }
      val phoneMatcher = PHONE.matcher(line)
      if (phone.isEmpty() && phoneMatcher.find()) {
        phone = phoneMatcher.group().trim()
      }
      val linkedinMatcher = LINKEDIN.matcher(line)
      if (linkedin.isEmpty() && linkedinMatcher.find()) {
        linkedin = linkedinMatcher.group(1)
      }
      // A name may sit on a "Name | Title | Location" line: only the first
      // segment (before the first "|") is a name candidate. Without this,
      // a name on such a line is skipped and a later heading ("EXECUTIVE
      // SUMMARY" — 2 capitalised words) is misread as the name.
      val nameCandidate = if (line.contains("|")) {
        line.substring(0, line.indexOf('|')).trim()
      } else {
        line
      }
      if (name.isEmpty() && !EMAIL.matcher(nameCandidate).find()
          && !nameCandidate.contains("@") && !nameCandidate.lowercase().contains("linkedin")
          && nameCandidate.none { it.isDigit() }
          && nameCandidate.split("\\s+".toRegex()).size in 2..5
          // A name is a proper noun: every word capitalised. Without this,
          // any 2-5 word lowercase line ("just some words") is misread as
          // the contact name and unstructured text looks structured.
          && nameCandidate.split("\\s+".toRegex())
              .all { w -> w.isNotEmpty() && w[0].isUpperCase() }) {
        name = nameCandidate
      }
      if (location.isEmpty() && line.contains(",") && !line.contains("@")
          && !YEAR.matcher(line).find() && name != line
          && line.split(",").size == 2 && line.length <= 40) {
        location = line
      }
    }
    contact.put("fullName", name)
    contact.put("email", email)
    contact.put("phone", phone)
    contact.put("linkedin", linkedin)
    contact.put("location", location)
    return contact
  }

  private fun parseSectionText(lines: List<String>, wanted: Section): String {
    val section = sectionLines(lines, wanted)
    return section.joinToString(" ").trim()
  }

  private fun parseExperience(lines: List<String>): ArrayNode {
    val items = mapper.createArrayNode()
    val section = sectionLines(lines, Section.EXPERIENCE)
    var current: ObjectNode? = null
    for (rawLine in section) {
      val line = rawLine.trim()
      if (line.isEmpty()) {
        continue
      }
      val bullet = BULLET.matcher(line)
      if (bullet.matches() && current != null) {
        (current.get("bullets") as ArrayNode).add(bullet.group(1).trim())
        continue
      }
      if (isJobHeader(line)) {
        current = newExperienceItem(line)
        items.add(current)
        continue
      }
      if (current != null) {
        // A non-bullet continuation line under a job: keep it as a bullet
        // so the text survives in the structured state too.
        (current.get("bullets") as ArrayNode).add(line)
      }
    }
    return items
  }

  private fun isJobHeader(line: String): Boolean {
    if (DATE_RANGE.matcher(line).find()) {
      return true
    }
    val lower = line.lowercase()
    return (lower.contains(" at ") || line.contains(" | ") || line.contains(" — "))
        && line.split("\\s+".toRegex()).size >= 3 && line.length <= 120
  }

  private fun newExperienceItem(header: String): ObjectNode {
    val item = mapper.createObjectNode()
    var title = header
    var company = ""
    var startDate = ""
    var endDate = ""
    val dates = DATE_RANGE.matcher(header)
    if (dates.find()) {
      startDate = dates.group(1)
      endDate = dates.group(2)
      title = header.substring(0, dates.start()).trim()
          .replace("[,|\\-–—]+\$".toRegex(), "").trim()
    }
    if (title.contains(" | ")) {
      val parts = splitTitleCompany(title, "\\s*\\|\\s*")
      title = parts[0]
      company = parts[1]
    } else if (title.lowercase().contains(" at ")) {
      val parts = title.split("(?i)\\s+at\\s+".toRegex(), limit = 2)
      title = parts[0].trim()
      company = parts[1].trim()
    } else if (title.contains(" — ")) {
      val parts = splitTitleCompany(title, "\\s+—\\s+")
      title = parts[0]
      company = parts[1]
    }
    item.put("title", title)
    item.put("company", company)
    item.put("startDate", startDate)
    item.put("endDate", endDate)
    item.set("bullets", mapper.createArrayNode())
    return item
  }

  /**
   * Split a "A | B | ..." (or "A — B — ...") header into {title, company}.
   * The header may be title-first ("Junior Engineer | Difference Engine Co")
   * or company-first ("Zillion Technologies/Navy Federal | Lead Engineer").
   * Score the first two segments: swap when the first looks like a company
   * and the second looks like a title. Further segments (location, dates
   * the range regex missed) are dropped — the state has no field for them,
   * and lumping them into the company is what produced the swapped fields.
   * A location segment ("Remote", "Vienna, VA") is never a company.
   */
  private fun splitTitleCompany(head: String, separatorRegex: String): Array<String> {
    val parts = head.split(separatorRegex.toRegex())
    val first = parts[0].trim()
    val second = if (parts.size > 1) parts[1].trim() else ""
    val companyFirst = companyScore(first) > 0
        && titleScore(second) > titleScore(first)
        && companyScore(first) >= companyScore(second)
    val titlePart = if (companyFirst) second else first
    var companyPart = if (companyFirst) first else second
    if (looksLikeLocation(companyPart)) {
      companyPart = ""
    }
    return arrayOf(titlePart, companyPart)
  }

  private fun companyScore(segment: String): Int {
    var score = 0
    for (word in segment.lowercase().split("[^a-z0-9]+".toRegex())) {
      if (COMPANY_WORDS.contains(word)) {
        score++
      }
    }
    return score
  }

  private fun titleScore(segment: String): Int {
    var score = 0
    for (word in segment.lowercase().split("[^a-z0-9]+".toRegex())) {
      if (TITLE_WORDS.contains(word)) {
        score++
      }
    }
    return score
  }

  private fun looksLikeLocation(segment: String): Boolean {
    val lower = segment.lowercase().trim()
    if (LOCATION_WORDS.contains(lower)) {
      return true
    }
    // "City, ST" (e.g. "Vienna, VA") or "City, State".
    return lower.matches("[a-z .'-]+,\\s*[a-z]{2,}".toRegex())
  }

  private fun parseEducation(lines: List<String>): ArrayNode {
    val items = mapper.createArrayNode()
    for (line in sectionLines(lines, Section.EDUCATION)) {
      if (line.isBlank()) {
        continue
      }
      val item = mapper.createObjectNode()
      val parts = line.split("\\s*,\\s*".toRegex())
      var year = ""
      val yearMatcher = YEAR.matcher(line)
      if (yearMatcher.find()) {
        year = yearMatcher.group()
      }
      item.put("degree", if (parts.isNotEmpty()) parts[0].trim() else "")
      item.put("school", if (parts.size > 1) parts[1].trim() else "")
      item.put("year", year)
      items.add(item)
    }
    return items
  }

  private fun parseSkills(lines: List<String>): ArrayNode {
    val skills = LinkedHashSet<String>()
    for (line in sectionLines(lines, Section.SKILLS)) {
      val cleaned = BULLET.matcher(line.trim()).replaceFirst("\$1")
      for (part in cleaned.split("[,;•·]".toRegex())) {
        val skill = part.trim()
        if (skill.isNotEmpty() && skill.length <= 60) {
          skills.add(skill)
        }
      }
    }
    val out = mapper.createArrayNode()
    skills.take(100).forEach { out.add(it) }
    return out
  }

  /** Lines belonging to a section (between its heading and the next heading). */
  private fun sectionLines(lines: List<String>, wanted: Section): List<String> {
    val out = ArrayList<String>()
    var inSection = false
    for (line in lines) {
      val heading = Section.headingOf(line)
      if (heading != null) {
        inSection = heading == wanted
        continue
      }
      if (inSection) {
        out.add(line)
      }
    }
    return out
  }

  private enum class Section {
    SUMMARY, EXPERIENCE, EDUCATION, SKILLS;

    companion object {
      /** A heading line, or null. Matches "EXPERIENCE", "Experience:", "WORK HISTORY", ... */
      fun headingOf(line: String): Section? {
        var upper = line.trim().uppercase()
        if (upper.endsWith(":")) {
          upper = upper.substring(0, upper.length - 1)
        }
        return when (upper) {
          "SUMMARY", "PROFILE", "OBJECTIVE", "ABOUT",
          // Common heading variants: without these, "EXECUTIVE SUMMARY"
          // is read as the contact name (2 capitalised words) and the
          // preview header shows the heading instead of the person's name.
          "EXECUTIVE SUMMARY", "PROFESSIONAL SUMMARY", "CAREER SUMMARY",
          "PROFESSIONAL PROFILE", "CAREER PROFILE",
          "PROFESSIONAL OBJECTIVE", "CAREER OBJECTIVE" -> SUMMARY
          "EXPERIENCE", "WORK EXPERIENCE", "WORK HISTORY", "EMPLOYMENT",
          "PROFESSIONAL EXPERIENCE", "EMPLOYMENT HISTORY" -> EXPERIENCE
          "EDUCATION", "EDUCATION & CERTIFICATIONS", "EDUCATION AND CERTIFICATIONS" -> EDUCATION
          "SKILLS", "TECHNICAL SKILLS", "CORE SKILLS", "SKILLS & TECHNOLOGIES" -> SKILLS
          else -> null
        }
      }
    }
  }

  companion object {
    private val EMAIL = Pattern.compile(
        "[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}")
    private val PHONE = Pattern.compile(
        "(\\+?1[\\s.\\-]?)?(\\(?\\d{3}\\)?[\\s.\\-]?)?\\d{3}[\\s.\\-]\\d{4}")
    private val LINKEDIN = Pattern.compile(
        "(linkedin\\.com/in/[^\\s,;]+)", Pattern.CASE_INSENSITIVE)
    private val YEAR = Pattern.compile("\\b(19|20)\\d{2}\\b")
    private val DATE_RANGE = Pattern.compile(
        "(?i)\\b((?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Sept|Oct|Nov|Dec)[a-z]*\\.?\\s+\\d{4}|\\d{4})\\s*(?:–|—|-|to)\\s*((?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Sept|Oct|Nov|Dec)[a-z]*\\.?\\s+\\d{4}|\\d{4}|Present|Current)\\b")
    private val BULLET = Pattern.compile("^\\s*[•\\-\\*·]\\s*(.*)$")

    /** Company-name signals: legal forms, org words, industry words. */
    private val COMPANY_WORDS = setOf(
        "technologies", "technology", "tech", "inc", "llc", "ltd", "corp", "corporation",
        "federal", "credit", "union", "bank", "systems", "labs", "laboratories",
        "consulting", "group", "solutions", "services", "software", "company", "co",
        "industries", "partners", "global", "digital", "networks", "holdings",
        "enterprises", "associates", "international", "financial", "health",
        "communications", "media", "energy", "aerospace", "defense", "retail")

    /** Role signals: words that appear in job titles. */
    private val TITLE_WORDS = setOf(
        "engineer", "developer", "lead", "senior", "manager", "architect", "analyst",
        "consultant", "director", "designer", "tester", "scientist", "administrator",
        "specialist", "principal", "staff", "intern", "junior", "associate", "head",
        "owner", "founder", "president", "officer", "coordinator", "supervisor",
        "programmer", "devops", "sre", "qa", "product", "project", "scrum", "master")

    /** Work-mode words: a segment that is a place/mode, not a title or a company. */
    private val LOCATION_WORDS = setOf(
        "remote", "hybrid", "onsite", "on-site", "on site")
  }
}
