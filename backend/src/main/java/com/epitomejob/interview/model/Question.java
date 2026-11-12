package com.epitomejob.interview.model;

import com.epitomejob.common.constants.AppConstants;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/** One seeded interview question. Keywords are comma-separated TEXT. */
@Entity
@Table(name = "questions", schema = "interview")
public class Question {

  @Id
  private UUID id = UUID.randomUUID();

  @Column(nullable = false)
  private String role;

  @Column(nullable = false)
  private String category;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String text;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String keywords = "";

  @Column(nullable = false)
  private int sortOrder;

  protected Question() {}

  public Question(String role, String category, String text, List<String> keywords, int sortOrder) {
    this.role = role;
    this.category = category;
    this.text = text;
    setKeywords(keywords);
    this.sortOrder = sortOrder;
  }

  public UUID getId() { return id; }
  public String getRole() { return role; }
  public String getCategory() { return category; }
  public String getText() { return text; }
  public int getSortOrder() { return sortOrder; }

  public List<String> getKeywords() {
    if (keywords == null || keywords.isBlank()) {
      return List.of();
    }
    return Arrays.stream(keywords.split(AppConstants.Job.SKILLS_SEPARATOR))
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .toList();
  }

  public void setKeywords(List<String> keywords) {
    this.keywords = keywords == null ? ""
        : String.join(AppConstants.Job.SKILLS_SEPARATOR, keywords);
  }
}
