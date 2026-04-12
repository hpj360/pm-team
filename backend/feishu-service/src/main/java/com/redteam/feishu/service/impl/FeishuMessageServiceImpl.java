package com.redteam.feishu.service.impl;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.lark.oa.service.message.MessageService;
import com.lark.oa.service.message.model.CreateMessageReq;
import com.lark.oa.service.message.model.CreateMessageResp;
import com.redteam.feishu.config.AgentConfig;
import com.redteam.feishu.config.FeishuConfig;
import com.redteam.feishu.dto.AgentRequest;
import com.redteam.feishu.dto.AgentResponse;
import com.redteam.feishu.service.AgentClientService;
import com.redteam.feishu.service.FeishuMessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class FeishuMessageServiceImpl implements FeishuMessageService {

    private final FeishuConfig feishuConfig;
    private final AgentConfig agentConfig;
    private final AgentClientService agentClientService;
    private final ConcurrentHashMap<String, String> sessionContext = new ConcurrentHashMap<>();

    @Override
    public Object handleEvent(String body) {
        try {
            JSONObject event = JSONUtil.parseObj(body);
            String eventType = event.getByPath("header.event_type", String.class);
            
            log.info("Processing event type: {}", eventType);

            if ("im.message.receive_v1".equals(eventType)) {
                return handleMessageEvent(event);
            }

            return Map.of("code", 0, "msg", "success");
        } catch (Exception e) {
            log.error("Error handling event", e);
            return Map.of("code", -1, "msg", e.getMessage());
        }
    }

    @Override
    public Object handleChallenge(String body) {
        JSONObject challenge = JSONUtil.parseObj(body);
        String challengeToken = challenge.getStr("challenge");
        return Map.of("challenge", challengeToken);
    }

    private Object handleMessageEvent(JSONObject event) {
        JSONObject eventBody = event.getJSONObject("body");
        if (eventBody == null) {
            return Map.of("code", -1, "msg", "Invalid event body");
        }

        JSONObject message = eventBody.getJSONObject("event").getJSONObject("message");
        if (message == null) {
            return Map.of("code", -1, "msg", "No message found");
        }

        String messageId = message.getStr("message_id");
        String content = message.getStr("content");
        String msgType = message.getStr("message_type");
        String senderId = eventBody.getJSONObject("event").getJSONObject("sender").getJSONObject("sender_id").getStr("union_id");

        log.info("Received message - id: {}, type: {}, content: {}", messageId, msgType, content);

        if (!"text".equals(msgType)) {
            sendMessage(senderId, "union_id", "暂只支持文本消息");
            return Map.of("code", 0);
        }

        JSONObject contentJson = JSONUtil.parseObj(content);
        String text = contentJson.getStr("text");

        String sessionId = "session_" + senderId;
        String context = sessionContext.getOrDefault(sessionId, "");

        AgentRequest request = AgentRequest.builder()
                .sessionId(sessionId)
                .userId(senderId)
                .message(text)
                .agentId(agentConfig.getDefaultAgent())
                .context(context)
                .build();

        try {
            AgentResponse response = agentClientService.sendMessage(request);
            
            if (response != null && response.getMessage() != null) {
                sessionContext.put(sessionId, response.getContext());
                sendMessage(senderId, "union_id", response.getMessage());
            } else {
                sendMessage(senderId, "union_id", "处理请求时出现错误，请稍后重试");
            }
        } catch (Exception e) {
            log.error("Error calling agent service", e);
            sendMessage(senderId, "union_id", "服务暂时不可用，请稍后重试");
        }

        return Map.of("code", 0);
    }

    @Override
    public void sendMessage(String receiveId, String receiveIdType, String message) {
        try {
            Map<String, Object> content = new HashMap<>();
            content.put("text", message);
            String contentStr = JSONUtil.toJsonStr(content);

            CreateMessageReq req = CreateMessageReq.builder()
                    .receiveIdType(receiveIdType)
                    .receiveId(receiveId)
                    .msgType("text")
                    .content(contentStr)
                    .build();

            log.info("Sending message to {}: {}", receiveId, message);
        } catch (Exception e) {
            log.error("Error sending message", e);
        }
    }
}
