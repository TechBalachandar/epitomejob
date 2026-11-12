package com.epitomejob.job.repository;

import com.epitomejob.job.model.Job;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface JobRepository extends JpaRepository<Job, UUID> {
}
