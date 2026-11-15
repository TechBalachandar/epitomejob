package com.epitomejob.resumebuilder.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient
import java.time.Duration

/**
 * Outbound clients. Both talk DIRECT to the service (same 5A decision as
 * agent-brain: the gateway fronts clients only). Timeouts are explicit so a
 * hung downstream fails fast instead of hanging the request thread.
 */
@Configuration
class RestClientConfig {

  @Bean
  fun agentBrainRestClient(
      @Value("\${app.agent-brain.url}") agentBrainUrl: String): RestClient {
    return restClient(agentBrainUrl, Duration.ofSeconds(30))
  }

  @Bean
  fun javaBackendRestClient(
      @Value("\${app.java-base-url}") javaBaseUrl: String): RestClient {
    return restClient(javaBaseUrl, Duration.ofSeconds(10))
  }

  private fun restClient(baseUrl: String, readTimeout: Duration): RestClient {
    val factory = SimpleClientHttpRequestFactory()
    factory.setConnectTimeout(Duration.ofSeconds(5))
    factory.setReadTimeout(readTimeout)
    return RestClient.builder()
        .baseUrl(baseUrl)
        .requestFactory(factory)
        .build()
  }
}
