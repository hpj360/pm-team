package com.redteam.search.listener;

import com.redteam.common.api.dto.CollabMessage;
import com.redteam.search.dto.OnlineUser;
import com.redteam.search.service.OnlineUserManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket 连接事件监听器
 *
 * <p>监听 STOMP 会话的建立与断开事件，维护在线用户列表并向文件协同频道广播
 * USER_JOINED / USER_LEFT 消息。</p>
 *
 * <p>客户端在 STOMP CONNECT 帧中通过原生头携带 fileId、userId、username，
 * 服务端在连接事件中读取并登记。断开时通过 sessionId 反查用户信息并移除。</p>
 *
 * @author 红方团队
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class WebSocketEventListener {

    /**
     * 广播频道前缀：/topic/file/{fileId}
     */
    private static final String FILE_TOPIC_PREFIX = "/topic/file/";

    /**
     * 会话ID -> 在线用户信息（用于断开连接时反查用户归属）
     */
    private final Map<String, OnlineUser> sessionUsers = new ConcurrentHashMap<>();

    private final OnlineUserManager onlineUserManager;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * 处理会话连接事件
     *
     * <p>从 STOMP 头或会话属性中提取 fileId / userId / username，
     * 登记到在线用户管理器并广播 USER_JOINED 通知。</p>
     *
     * @param event 会话连接事件
     */
    @EventListener
    public void handleSessionConnect(SessionConnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = accessor.getSessionId();

        Long fileId = parseLong(getHeader(accessor, "fileId"));
        Long userId = parseLong(getHeader(accessor, "userId"));
        String username = getHeader(accessor, "username");

        // 非协同查看场景（缺少 fileId 或 userId）不登记
        if (fileId == null || userId == null) {
            log.debug("忽略非协同连接: sessionId={}, fileId={}, userId={}", sessionId, fileId, userId);
            return;
        }

        OnlineUser user = new OnlineUser();
        user.setUserId(userId);
        user.setUsername(username);
        user.setSessionId(sessionId);
        user.setFileId(fileId);
        user.setJoinedAt(Instant.now());

        sessionUsers.put(sessionId, user);
        onlineUserManager.userJoined(fileId, user);

        // 广播 USER_JOINED 通知
        CollabMessage msg = buildBaseMessage("USER_JOINED", fileId, userId, username);
        messagingTemplate.convertAndSend(FILE_TOPIC_PREFIX + fileId, msg);
        log.info("广播用户加入: fileId={}, userId={}, username={}", fileId, userId, username);
    }

    /**
     * 处理会话断开事件
     *
     * <p>通过 sessionId 反查用户信息，从在线列表移除并广播 USER_LEFT 通知。</p>
     *
     * @param event 会话断开事件
     */
    @EventListener
    public void handleSessionDisconnect(SessionDisconnectEvent event) {
        String sessionId = event.getSessionId();
        OnlineUser user = sessionUsers.remove(sessionId);
        if (user == null) {
            log.debug("断开连接的用户未登记: sessionId={}", sessionId);
            return;
        }

        onlineUserManager.userLeft(user.getFileId(), user.getUserId());

        // 广播 USER_LEFT 通知
        CollabMessage msg = buildBaseMessage("USER_LEFT", user.getFileId(),
                user.getUserId(), user.getUsername());
        messagingTemplate.convertAndSend(FILE_TOPIC_PREFIX + user.getFileId(), msg);
        log.info("广播用户离开: fileId={}, userId={}, username={}",
                user.getFileId(), user.getUserId(), user.getUsername());
    }

    // ==================== 私有方法 ====================

    /**
     * 从 STOMP 头或会话属性中读取指定键值
     *
     * <p>优先读取 STOMP 原生头（客户端 CONNECT 帧携带），其次回退到会话属性
     * （由 HandshakeInterceptor 从查询参数写入）。</p>
     *
     * @param accessor STOMP 头访问器
     * @param key      头名称
     * @return 头值（可能为 null）
     */
    private String getHeader(StompHeaderAccessor accessor, String key) {
        String value = accessor.getFirstNativeHeader(key);
        if (value != null) {
            return value;
        }
        Map<String, Object> attrs = accessor.getSessionAttributes();
        if (attrs != null) {
            Object attr = attrs.get(key);
            return attr != null ? attr.toString() : null;
        }
        return null;
    }

    /**
     * 安全解析 Long
     *
     * @param value 字符串值
     * @return Long 值，解析失败或入参为空时返回 null
     */
    private Long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 构建基础协同消息
     *
     * @param type     消息类型
     * @param fileId   文件ID
     * @param userId   用户ID
     * @param username 用户名
     * @return 协同消息
     */
    private CollabMessage buildBaseMessage(String type, Long fileId, Long userId, String username) {
        CollabMessage msg = new CollabMessage();
        msg.setType(type);
        msg.setFileId(fileId);
        msg.setUserId(userId);
        msg.setUsername(username);
        msg.setTimestamp(Instant.now().toString());
        return msg;
    }
}
