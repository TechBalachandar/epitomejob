package com.epitomejob.billing.repository;

import com.epitomejob.billing.model.Plan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PlanRepository extends JpaRepository<Plan, String> {

  List<Plan> findByActiveTrue();
}
