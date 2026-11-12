package com.epitomejob.approval.repository;

import com.epitomejob.approval.model.Approval;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ApprovalRepository extends JpaRepository<Approval, UUID> {
  List<Approval> findByUserIdAndStatusOrderByExpiresAtAsc(UUID userId, String status);
}
