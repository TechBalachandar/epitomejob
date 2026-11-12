package com.epitomejob.interview.repository;

import com.epitomejob.interview.model.Answer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AnswerRepository extends JpaRepository<Answer, UUID> {
  List<Answer> findBySessionId(UUID sessionId);
  Optional<Answer> findBySessionIdAndQuestionId(UUID sessionId, UUID questionId);
}
