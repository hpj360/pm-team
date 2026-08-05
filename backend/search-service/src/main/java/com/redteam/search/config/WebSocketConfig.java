package com.redteam.search.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocket 配置
 *
 * <p>基于 Spring WebSocket + STOMP 协议实现实时协同标注能力。
 * 客户端通过 SockJS 回退的 /ws 端点建立连接，使用 STOMP 帧进行通信：</p>
 * <ul>
 *   <li>应用前缀 /app —— 客户端发送消息至服务端 @MessageMapping 的目的地前缀</li>
 *   <li>广播频道 /topic —— 一对多广播（如 /topic/file/{fileId} 文件协同频道）</li>
 *   <li>点对点频道 /queue —— 一对一推送（配合 /user 前缀实现用户私有队列）</li>
 * </ul>
 *
 * <p>使用 SimpleBroker 内存代理，不依赖 RabbitMQ/ActiveMQ 等外部 broker，
 * 适用于单实例部署场景。</p>
 *
 * @author 红方团队
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    /**
     * 注册 STOMP 端点
     *
     * <p>客户端连接地址：ws://host:port/ws
     * 使用 SockJS 提供浏览器兼容性回退（WebSocket 不可用时降级为 XHR streaming/polling）。</p>
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }

    /**
     * 配置消息代理
     *
     * <p>/app 前缀的消息路由到 @MessageMapping 注解的方法；
     * /topic、/queue 为 SimpleBroker 管理的广播/队列目的地；
     * /user 前缀用于将消息路由到特定用户的私有队列。</p>
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.setApplicationDestinationPrefixes("/app");
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setUserDestinationPrefix("/user");
    }
}
