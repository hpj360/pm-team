package com.redteam.feishu.service;

import com.redteam.feishu.dto.AgentRequest;
import com.redteam.feishu.dto.AgentResponse;

public interface AgentClientService {

    AgentResponse sendMessage(AgentRequest request);

    AgentResponse invokeAgent(String agentId, String message, String sessionId);
}
