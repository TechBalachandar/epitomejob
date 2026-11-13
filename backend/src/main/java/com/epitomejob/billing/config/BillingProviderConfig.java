package com.epitomejob.billing.config;

import com.epitomejob.billing.service.BillingProvider;
import com.epitomejob.billing.service.FakeBillingProvider;
import com.epitomejob.billing.service.StripeBillingProvider;
import com.epitomejob.common.constants.AppConstants;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Selects the billing provider (W4 contract §1a), following the existing
 * {@code @ConditionalOnProperty} pattern (see job/config/JobProviderConfig).
 * Default is Stripe; set {@code features.billing.provider=fake} for tests
 * and explicit local-dev demo. The fake provider is never the default.
 */
@Configuration
public class BillingProviderConfig {

  @Bean
  @ConditionalOnProperty(name = AppConstants.Billing.FEATURE_PROVIDER,
      havingValue = AppConstants.Billing.PROVIDER_STRIPE, matchIfMissing = true)
  public BillingProvider stripeBillingProvider(
      @Value("${STRIPE_SECRET_KEY:}") String secretKey,
      @Value("${STRIPE_WEBHOOK_SECRET:}") String webhookSecret,
      @Value("${app.public-url:http://localhost:3000}") String publicUrl,
      ObjectMapper objectMapper) {
    return new StripeBillingProvider(secretKey, webhookSecret, publicUrl, objectMapper);
  }

  @Bean
  @ConditionalOnProperty(name = AppConstants.Billing.FEATURE_PROVIDER,
      havingValue = AppConstants.Billing.PROVIDER_FAKE)
  public BillingProvider fakeBillingProvider(ObjectMapper objectMapper) {
    return new FakeBillingProvider(objectMapper);
  }
}
