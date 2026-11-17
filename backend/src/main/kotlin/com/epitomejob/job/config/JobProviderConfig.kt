package com.epitomejob.job.config

import com.epitomejob.job.repository.JobRepository
import com.epitomejob.job.service.AdzunaJobProvider
import com.epitomejob.job.service.JobProvider
import com.epitomejob.job.service.SeedJobProvider
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Selects the job provider (W2 contract §8). Default is the seed provider;
 * set `epitomejob.jobs.provider=adzuna` to use the live Adzuna API
 * feed (requires ADZUNA_APP_ID + ADZUNA_APP_KEY). The compose stack sets
 * it to adzuna via EPITOMEJOB_JOBS_PROVIDER.
 */
@Configuration
class JobProviderConfig {

  @Bean
  @ConditionalOnProperty(
    name = ["epitomejob.jobs.provider"],
    havingValue = "seed",
    matchIfMissing = true
  )
  fun seedJobProvider(jobs: JobRepository): JobProvider {
    return SeedJobProvider(jobs)
  }

  @Bean
  @ConditionalOnProperty(name = ["epitomejob.jobs.provider"], havingValue = "adzuna")
  fun adzunaJobProvider(): JobProvider {
    return AdzunaJobProvider()
  }
}
