package com.epitomejob.coach.service;

import com.epitomejob.common.util.SecurityUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

/**
 * Coach chat: resolves the authenticated user and hands the message to the
 * agent-brain. The agent-brain response is returned verbatim (status + JSON
 * body) — this service never interprets it (W3 contract §3).
 */
@Service
public class CoachService {

  private final AgentBrainClient agentBrain;

  public CoachService(AgentBrainClient agentBrain) {
    this.agentBrain = agentBrain;
  }

  public ResponseEntity<String> chat(String message, String authorization) {
    return agentBrain.chat(message, SecurityUtil.currentUserId(), authorization);
  }
}
