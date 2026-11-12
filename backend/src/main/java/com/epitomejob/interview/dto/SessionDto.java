package com.epitomejob.interview.dto;

import com.epitomejob.interview.model.Answer;
import com.epitomejob.interview.model.InterviewSession;
import com.epitomejob.interview.model.Question;
import com.epitomejob.job.model.Job;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** Session shape, exact field names per the W2 contract §3. */
public record SessionDto(
    UUID id,
    UUID jobId,
    String jobTitle,
    String status,
    Instant createdAt,
    Instant completedAt,
    List<AnswerDto> answers,
    Integer averageScore) {

  /**
   * Answers are listed in the role-question order. averageScore is the
   * rounded mean of the answer overallScores, null when unanswered.
   */
  public static SessionDto from(InterviewSession session, Job job,
                                List<Question> roleQuestions, List<Answer> answers) {
    Map<UUID, Question> byId = roleQuestions.stream()
        .collect(Collectors.toMap(Question::getId, q -> q));
    Map<UUID, Answer> answersByQuestion = answers.stream()
        .collect(Collectors.toMap(Answer::getQuestionId, a -> a));
    List<AnswerDto> dtos = roleQuestions.stream()
        .filter(q -> answersByQuestion.containsKey(q.getId()))
        .map(q -> AnswerDto.from(answersByQuestion.get(q.getId()), q.getText()))
        .toList();
    Integer average = answers.isEmpty() ? null
        : (int) Math.round(answers.stream()
            .mapToInt(Answer::getOverallScore).average().orElse(0));
    return new SessionDto(session.getId(), session.getJobId(), job.getTitle(),
        session.getStatus(), session.getCreatedAt(), session.getCompletedAt(),
        dtos, average);
  }
}
