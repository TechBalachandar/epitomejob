package com.epitomejob.resumebuilder.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Best-effort structured extraction from plain resume text. Anything that
 * cannot be structured is preserved verbatim in {@code rawText} — nothing
 * the user uploaded is ever silently dropped.
 *
 * <p>Heuristics (line-based, deterministic — no LLM): the contact block is
 * read from the top lines; sections are found by heading lines
 * (SUMMARY / EXPERIENCE / EDUCATION / SKILLS and common variants);
 * experience items start at a line carrying a date range or a
 * "Title at Company" / "Title | Company" header, with •/-/* lines as
 * bullets; education lines split on commas; skills split on commas,
 * semicolons and bullets.
 */
@Service
public class ResumeStateParser {

  private static final Pattern EMAIL = Pattern.compile(
      "[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}");
  private static final Pattern PHONE = Pattern.compile(
      "(\\+?1[\\s.\\-]?)?(\\(?\\d{3}\\)?[\\s.\\-]?)?\\d{3}[\\s.\\-]\\d{4}");
  private static final Pattern LINKEDIN = Pattern.compile(
      "(linkedin\\.com/in/[^\\s,;]+)", Pattern.CASE_INSENSITIVE);
  private static final Pattern YEAR = Pattern.compile("\\b(19|20)\\d{2}\\b");
  private static final Pattern DATE_RANGE = Pattern.compile(
      "(?i)\\b((?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Sept|Oct|Nov|Dec)[a-z]*\\.?\\s+\\d{4}|\\d{4})\\s*(?:–|—|-|to)\\s*((?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Sept|Oct|Nov|Dec)[a-z]*\\.?\\s+\\d{4}|\\d{4}|Present|Current)\\b");
  private static final Pattern BULLET = Pattern.compile("^\\s*[•\\-\\*·]\\s*(.*)$");

  /** Company-name signals: legal forms, org words, industry words. */
  private static final Set<String> COMPANY_WORDS = Set.of(
      "technologies", "technology", "tech", "inc", "llc", "ltd", "corp", "corporation",
      "federal", "credit", "union", "bank", "systems", "labs", "laboratories",
      "consulting", "group", "solutions", "services", "software", "company", "co",
      "industries", "partners", "global", "digital", "networks", "holdings",
      "enterprises", "associates", "international", "financial", "health",
      "communications", "media", "energy", "aerospace", "defense", "retail");

  /** Role signals: words that appear in job titles. */
  private static final Set<String> TITLE_WORDS = Set.of(
      "engineer", "developer", "lead", "senior", "manager", "architect", "analyst",
      "consultant", "director", "designer", "tester", "scientist", "administrator",
      "specialist", "principal", "staff", "intern", "junior", "associate", "head",
      "owner", "founder", "president", "officer", "coordinator", "supervisor",
      "programmer", "devops", "sre", "qa", "product", "project", "scrum", "master");

  /** Work-mode words: a segment that is a place/mode, not a title or a company. */
  private static final Set<String> LOCATION_WORDS = Set.of(
      "remote", "hybrid", "onsite", "on-site", "on site");

  private final ObjectMapper mapper;

