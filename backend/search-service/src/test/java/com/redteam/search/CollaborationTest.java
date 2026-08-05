package com.redteam.search;

import com.redteam.common.api.dto.CollabMessage;
import com.redteam.common.service.TagService;
import com.redteam.search.controller.CollaborationController;
import com.redteam.search.dto.OnlineUser;
import com.redteam.search.service.OnlineUserManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 实时协同标注单元测试
 *
 * <p>覆盖 {@link OnlineUserManager} 在线用户管理（加入/离开/多用户/获取列表）与
 * {@link CollaborationController} 标注消息处理（打标/取消打标广播）。</p>
 *
 * @author 红方团队
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CollaborationTest {

    private OnlineUserManager onlineUserManager;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private TagService tagService;

    private CollaborationController collaborationController;

    @BeforeEach
    void setUp() {
        // 手动构造控制器：注入 mock 的 messagingTemplate/tagService + 真实的 OnlineUserManager
        onlineUserManager = new OnlineUserManager();
        collaborationController = new CollaborationController(messagingTemplate, tagService, onlineUserManager);
    }

    // ==================== 用例1: 用户加入/离开 ====================

    /**
     * 用户加入文件后在线人数 +1，离开后归零
     */
    @Test
    @DisplayName("OnlineUserManager: 用户加入/离开")
    void testOnlineUserManager_JoinLeave() {
        Long fileId = 100L;
        OnlineUser user = buildUser(1L, "alice", "session-1");

        // 初始无在线用户
        assertEquals(0, onlineUserManager.getViewerCount(fileId), "初始在线人数应为 0");

        // 用户加入
        onlineUserManager.userJoined(fileId, user);
        assertEquals(1, onlineUserManager.getViewerCount(fileId), "加入后在线人数应为 1");

        // 用户离开
        onlineUserManager.userLeft(fileId, 1L);
        assertEquals(0, onlineUserManager.getViewerCount(fileId), "离开后在线人数应为 0");
    }

    // ==================== 用例2: 多用户查看同一文件 ====================

    /**
     * 多个不同用户加入同一文件，在线人数应正确累加
     */
    @Test
    @DisplayName("OnlineUserManager: 多用户查看同一文件")
    void testOnlineUserManager_MultipleViewers() {
        Long fileId = 200L;

        onlineUserManager.userJoined(fileId, buildUser(1L, "alice", "s1"));
        onlineUserManager.userJoined(fileId, buildUser(2L, "bob", "s2"));
        onlineUserManager.userJoined(fileId, buildUser(3L, "carol", "s3"));

        assertEquals(3, onlineUserManager.getViewerCount(fileId), "3 个不同用户加入后在线人数应为 3");

        // 移除一个用户，在线人数应递减
        onlineUserManager.userLeft(fileId, 2L);
        assertEquals(2, onlineUserManager.getViewerCount(fileId), "移除 bob 后在线人数应为 2");
    }

    // ==================== 用例3: 标注消息广播（TAG_ADDED） ====================

    /**
     * TAG_ADDED 消息应触发打标落库并广播到文件频道
     */
    @Test
    @DisplayName("CollabMessage: TAG_ADDED 标注消息广播")
    void testCollabMessage_TagAdded() {
        Long fileId = 300L;
        Long tagId = 5L;

        CollabMessage msg = new CollabMessage();
        msg.setType("TAG_ADDED");
        msg.setUserId(1L);
        msg.setUsername("alice");
        msg.setTagId(tagId);
        msg.setTagCode("L1.FILE.TYPE.PDF");

        collaborationController.handleCollabMessage(fileId, msg);

        // 验证调用 tagService.addFileTags 落库
        verify(tagService, times(1)).addFileTags(eq(fileId), eq(List.of(tagId)), eq("MANUAL"));

        // 验证广播到 /topic/file/{fileId}
        ArgumentCaptor<CollabMessage> captor = ArgumentCaptor.forClass(CollabMessage.class);
        verify(messagingTemplate, times(1)).convertAndSend(eq("/topic/file/" + fileId), captor.capture());

        CollabMessage sent = captor.getValue();
        assertEquals("TAG_ADDED", sent.getType(), "广播消息类型应为 TAG_ADDED");
        assertEquals(fileId, sent.getFileId(), "广播消息 fileId 应为入参");
        assertEquals(tagId, sent.getTagId(), "广播消息 tagId 应为入参");
        assertEquals("ADD", sent.getTagAction(), "打标操作方向应为 ADD");
        assertNotNull(sent.getTimestamp(), "时间戳不应为空");
    }

    // ==================== 用例4: 获取在线列表 ====================

    /**
     * getViewers 应返回当前在线用户集合，不存在的文件返回空集合
     */
    @Test
    @DisplayName("OnlineUserManager: 获取在线列表")
    void testOnlineUserManager_GetViewers() {
        Long fileId = 400L;

        onlineUserManager.userJoined(fileId, buildUser(1L, "alice", "s1"));
        onlineUserManager.userJoined(fileId, buildUser(2L, "bob", "s2"));

        Set<OnlineUser> viewers = onlineUserManager.getViewers(fileId);
        assertEquals(2, viewers.size(), "在线列表应包含 2 个用户");
        assertTrue(viewers.stream().anyMatch(u -> u.getUserId().equals(1L)), "应包含 alice");
        assertTrue(viewers.stream().anyMatch(u -> u.getUserId().equals(2L)), "应包含 bob");

        // 不存在的文件返回空集合
        Set<OnlineUser> empty = onlineUserManager.getViewers(999L);
        assertNotNull(empty, "不存在文件的在线列表不应为 null");
        assertTrue(empty.isEmpty(), "不存在文件的在线列表应为空");
    }

    // ==================== 用例5: 取消标注广播（TAG_REMOVED） ====================

    /**
     * TAG_REMOVED 消息应触发取消打标并广播
     */
    @Test
    @DisplayName("CollabMessage: TAG_REMOVED 取消标注广播")
    void testCollabMessage_TagRemoved() {
        Long fileId = 500L;
        Long tagId = 8L;

        CollabMessage msg = new CollabMessage();
        msg.setType("TAG_REMOVED");
        msg.setUserId(2L);
        msg.setUsername("bob");
        msg.setTagId(tagId);

        collaborationController.handleCollabMessage(fileId, msg);

        // 验证调用 tagService.removeFileTag 取消打标
        verify(tagService, times(1)).removeFileTag(eq(fileId), eq(tagId));

        // 验证广播
        ArgumentCaptor<CollabMessage> captor = ArgumentCaptor.forClass(CollabMessage.class);
        verify(messagingTemplate, times(1)).convertAndSend(eq("/topic/file/" + fileId), captor.capture());

        CollabMessage sent = captor.getValue();
        assertEquals("TAG_REMOVED", sent.getType(), "广播消息类型应为 TAG_REMOVED");
        assertEquals("REMOVE", sent.getTagAction(), "取消打标方向应为 REMOVE");
        assertEquals(fileId, sent.getFileId(), "广播消息 fileId 应为入参");
    }

    // ==================== 辅助方法 ====================

    /**
     * 构造测试用在线用户
     */
    private OnlineUser buildUser(Long userId, String username, String sessionId) {
        OnlineUser user = new OnlineUser();
        user.setUserId(userId);
        user.setUsername(username);
        user.setSessionId(sessionId);
        user.setJoinedAt(Instant.now());
        return user;
    }
}
