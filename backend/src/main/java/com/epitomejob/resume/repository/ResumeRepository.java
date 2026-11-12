package com.epitomejob.resume.repository;

import com.epitomejob.resume.model.Resume;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ResumeRepository extends JpaRepository<Resume, UUID> {
  List<Resume> findByUserIdOrderByCreatedAtDesc(UUID userId);
}
