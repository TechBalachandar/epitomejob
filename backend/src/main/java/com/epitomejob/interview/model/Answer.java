package com.epitomejob.interview.model;

import com.epitomejob.common.constants.AppConstants;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/** One scored answer. Feedback lines are newline-separated TEXT. */
@Entity
@Table(name = "answers", schema = "interview")
public class Answer {

  @Id
  private UUID id = UUID.randomUUID();

  @Column(nullable = false)
  private UUID sessionId;

  @Column(nullable = false)
  private UUID questionId;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String answerText;

  @Column(nullable = false)
  private int structure;

  @Column(nullable = false)
  private int relevance;

  @Column(nullable = false)
  private int specificity;

  @Column(nullable = false)
  private int conciseness;

  @Column(nullable = false)
  private int overallScore;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String feedback = "";

  protected Answer() {}

  public Answer(UUID sessionId, UUID questionId, String answerText,
                int structure, int relevance, int specificity, int conciseness,
                int overallScore, List<String> feedback) {
    this.sessionId = sessionId;
    this.questionId = questionId;
    this.answerText = answerText;
    this.structure = structure;
    this.relevance = relevance;
    this.specificity = specificity;
    this.conciseness = conciseness;
    this.overallScore = overallScore;
    setFeedback(feedback);
  }

  public UUID getId() { return id; }
  public UUID getSessionId() { return sessionId; }
  public UUID getQuestionId() { return questionId; }
  public String getAnswerText() { return answerText; }
  public void setAnswerText(String t) { this.answerText = t; }
  public int getStructure() { return structure; }
  public void setStructure(int v) { this.structure = v; }
  public int getRelevance() { return relevance; }
  public void setRelevance(int v) { this.relevance = v; }
  public int getSpecificity() { return specificity; }
  public void setSpecificity(int v) { this.specificity = v; }
  public int getConciseness() { return conciseness; }
  public void setConciseness(int v) { this.conciseness = v; }
  public int getOverallScore() { return overallScore; }
  public void setOverallScore(int v) { this.overallScore = v; }

  public List<String> getFeedback() {
    if (feedback == null || feedback.isBlank()) {
      return List.of();
    }
    return Arrays.stream(feedback.split(AppConstants.Interview.FEEDBACK_SEPARATOR))
        .filter(s -> !s.isBlank())
        .toList();
  }

  public void setFeedback(List<String> feedback) {
    this.feedback = feedback == null ? ""
        : String.join(AppConstants.Interview.FEEDBACK_SEPARATOR, feedback);
  }
}
