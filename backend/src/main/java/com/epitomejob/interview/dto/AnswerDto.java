package com.epitomejob.interview.dto;

import com.epitomejob.interview.model.Answer;

import java.util.List;
import java.util.UUID;

/** Answer shape, exact field names per the W2 contract §3. */
public record AnswerDto(
    UUID id,
    UUID questionId,
    String questionText,
    String answerText,
    AnswerScores scores,
    int overallScore,
    List<String> feedback) {

  public record AnswerScores(int structure, int relevance, int specificity, int conciseness) {
  }

  public static AnswerDto from(Answer answer, String questionText) {
    return new AnswerDto(answer.getId(), answer.getQuestionId(), questionText,
        answer.getAnswerText(),
        new AnswerScores(answer.getStructure(), answer.getRelevance(),
            answer.getSpecificity(), answer.getConciseness()),
        answer.getOverallScore(), answer.getFeedback());
  }
}
