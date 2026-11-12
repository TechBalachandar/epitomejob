package com.epitomejob.job.config;

import com.epitomejob.job.repository.JobRepository;
import com.epitomejob.job.service.AdzunaJobProvider;
import com.epitomejob.job.service.JobProvider;
import com.epitomejob.job.service.SeedJobProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Selects the job provider (W2 contract §8). Default is the seed provider;
 * set {@code epitomejob.jobs.provider=adzuna} to use the Adzuna stub.
 */
@Configuration
public class JobProviderConfig {

  @Bean
  @ConditionalOnProperty(name = "epitomejob.jobs.provider", havingValue = "seed", matchIfMissing = true)
  public JobProvider seedJobProvider(JobRepository jobs) {
    return new SeedJobProvider(jobs);
  }

  @Bean
  @ConditionalOnProperty(name = "epitomejob.jobs.provider", havingValue = "adzuna")
  public JobProvider adzunaJobProvider() {
    return new AdzunaJobProvider();
  }
}
