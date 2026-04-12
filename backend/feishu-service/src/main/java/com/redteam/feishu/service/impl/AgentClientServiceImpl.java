package com.redteam.feishu.service.impl;

import com.redteam.feishu.config.AgentConfig;
import com.redteam.feishu.dto.AgentRequest;
import com.redteam.feishu.dto.AgentResponse;
import com.redteam.feishu.service.AgentClientService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentClientServiceImpl implements AgentClientService {

    private final AgentConfig agentConfig;
    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public AgentResponse sendMessage(AgentRequest request) {
        try {
            String url = agentConfig.getServiceUrl() + "/agent/chat";
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<AgentRequest> entity = new HttpEntity<>(request, headers);
            
            log.info("Calling agent service: {} with request: {}", url, request);
            
            AgentResponse response = restTemplate.postForObject(url, entity, AgentResponse.class);
            log.info("Agent response: {}", response);
            
            return response;
        } catch (Exception e) {
            log.error("Error calling agent service", e);
            return AgentResponse.builder()
                    .status("error")
                    .message("服务调用失败: " + e.getMessage())
                    .build();
        }
    }

    @Override
    public AgentResponse invokeAgent(String agentId, String message, String sessionId) {
        AgentRequest request = AgentRequest.builder()
                .agentId(agentId)
                .message(message)
                .sessionId(sessionId)
                .build();
        return sendMessage(request);
    }
}
