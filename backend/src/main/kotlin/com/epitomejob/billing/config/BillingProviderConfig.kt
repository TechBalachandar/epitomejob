package com.epitomejob.billing.config

import com.epitomejob.billing.service.BillingProvider
import com.epitomejob.billing.service.FakeBillingProvider
import com.epitomejob.billing.service.StripeBillingProvider
import com.epitomejob.common.constants.AppConstants
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Selects the billing provider (W4 contract §1a), following the existing
 * `@ConditionalOnProperty` pattern (see job/config/JobProviderConfig).
 * Default is Stripe; set `features.billing.provider=fake` for tests
 * and explicit local-dev demo. The fake provider is never the default.
 */
@Configuration
class BillingProviderConfig {

  @Bean
  @ConditionalOnProperty(
    name = [AppConstants.Billing.FEATURE_PROVIDER],
    havingValue = AppConstants.Billing.PROVIDER_STRIPE,
    matchIfMissing = true
  )
  fun stripeBillingProvider(
    @Value("\${STRIPE_SECRET_KEY:}") secretKey: String,
    @Value("\${STRIPE_WEBHOOK_SECRET:}") webhookSecret: String,
    @Value("\${app.public-url:http://localhost:3000}") publicUrl: String,
    objectMapper: ObjectMapper
  ): BillingProvider {
    return StripeBillingProvider(secretKey, webhookSecret, publicUrl, objectMapper)
  }

  @Bean
  @ConditionalOnProperty(
    name = [AppConstants.Billing.FEATURE_PROVIDER],
    havingValue = AppConstants.Billing.PROVIDER_FAKE
  )
  fun fakeBillingProvider(objectMapper: ObjectMapper): BillingProvider {
    return FakeBillingProvider(objectMapper)
  }
}
