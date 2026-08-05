package com.redteam.search.controller;

import com.redteam.common.api.dto.CollabMessage;
import com.redteam.common.result.Result;
import com.redteam.common.service.TagService;
import com.redteam.search.dto.OnlineUser;
import com.redteam.search.service.OnlineUserManager;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * 实时协同控制器
 *
 * <p>承载两类入口：</p>
 * <ol>
 *   <li>STOMP 消息映射 —— 客户端发送至 /app/collab/{fileId} 的标注操作消息，
 *       服务端按类型处理（打标/取消打标）后广播到 /topic/file/{fileId} 频道</li>
 *   <li>REST 接口 —— 查询指定文件的在线查看者列表</li>
 * </ol>
 *
 * @author 红方团队
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "实时协同", description = "文件协同标注与在线用户管理接口")
public class CollaborationController {

    /**
     * 文件协同频道前缀
     */
    private static final String FILE_TOPIC_PREFIX = "/topic/file/";

    /**
     * 默认标签来源（协同打标）
     */
    private static final String TAG_SOURCE = "MANUAL";

    private final SimpMessagingTemplate messagingTemplate;
    private final TagService tagService;
    private final OnlineUserManager onlineUserManager;

    /**
     * 处理协同标注消息
     *
     * <p>客户端发送 STOMP 消息至 /app/collab/{fileId}，消息体为 {@link CollabMessage}。
     * 根据 type 执行相应操作并广播给所有订阅 /topic/file/{fileId} 的用户：</p>
     * <ul>
     *   <li>TAG_ADDED —— 调用 {@link TagService#addFileTags} 落库后广播</li>
     *   <li>TAG_REMOVED —— 调用 {@link TagService#removeFileTag} 取消打标后广播</li>
     *   <li>CURSOR / 其他 —— 直接广播，不落库</li>
     * </ul>
     *
     * @param fileId  文件ID（来自目的地路径变量）
     * @param message 协同消息
     */
    @MessageMapping("/collab/{fileId}")
    public void handleCollabMessage(@DestinationVariable Long fileId, CollabMessage message) {
        // 填充文件ID与时间戳
        message.setFileId(fileId);
        message.setTimestamp(Instant.now().toString());

        String type = message.getType();
        if ("TAG_ADDED".equals(type)) {
            // 新增标注：落库 + 广播
            if (message.getTagId() != null) {
                tagService.addFileTags(fileId, List.of(message.getTagId()), TAG_SOURCE);
            }
            message.setTagAction("ADD");
            log.info("协同打标: fileId={}, tagId={}, userId={}", fileId, message.getTagId(), message.getUserId());
        } else if ("TAG_REMOVED".equals(type)) {
            // 取消标注：落库 + 广播
            if (message.getTagId() != null) {
                tagService.removeFileTag(fileId, message.getTagId());
            }
            message.setTagAction("REMOVE");
            log.info("协同取消打标: fileId={}, tagId={}, userId={}", fileId, message.getTagId(), message.getUserId());
        }

        // 广播给所有订阅该文件的用户
        messagingTemplate.convertAndSend(FILE_TOPIC_PREFIX + fileId, message);
    }

    /**
     * 获取指定文件的在线用户列表
     *
     * @param fileId 文件ID
     * @return 在线用户列表
     */
    @GetMapping("/api/collab/files/{fileId}/online")
    @Operation(summary = "获取在线用户列表", description = "返回当前正在查看指定文件的在线用户列表")
    public Result<List<OnlineUser>> getOnlineUsers(@PathVariable Long fileId) {
        Set<OnlineUser> viewers = onlineUserManager.getViewers(fileId);
        return Result.success(List.copyOf(viewers));
    }

    /**
     * 获取指定文件的在线人数
     *
     * @param fileId 文件ID
     * @return 在线人数
     */
    @GetMapping("/api/collab/files/{fileId}/online/count")
    @Operation(summary = "获取在线人数", description = "返回当前正在查看指定文件的在线用户数量")
    public Result<Integer> getOnlineCount(@PathVariable Long fileId) {
        return Result.success(onlineUserManager.getViewerCount(fileId));
    }
}
