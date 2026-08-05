package com.redteam.search.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.Instant;

/**
 * 在线用户信息
 *
 * <p>描述当前正在查看某个文件的在线用户，由 {@code OnlineUserManager} 在内存中维护。
 * 同一文件下按 userId 去重，断开连接时按 sessionId 定位并移除。</p>
 *
 * @author 红方团队
 */
@Data
public class OnlineUser implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 用户名
     */
    private String username;

    /**
     * WebSocket 会话ID（用于断开连接时定位用户）
     */
    private String sessionId;

    /**
     * 正在查看的文件ID
     */
    private Long fileId;

    /**
     * 加入时间
     */
    private Instant joinedAt;
}
