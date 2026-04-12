package com.redteam.feishu.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentResponse {

    private String sessionId;
    private String message;
    private String status;
    private String agentId;
    private Object data;
}
