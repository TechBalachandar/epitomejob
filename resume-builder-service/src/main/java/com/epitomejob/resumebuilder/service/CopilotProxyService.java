package com.epitomejob.resumebuilder.service;

import com.epitomejob.resumebuilder.common.constants.AppConstants;
import com.epitomejob.resumebuilder.common.exception.AgentUnavailableException;
import com.epitomejob.resumebuilder.common.util.SecurityUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.UUID;

/**
 * Co-Pilot proxy — same pattern as the monolith's AgentBrainClient
 * (coach/service/AgentBrainClient.java): the response is returned verbatim
 * (status + JSON body), the service never interprets it. Unreachable /
 * timeout → 502; an agent-brain 4xx/5xx is forwarded with status + body
 * unchanged. The caller's Bearer token is forwarded so the agent-brain
 * can validate it.
 *
 * <p>CONTRACT GAP (2026-11-14, live main 3855e76): the agent-brain on main
 * exposes POST /chat, /rag/index and /rag/query — it has NO
 * POST /resume-builder/copilot endpoint yet. This proxy targets the
 * endpoint the RB brief specifies; until the agent-brain worker adds it,
 * every copilot call will come back as the agent-brain's 404 (forwarded
 * verbatim) or 502 if the service is down. The journey IT stubs the
 * endpoint, so the proxy itself is proven here either way.
 */
@Service
public class CopilotProxyService {

  private final RestClient agentBrain;
  private final ObjectMapper mapper;

  public CopilotProxyService(RestClient agentBrainRestClient, ObjectMapper mapper) {
    this.agentBrain = agentBrainRestClient;
    this.mapper = mapper;
  }

  /**
   * Forwards the client's copilot request plus the user id, document id and
   * current resume state — everything the Co-Pilot needs — to the
   * agent-brain and returns its JSON verbatim.
   */
  public ResponseEntity<String> copilot(UUID documentId, JsonNode state,
      JsonNode clientRequest, String authorization) {
    ObjectNode body = mapper.createObjectNode();
    if (clientRequest != null && clientRequest.isObject()) {
      body.setAll((ObjectNode) clientRequest);
    }
    body.put("userId", SecurityUtil.currentUserId().toString());
    body.put("documentId", documentId.toString());
    body.set("resumeState", state);
    try {
      ResponseEntity<String> response = agentBrain.post()
          .uri(AppConstants.Downstream.COPILOT_PATH)
          .header(AppConstants.Http.AUTHORIZATION_HEADER, authorization)
          .contentType(MediaType.APPLICATION_JSON)
          .body(body)
          .retrieve()
          .toEntity(String.class);
      return ResponseEntity.status(response.getStatusCode())
          .contentType(contentTypeOf(response.getHeaders()))
          .body(response.getBody());
    } catch (HttpStatusCodeException ex) {
      return ResponseEntity.status(ex.getStatusCode())
          .contentType(contentTypeOf(ex.getResponseHeaders()))
          .body(ex.getResponseBodyAsString());
    } catch (ResourceAccessException ex) {
      throw new AgentUnavailableException(AppConstants.Errors.AGENT_UNAVAILABLE_MESSAGE, ex);
    }
  }

  private static MediaType contentTypeOf(HttpHeaders headers) {
    MediaType contentType = headers == null ? null : headers.getContentType();
    return contentType != null ? contentType : MediaType.APPLICATION_JSON;
  }
}
