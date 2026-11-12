package com.epitomejob.coach.config;

import com.epitomejob.common.constants.AppConstants;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Outbound HTTP to the agent-brain (W3 contract §3) — the first outbound
 * client in the codebase. Base URL comes from {@code app.agent-brain.url}
 * (default http://localhost:8000, env AGENT_BRAIN_URL). Read timeout 120s
 * matches the agent-brain's Ollama timeout.
 */
@Configuration
public class AgentBrainConfig {

  @Bean
  public RestClient agentBrainRestClient(
      @Value("${app.agent-brain.url:http://localhost:8000}") String baseUrl) {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(Duration.ofSeconds(
        AppConstants.Coach.AGENT_BRAIN_CONNECT_TIMEOUT_SECONDS));
    factory.setReadTimeout(Duration.ofSeconds(
        AppConstants.Coach.AGENT_BRAIN_READ_TIMEOUT_SECONDS));
    return RestClient.builder()
        .baseUrl(baseUrl)
        .requestFactory(factory)
        .build();
  }
}
