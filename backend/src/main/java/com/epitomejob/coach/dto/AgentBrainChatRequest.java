package com.epitomejob.coach.dto;

/** Body the Java backend sends to the agent-brain POST /chat (W3 contract §2). */
public record AgentBrainChatRequest(String message, String userId) {}