  public ResumeStateParser(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  /** Parses raw text into the resume-state JSON shape (see README §State). */
  public ObjectNode parse(String rawText) {
    String text = rawText == null ? "" : rawText;
    List<String> lines = text.lines().map(String::trim).toList();
    ObjectNode state = mapper.createObjectNode();
    state.set("contact", parseContact(lines));
    state.put("summary", parseSectionText(lines, Section.SUMMARY));
    state.set("experience", parseExperience(lines));
    state.set("education", parseEducation(lines));
    state.set("skills", parseSkills(lines));
    state.put("rawText", text);
    return state;
  }

  /** An empty state for a blank document (POST /documents without state). */
  public ObjectNode emptyState() {
    ObjectNode state = mapper.createObjectNode();
    ObjectNode contact = mapper.createObjectNode();
    contact.put("fullName", "");
    contact.put("email", "");
    contact.put("phone", "");
    contact.put("linkedin", "");
    contact.put("location", "");
    state.set("contact", contact);
    state.put("summary", "");
    state.set("experience", mapper.createArrayNode());
    state.set("education", mapper.createArrayNode());
    state.set("skills", mapper.createArrayNode());
    state.put("rawText", "");
    return state;
  }

  private ObjectNode parseContact(List<String> lines) {
    ObjectNode contact = mapper.createObjectNode();
    String name = "";
    String email = "";
    String phone = "";
    String linkedin = "";
    String location = "";
    int scanned = 0;
    for (String line : lines) {
      if (line.isEmpty()) {
        continue;
      }
      if (scanned >= 8 || Section.headingOf(line) != null) {
        break;
      }
      scanned++;
      Matcher emailMatcher = EMAIL.matcher(line);
      if (email.isEmpty() && emailMatcher.find()) {
        email = emailMatcher.group();
      }
      Matcher phoneMatcher = PHONE.matcher(line);
      if (phone.isEmpty() && phoneMatcher.find()) {
        phone = phoneMatcher.group().trim();
      }
      Matcher linkedinMatcher = LINKEDIN.matcher(line);
      if (linkedin.isEmpty() && linkedinMatcher.find()) {
        linkedin = linkedinMatcher.group(1);
      }
      // A name may sit on a "Name | Title | Location" line: only the first
      // segment (before the first "|") is a name candidate. Without this,
      // a name on such a line is skipped and a later heading ("EXECUTIVE
      // SUMMARY" — 2 capitalised words) is misread as the name.
      String nameCandidate = line.contains("|")
          ? line.substring(0, line.indexOf('|')).trim() : line;
      if (name.isEmpty() && !EMAIL.matcher(nameCandidate).find()
          && !nameCandidate.contains("@") && !nameCandidate.toLowerCase().contains("linkedin")
          && nameCandidate.chars().noneMatch(Character::isDigit)
          && nameCandidate.split("\\s+").length >= 2 && nameCandidate.split("\\s+").length <= 5
          // A name is a proper noun: every word capitalised. Without this,
          // any 2-5 word lowercase line ("just some words") is misread as
          // the contact name and unstructured text looks structured.
          && Arrays.stream(nameCandidate.split("\\s+"))
              .allMatch(w -> !w.isEmpty() && Character.isUpperCase(w.charAt(0)))) {
        name = nameCandidate;
      }
      if (location.isEmpty() && line.contains(",") && !line.contains("@")
          && YEAR.matcher(line).find() == false && name.equals(line) == false
          && line.split(",").length == 2 && line.length() <= 40) {
        location = line;
      }
    }
    contact.put("fullName", name);
    contact.put("email", email);
    contact.put("phone", phone);
    contact.put("linkedin", linkedin);
    contact.put("location", location);
    return contact;
  }

  private String parseSectionText(List<String> lines, Section wanted) {
    List<String> section = sectionLines(lines, wanted);
    return String.join(" ", section).trim();
  }

  private ArrayNode parseExperience(List<String> lines) {
    ArrayNode items = mapper.createArrayNode();
    List<String> section = sectionLines(lines, Section.EXPERIENCE);
    ObjectNode current = null;
    for (String rawLine : section) {
      String line = rawLine.trim();
      if (line.isEmpty()) {
        continue;
      }
      Matcher bullet = BULLET.matcher(line);
      if (bullet.matches() && current != null) {
        ((ArrayNode) current.get("bullets")).add(bullet.group(1).trim());
        continue;
      }
      if (isJobHeader(line)) {
        current = newExperienceItem(line);
        items.add(current);
        continue;
      }
      if (current != null) {
        // A non-bullet continuation line under a job: keep it as a bullet
        // so the text survives in the structured state too.
        ((ArrayNode) current.get("bullets")).add(line);
      }
    }
    return items;
  }

  private boolean isJobHeader(String line) {
    if (DATE_RANGE.matcher(line).find()) {
      return true;
    }
    String lower = line.toLowerCase();
    return (lower.contains(" at ") || line.contains(" | ") || line.contains(" — "))
        && line.split("\\s+").length >= 3 && line.length() <= 120;
  }

  private ObjectNode newExperienceItem(String header) {
    ObjectNode item = mapper.createObjectNode();
    String title = header;
    String company = "";
    String startDate = "";
    String endDate = "";
    Matcher dates = DATE_RANGE.matcher(header);
    if (dates.find()) {
      startDate = dates.group(1);
      endDate = dates.group(2);
      title = header.substring(0, dates.start()).trim()
          .replaceAll("[,|\\-–—]+$", "").trim();
    }
    if (title.contains(" | ")) {
      String[] parts = splitTitleCompany(title, "\\s*\\|\\s*");
      title = parts[0];
      company = parts[1];
    } else if (title.toLowerCase().contains(" at ")) {
      String[] parts = title.split("(?i)\\s+at\\s+", 2);
      title = parts[0].trim();
      company = parts[1].trim();
    } else if (title.contains(" — ")) {
      String[] parts = splitTitleCompany(title, "\\s+—\\s+");
      title = parts[0];
      company = parts[1];
    }
    item.put("title", title);
    item.put("company", company);
    item.put("startDate", startDate);
    item.put("endDate", endDate);
    item.set("bullets", mapper.createArrayNode());
    return item;
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
  private static String[] splitTitleCompany(String head, String separatorRegex) {
    String[] parts = head.split(separatorRegex);
    String first = parts[0].trim();
    String second = parts.length > 1 ? parts[1].trim() : "";
    boolean companyFirst = companyScore(first) > 0
        && titleScore(second) > titleScore(first)
        && companyScore(first) >= companyScore(second);
    String titlePart = companyFirst ? second : first;
    String companyPart = companyFirst ? first : second;
    if (looksLikeLocation(companyPart)) {
      companyPart = "";
    }
    return new String[]{titlePart, companyPart};
  }

  private static int companyScore(String segment) {
    int score = 0;
    for (String word : segment.toLowerCase().split("[^a-z0-9]+")) {
      if (COMPANY_WORDS.contains(word)) {
        score++;
      }
    }
    return score;
  }

  private static int titleScore(String segment) {
    int score = 0;
    for (String word : segment.toLowerCase().split("[^a-z0-9]+")) {
      if (TITLE_WORDS.contains(word)) {
        score++;
      }
    }
    return score;
  }

  private static boolean looksLikeLocation(String segment) {
    String lower = segment.toLowerCase().trim();
    if (LOCATION_WORDS.contains(lower)) {
      return true;
    }
    // "City, ST" (e.g. "Vienna, VA") or "City, State".
    return lower.matches("[a-z .'-]+,\\s*[a-z]{2,}");
  }

  private ArrayNode parseEducation(List<String> lines) {
    ArrayNode items = mapper.createArrayNode();
    for (String line : sectionLines(lines, Section.EDUCATION)) {
      if (line.isBlank()) {
        continue;
      }
      ObjectNode item = mapper.createObjectNode();
      String[] parts = line.split("\\s*,\\s*");
      String year = "";
      Matcher yearMatcher = YEAR.matcher(line);
      if (yearMatcher.find()) {
        year = yearMatcher.group();
      }
      item.put("degree", parts.length > 0 ? parts[0].trim() : "");
      item.put("school", parts.length > 1 ? parts[1].trim() : "");
      item.put("year", year);
      items.add(item);
    }
    return items;
  }

  private ArrayNode parseSkills(List<String> lines) {
    Set<String> skills = new LinkedHashSet<>();
    for (String line : sectionLines(lines, Section.SKILLS)) {
      String cleaned = BULLET.matcher(line.trim()).replaceFirst("$1");
      for (String part : cleaned.split("[,;•·]")) {
        String skill = part.trim();
        if (!skill.isEmpty() && skill.length() <= 60) {
          skills.add(skill);
        }
      }
    }
    ArrayNode out = mapper.createArrayNode();
    skills.stream().limit(100).forEach(out::add);
    return out;
  }

  /** Lines belonging to a section (between its heading and the next heading). */
  private static List<String> sectionLines(List<String> lines, Section wanted) {
    List<String> out = new ArrayList<>();
    boolean inSection = false;
    for (String line : lines) {
      Section heading = Section.headingOf(line);
      if (heading != null) {
        inSection = heading == wanted;
        continue;
      }
      if (inSection) {
        out.add(line);
      }
    }
    return out;
  }

  private enum Section {
    SUMMARY, EXPERIENCE, EDUCATION, SKILLS;

    /** A heading line, or null. Matches "EXPERIENCE", "Experience:", "WORK HISTORY", ... */
    static Section headingOf(String line) {
      String upper = line.trim().toUpperCase();
      if (upper.endsWith(":")) {
        upper = upper.substring(0, upper.length() - 1);
      }
      return switch (upper) {
        case "SUMMARY", "PROFILE", "OBJECTIVE", "ABOUT",
             // Common heading variants: without these, "EXECUTIVE SUMMARY"
             // is read as the contact name (2 capitalised words) and the
             // preview header shows the heading instead of the person's name.
             "EXECUTIVE SUMMARY", "PROFESSIONAL SUMMARY", "CAREER SUMMARY",
             "PROFESSIONAL PROFILE", "CAREER PROFILE",
             "PROFESSIONAL OBJECTIVE", "CAREER OBJECTIVE" -> SUMMARY;
        case "EXPERIENCE", "WORK EXPERIENCE", "WORK HISTORY", "EMPLOYMENT",
             "PROFESSIONAL EXPERIENCE", "EMPLOYMENT HISTORY" -> EXPERIENCE;
        case "EDUCATION", "EDUCATION & CERTIFICATIONS", "EDUCATION AND CERTIFICATIONS" -> EDUCATION;
        case "SKILLS", "TECHNICAL SKILLS", "CORE SKILLS", "SKILLS & TECHNOLOGIES" -> SKILLS;
        default -> null;
      };
    }
  }
}
