package com.epitomejob.coach.service;

import com.epitomejob.coach.dto.AgentBrainChatRequest;
import com.epitomejob.common.constants.AppConstants;
import com.epitomejob.common.exception.AgentUnavailableException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.UUID;

/**
 * Calls the agent-brain (W3 contract §2/§3). The response is returned
 * verbatim — status + JSON body — so the coach endpoint is a transparent
 * proxy with exactly the error mapping the contract requires: unreachable /
 * timeout → 502, agent-brain 401 → forward 401, any other agent-brain
 * 4xx/5xx → forward status + body unchanged.
 */
@Service
public class AgentBrainClient {

  private final RestClient agentBrain;

  public AgentBrainClient(RestClient agentBrainRestClient) {
    this.agentBrain = agentBrainRestClient;
  }

  public ResponseEntity<String> chat(String message, UUID userId, String authorization) {
    try {
      ResponseEntity<String> response = agentBrain.post()
          .uri(AppConstants.Coach.CHAT_PATH)
          .header(AppConstants.Http.AUTHORIZATION_HEADER, authorization)
          .contentType(MediaType.APPLICATION_JSON)
          .body(new AgentBrainChatRequest(message, userId.toString()))
          .retrieve()
          .toEntity(String.class);
      return ResponseEntity.status(response.getStatusCode())
          .contentType(contentTypeOf(response.getHeaders()))
          .body(response.getBody());
    } catch (HttpStatusCodeException ex) {
      // Agent-brain 4xx/5xx (including 401): forward status + body unchanged.
      return ResponseEntity.status(ex.getStatusCode())
          .contentType(contentTypeOf(ex.getResponseHeaders()))
          .body(ex.getResponseBodyAsString());
    } catch (ResourceAccessException ex) {
      // Unreachable / connect or read timeout → 502 (contract §3).
      throw new AgentUnavailableException(ex);
    }
  }

  private static MediaType contentTypeOf(HttpHeaders headers) {
    MediaType contentType = headers == null ? null : headers.getContentType();
    return contentType != null ? contentType : MediaType.APPLICATION_JSON;
  }
}
