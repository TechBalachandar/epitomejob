package com.epitomejob.billing.repository;

import com.epitomejob.billing.model.CheckoutSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CheckoutSessionRepository extends JpaRepository<CheckoutSession, UUID> {

  Optional<CheckoutSession> findByProviderSessionId(String providerSessionId);
}
