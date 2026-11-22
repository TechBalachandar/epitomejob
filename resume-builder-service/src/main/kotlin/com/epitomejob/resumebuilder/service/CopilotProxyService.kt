package com.epitomejob.resumebuilder.service

import com.epitomejob.resumebuilder.common.constants.AppConstants
import com.epitomejob.resumebuilder.common.exception.AgentUnavailableException
import com.epitomejob.resumebuilder.common.util.SecurityUtil
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import org.springframework.web.client.HttpStatusCodeException
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClient
import java.util.UUID

/**
 * Co-Pilot proxy — same pattern as the monolith's AgentBrainClient
 * (coach/service/AgentBrainClient.java): the response is returned verbatim
 * (status + JSON body), the service never interprets it. Unreachable /
 * timeout → 502; an agent-brain 4xx/5xx is forwarded with status + body
 * unchanged. The caller's Bearer token is forwarded so the agent-brain
 * can validate it.
 *
 * CONTRACT GAP — CORRECTED 2026-11-18: this comment (2026-11-14) said the
 * agent-brain had NO POST /resume-builder/copilot endpoint yet. That was
 * true of main at the time, but the endpoint was added the same day in
 * ed11216 and EXISTS on this branch (agent-brain/app/main.py:254). Do not
 * re-add 404 tolerance for copilot on the strength of this old note: a
 * 404 from the proxy now means the route is broken, not a missing
 * endpoint. The journey IT stubs the endpoint, so the proxy itself is
 * proven here either way.
 */
@Service
class CopilotProxyService(
  // Constructor parameter name must stay `agentBrainRestClient`: it matches
  // the RestClient bean name, which is how Spring picks it over the
  // javaBackendRestClient bean.
  private val agentBrainRestClient: RestClient,
  private val mapper: ObjectMapper
) {

  /**
   * Forwards the client's copilot request plus the user id, document id and
   * current resume state — everything the Co-Pilot needs — to the
   * agent-brain and returns its JSON verbatim.
   */
  fun copilot(documentId: UUID, state: JsonNode,
      clientRequest: JsonNode?, authorization: String): ResponseEntity<String> {
    val body = mapper.createObjectNode()
    if (clientRequest != null && clientRequest.isObject) {
      body.setAll<JsonNode>(clientRequest as ObjectNode)
    }
    body.put("userId", SecurityUtil.currentUserId().toString())
    body.put("documentId", documentId.toString())
    body.set<JsonNode>("resumeState", state)
    try {
      val response = agentBrainRestClient.post()
          .uri(AppConstants.Downstream.COPILOT_PATH)
          .header(AppConstants.Http.AUTHORIZATION_HEADER, authorization)
          .contentType(MediaType.APPLICATION_JSON)
          .body(body)
          .retrieve()
          .toEntity(String::class.java)
      return ResponseEntity.status(response.statusCode)
          .contentType(contentTypeOf(response.headers))
          .body(response.body)
    } catch (ex: HttpStatusCodeException) {
      return ResponseEntity.status(ex.statusCode)
          .contentType(contentTypeOf(ex.responseHeaders))
          .body(ex.responseBodyAsString)
    } catch (ex: ResourceAccessException) {
      throw AgentUnavailableException(AppConstants.Errors.AGENT_UNAVAILABLE_MESSAGE, ex)
    }
  }

  private fun contentTypeOf(headers: HttpHeaders?): MediaType {
    val contentType = headers?.contentType
    return contentType ?: MediaType.APPLICATION_JSON
  }
}
