package com.epitomejob.billing.repository;

import com.epitomejob.billing.model.Entitlement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface EntitlementRepository extends JpaRepository<Entitlement, UUID> {

  boolean existsByUserIdAndPlanCodeAndStatus(UUID userId, String planCode, String status);

  Optional<Entitlement> findFirstByUserIdAndStatusOrderByStartedAtDesc(UUID userId, String status);

  long countByUserIdAndPlanCodeAndStatus(UUID userId, String planCode, String status);
}
