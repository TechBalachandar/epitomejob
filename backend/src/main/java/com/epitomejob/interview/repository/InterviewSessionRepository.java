package com.epitomejob.interview.repository;

import com.epitomejob.interview.model.InterviewSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface InterviewSessionRepository extends JpaRepository<InterviewSession, UUID> {
  List<InterviewSession> findByUserIdOrderByCreatedAtDesc(UUID userId);
}
