package com.redteam.common.api.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 协同标注消息模型
 *
 * <p>用于 WebSocket 实时协同场景下，在多用户查看同一文件时同步标注操作、
 * 光标位置以及用户加入/离开事件。消息通过 STOMP 协议在客户端与服务端之间传递，
 * 服务端接收后按类型处理并广播至订阅该文件频道的所有在线用户。</p>
 *
 * <p>消息类型（type）说明：</p>
 * <ul>
 *   <li>TAG_ADDED —— 新增标注（打标），携带 tagId/tagCode</li>
 *   <li>TAG_REMOVED —— 移除标注（取消打标），携带 tagId</li>
 *   <li>USER_JOINED —— 用户加入文件查看，由连接事件监听器发出</li>
 *   <li>USER_LEFT —— 用户离开文件查看，由断开事件监听器发出</li>
 *   <li>CURSOR —— 光标位置同步，仅广播不落库</li>
 * </ul>
 *
 * @author 红方团队
 */
@Data
public class CollabMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 消息类型：TAG_ADDED / TAG_REMOVED / USER_JOINED / USER_LEFT / CURSOR
     */
    private String type;

    /**
     * 文件ID
     */
    private Long fileId;

    /**
     * 操作用户ID
     */
    private Long userId;

    /**
     * 操作用户名
     */
    private String username;

    /**
     * 标签ID（TAG_ADDED / TAG_REMOVED 时使用）
     */
    private Long tagId;

    /**
     * 标签编码
     */
    private String tagCode;

    /**
     * 标签操作方向：ADD / REMOVE
     */
    private String tagAction;

    /**
     * 消息时间戳（ISO-8601 格式字符串）
     */
    private String timestamp;
}
