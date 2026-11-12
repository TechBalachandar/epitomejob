package com.epitomejob.interview.dto;

import com.epitomejob.interview.model.Question;

import java.util.UUID;

/** Question shape, exact field names per the W2 contract §3. */
public record QuestionDto(
    UUID id,
    String text,
    String category,
    String role) {

  public static QuestionDto from(Question question) {
    return new QuestionDto(question.getId(), question.getText(),
        question.getCategory(), question.getRole());
  }
}
