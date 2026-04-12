package com.redteam.feishu.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentRequest {

    private String sessionId;
    private String userId;
    private String message;
    private String agentId;
    private String context;
}
