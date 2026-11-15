package com.epitomejob.coach.service

import com.epitomejob.coach.dto.AgentBrainChatRequest
import com.epitomejob.common.constants.AppConstants
import com.epitomejob.common.exception.AgentUnavailableException
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import org.springframework.web.client.HttpStatusCodeException
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClient
import java.util.UUID

/**
 * Calls the agent-brain (W3 contract §2/§3). The response is returned
 * verbatim — status + JSON body — so the coach endpoint is a transparent
 * proxy with exactly the error mapping the contract requires: unreachable /
 * timeout → 502, agent-brain 401 → forward 401, any other agent-brain
 * 4xx/5xx → forward status + body unchanged.
 */
@Service
class AgentBrainClient(private val agentBrain: RestClient) {

  fun chat(message: String, userId: UUID, authorization: String): ResponseEntity<String> {
    try {
      val response = agentBrain.post()
        .uri(AppConstants.Coach.CHAT_PATH)
        .header(AppConstants.Http.AUTHORIZATION_HEADER, authorization)
        .contentType(MediaType.APPLICATION_JSON)
        .body(AgentBrainChatRequest(message, userId.toString()))
        .retrieve()
        .toEntity(String::class.java)
      // The body may be null (e.g. an empty agent-brain response); a null
      // body must still be forwarded as null, not as an empty string.
      val builder = ResponseEntity.status(response.statusCode)
        .contentType(contentTypeOf(response.headers))
      return response.body?.let { builder.body(it) } ?: builder.build()
    } catch (ex: HttpStatusCodeException) {
      // Agent-brain 4xx/5xx (including 401): forward status + body unchanged.
      return ResponseEntity.status(ex.statusCode)
        .contentType(contentTypeOf(ex.responseHeaders))
        .body(ex.responseBodyAsString)
    } catch (ex: ResourceAccessException) {
      // Unreachable / connect or read timeout → 502 (contract §3).
      throw AgentUnavailableException(ex)
    }
  }

  companion object {
    private fun contentTypeOf(headers: HttpHeaders?): MediaType {
      return headers?.contentType ?: MediaType.APPLICATION_JSON
    }
  }
}
