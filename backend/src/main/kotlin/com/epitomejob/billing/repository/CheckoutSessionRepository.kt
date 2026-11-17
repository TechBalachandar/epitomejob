package com.epitomejob.billing.repository

import com.epitomejob.billing.model.CheckoutSession
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional
import java.util.UUID

interface CheckoutSessionRepository : JpaRepository<CheckoutSession, UUID> {

  fun findByProviderSessionId(providerSessionId: String): Optional<CheckoutSession>
}
