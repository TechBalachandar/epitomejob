package com.epitomejob.coach.dto

/** Body the Java backend sends to the agent-brain POST /chat (W3 contract §2). */
data class AgentBrainChatRequest(val message: String, val userId: String)
