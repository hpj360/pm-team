package com.redteam.notification.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.redteam.common.exception.BusinessException;
import com.redteam.common.result.PageResult;
import com.redteam.notification.dto.BroadcastNotificationDTO;
import com.redteam.notification.dto.NotificationDTO;
import com.redteam.notification.dto.NotificationQueryDTO;
import com.redteam.notification.dto.NotificationStatsDTO;
import com.redteam.notification.dto.NotificationVO;
import com.redteam.notification.entity.NotificationEntity;
import com.redteam.notification.mapper.NotificationMapper;
import com.redteam.notification.service.impl.NotificationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 通知服务单元测试
 *
 * @author 红方团队
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NotificationServiceTest {

    @Mock
    private NotificationMapper notificationMapper;

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @InjectMocks
    private NotificationServiceImpl notificationService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(notificationService, "imDispatchTopic", "redteam.notification.im");
        ReflectionTestUtils.setField(notificationService, "mailFrom", "noreply@redteam.example.com");
        ReflectionTestUtils.setField(notificationService, "mailEnabled", true);
        ReflectionTestUtils.setField(notificationService, "imEnabled", true);
        // kafkaTemplate.send 返回一个非空 CompletableFuture，避免 NPE
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));
    }

    /**
     * 构造标准 DTO
     */
    private NotificationDTO buildDTO(String channel) {
        NotificationDTO dto = new NotificationDTO();
        dto.setUserId(1001L);
        dto.setTitle("任务已完成");
        dto.setContent("您提交的分析任务已完成");
        dto.setType("TASK");
        dto.setLevel("INFO");
        dto.setChannel(channel);
        dto.setRelatedId("task-uuid-1234");
        dto.setRelatedType("TASK");
        return dto;
    }

    // ==================== sendNotification ====================

    @Nested
    @DisplayName("sendNotification: 通道分发")
    class SendNotificationTests {

        @Test
        @DisplayName("IN_APP 通道：仅落库，不发邮件不投 IM")
        void sendNotification_inApp() {
            NotificationDTO dto = buildDTO("IN_APP");

            NotificationVO vo = notificationService.sendNotification(dto);

            assertNotNull(vo.getNotificationId());
            assertEquals(1001L, vo.getUserId());
            assertEquals("TASK", vo.getType());
            assertEquals("IN_APP", vo.getChannel());
            assertEquals(0, vo.getIsRead());

            verify(notificationMapper).insert(any(NotificationEntity.class));
            verifyNoInteractions(mailSender);
            verifyNoInteractions(kafkaTemplate);
        }

        @Test
        @DisplayName("EMAIL 通道：落库 + 发送邮件")
        void sendNotification_email() {
            NotificationDTO dto = buildDTO("EMAIL");

            NotificationVO vo = notificationService.sendNotification(dto);

            assertNotNull(vo);
            verify(notificationMapper).insert(any(NotificationEntity.class));
            ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
            verify(mailSender).send(captor.capture());
            assertEquals("任务已完成", captor.getValue().getSubject());
            assertEquals("您提交的分析任务已完成", captor.getValue().getText());
            verifyNoInteractions(kafkaTemplate);
        }

        @Test
        @DisplayName("IM 通道：落库 + 投递 Kafka")
        void sendNotification_im() {
            NotificationDTO dto = buildDTO("IM");

            NotificationVO vo = notificationService.sendNotification(dto);

            assertNotNull(vo);
            verify(notificationMapper).insert(any(NotificationEntity.class));
            verify(kafkaTemplate).send(eq("redteam.notification.im"), anyString(), anyString());
            verifyNoInteractions(mailSender);
        }

        @Test
        @DisplayName("ALL 通道：落库 + 发邮件 + 投 IM")
        void sendNotification_all() {
            NotificationDTO dto = buildDTO("ALL");

            notificationService.sendNotification(dto);

            verify(notificationMapper).insert(any(NotificationEntity.class));
            verify(mailSender).send(any(SimpleMailMessage.class));
            verify(kafkaTemplate).send(eq("redteam.notification.im"), anyString(), anyString());
        }

        @Test
        @DisplayName("未知通道：默认走站内信")
        void sendNotification_unknownChannel() {
            NotificationDTO dto = buildDTO("UNKNOWN_CHANNEL");

            NotificationVO vo = notificationService.sendNotification(dto);

            assertNotNull(vo);
            verify(notificationMapper).insert(any(NotificationEntity.class));
            verifyNoInteractions(mailSender);
            verifyNoInteractions(kafkaTemplate);
        }

        @Test
        @DisplayName("邮件通道关闭：跳过邮件发送")
        void sendNotification_emailDisabled() {
            ReflectionTestUtils.setField(notificationService, "mailEnabled", false);
            NotificationDTO dto = buildDTO("EMAIL");

            notificationService.sendNotification(dto);

            verify(notificationMapper).insert(any(NotificationEntity.class));
            verifyNoInteractions(mailSender);
        }

        @Test
        @DisplayName("IM 通道关闭：跳过 IM 投递")
        void sendNotification_imDisabled() {
            ReflectionTestUtils.setField(notificationService, "imEnabled", false);
            NotificationDTO dto = buildDTO("IM");

            notificationService.sendNotification(dto);

            verify(notificationMapper).insert(any(NotificationEntity.class));
            verifyNoInteractions(kafkaTemplate);
        }

        @Test
        @DisplayName("邮件发送异常：不影响主流程")
        void sendNotification_emailException_notPropagated() {
            doThrow(new RuntimeException("SMTP 不可用"))
                    .when(mailSender).send(any(SimpleMailMessage.class));
            NotificationDTO dto = buildDTO("EMAIL");

            NotificationVO vo = notificationService.sendNotification(dto);

            assertNotNull(vo);
            verify(notificationMapper).insert(any(NotificationEntity.class));
        }

        @Test
        @DisplayName("IM 投递异常：不影响主流程")
        void sendNotification_imException_notPropagated() {
            when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                    .thenThrow(new RuntimeException("Kafka 不可用"));
            NotificationDTO dto = buildDTO("IM");

            NotificationVO vo = notificationService.sendNotification(dto);

            assertNotNull(vo);
            verify(notificationMapper).insert(any(NotificationEntity.class));
        }

        @Test
        @DisplayName("实体字段正确填充：notificationId 为 UUID，isRead 为 0")
        void sendNotification_entityFieldsPopulated() {
            NotificationDTO dto = buildDTO("IN_APP");

            ArgumentCaptor<NotificationEntity> captor = ArgumentCaptor.forClass(NotificationEntity.class);
            notificationService.sendNotification(dto);

            verify(notificationMapper).insert(captor.capture());
            NotificationEntity saved = captor.getValue();
            assertNotNull(saved.getNotificationId());
            assertEquals(36, saved.getNotificationId().length()); // UUID 长度
            assertEquals(0, saved.getIsRead());
            assertEquals("task-uuid-1234", saved.getRelatedId());
            assertEquals("TASK", saved.getRelatedType());
        }
    }

    // ==================== getNotification ====================

    @Nested
    @DisplayName("getNotification: 查询通知详情")
    class GetNotificationTests {

        @Test
        @DisplayName("查询成功")
        void getNotification_success() {
            NotificationEntity entity = new NotificationEntity();
            entity.setId(1L);
            entity.setNotificationId("notif-123");
            entity.setUserId(1001L);
            entity.setTitle("标题");
            entity.setContent("内容");
            entity.setType("SYSTEM");
            entity.setLevel("INFO");
            entity.setChannel("IN_APP");
            entity.setIsRead(0);
            entity.setCreateTime(LocalDateTime.now());
            when(notificationMapper.selectOne(any())).thenReturn(entity);

            NotificationVO vo = notificationService.getNotification("notif-123");

            assertEquals("notif-123", vo.getNotificationId());
            assertEquals("标题", vo.getTitle());
            assertEquals("SYSTEM", vo.getType());
        }

        @Test
        @DisplayName("通知ID为空：抛参数异常")
        void getNotification_blankId_throwsException() {
            assertThrows(BusinessException.class, () -> notificationService.getNotification(""));
            assertThrows(BusinessException.class, () -> notificationService.getNotification(null));
        }

        @Test
        @DisplayName("通知不存在：抛业务异常")
        void getNotification_notFound_throwsException() {
            when(notificationMapper.selectOne(any())).thenReturn(null);
            assertThrows(BusinessException.class, () -> notificationService.getNotification("missing"));
        }
    }

    // ==================== listUserNotifications ====================

    @Nested
    @DisplayName("listUserNotifications: 分页查询")
    class ListUserNotificationsTests {

        @Test
        @DisplayName("查询成功：返回分页结果")
        @SuppressWarnings("unchecked")
        void listUserNotifications_success() {
            NotificationEntity entity = new NotificationEntity();
            entity.setNotificationId("notif-1");
            entity.setUserId(1001L);
            entity.setTitle("标题");
            entity.setContent("内容");
            entity.setType("TASK");
            entity.setLevel("INFO");
            entity.setChannel("IN_APP");
            entity.setIsRead(0);
            Page<NotificationEntity> page = new Page<>(1, 10);
            page.setTotal(1L);
            page.setRecords(List.of(entity));
            when(notificationMapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(page);

            NotificationQueryDTO query = new NotificationQueryDTO();
            query.setPageNum(1L);
            query.setPageSize(10L);
            PageResult<NotificationVO> result = notificationService.listUserNotifications(1001L, query);

            assertEquals(1L, result.getTotal());
            assertEquals(1, result.getRecords().size());
            assertEquals("notif-1", result.getRecords().get(0).getNotificationId());
        }

        @Test
        @DisplayName("带过滤条件：type/level/isRead/时间范围")
        @SuppressWarnings("unchecked")
        void listUserNotifications_withFilters() {
            Page<NotificationEntity> page = new Page<>(1, 10);
            page.setTotal(0L);
            page.setRecords(Collections.emptyList());
            when(notificationMapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(page);

            NotificationQueryDTO query = new NotificationQueryDTO();
            query.setType("FILE");
            query.setLevel("WARN");
            query.setIsRead(0);
            query.setStartTime(LocalDateTime.now().minusDays(1));
            query.setEndTime(LocalDateTime.now());

            PageResult<NotificationVO> result = notificationService.listUserNotifications(1001L, query);

            assertEquals(0L, result.getTotal());
            assertTrue(result.getRecords().isEmpty());
        }

        @Test
        @DisplayName("null 查询条件：使用默认分页")
        @SuppressWarnings("unchecked")
        void listUserNotifications_nullQuery_usesDefaults() {
            Page<NotificationEntity> page = new Page<>(1, 10);
            page.setTotal(0L);
            page.setRecords(Collections.emptyList());
            when(notificationMapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(page);

            PageResult<NotificationVO> result = notificationService.listUserNotifications(1001L, null);

            assertEquals(0L, result.getTotal());
        }

        @Test
        @DisplayName("userId 为空：抛参数异常")
        void listUserNotifications_nullUserId_throwsException() {
            assertThrows(BusinessException.class,
                    () -> notificationService.listUserNotifications(null, new NotificationQueryDTO()));
        }

        @Test
        @DisplayName("pageSize 超过 100：被截断为 100")
        @SuppressWarnings("unchecked")
        void listUserNotifications_pageSizeCapped() {
            Page<NotificationEntity> page = new Page<>(1, 100);
            page.setTotal(0L);
            page.setRecords(Collections.emptyList());
            when(notificationMapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(page);

            NotificationQueryDTO query = new NotificationQueryDTO();
            query.setPageSize(500L);
            notificationService.listUserNotifications(1001L, query);

            ArgumentCaptor<Page<NotificationEntity>> captor = ArgumentCaptor.forClass(Page.class);
            verify(notificationMapper).selectPage(captor.capture(), any(Wrapper.class));
            assertEquals(100L, captor.getValue().getSize());
        }
    }

    // ==================== markAsRead ====================

    @Nested
    @DisplayName("markAsRead: 标记已读")
    class MarkAsReadTests {

        @Test
        @DisplayName("标记成功")
        void markAsRead_success() {
            NotificationEntity entity = new NotificationEntity();
            entity.setId(1L);
            entity.setNotificationId("notif-1");
            entity.setIsRead(0);
            when(notificationMapper.selectOne(any())).thenReturn(entity);
            when(notificationMapper.update(eq(null), any())).thenReturn(1);

            assertDoesNotThrow(() -> notificationService.markAsRead("notif-1"));
            verify(notificationMapper).update(eq(null), any());
        }

        @Test
        @DisplayName("通知ID为空：抛参数异常")
        void markAsRead_blankId_throwsException() {
            assertThrows(BusinessException.class, () -> notificationService.markAsRead(""));
        }

        @Test
        @DisplayName("通知不存在：抛业务异常")
        void markAsRead_notFound_throwsException() {
            when(notificationMapper.selectOne(any())).thenReturn(null);
            assertThrows(BusinessException.class, () -> notificationService.markAsRead("missing"));
        }

        @Test
        @DisplayName("已读通知：跳过更新")
        void markAsRead_alreadyRead_skips() {
            NotificationEntity entity = new NotificationEntity();
            entity.setId(1L);
            entity.setNotificationId("notif-1");
            entity.setIsRead(1);
            when(notificationMapper.selectOne(any())).thenReturn(entity);

            notificationService.markAsRead("notif-1");

            verify(notificationMapper, never()).update(eq(null), any());
        }
    }

    // ==================== markAllAsRead ====================

    @Nested
    @DisplayName("markAllAsRead: 全部已读")
    class MarkAllAsReadTests {

        @Test
        @DisplayName("全部已读成功")
        void markAllAsRead_success() {
            when(notificationMapper.update(eq(null), any())).thenReturn(5);
            assertDoesNotThrow(() -> notificationService.markAllAsRead(1001L));
            verify(notificationMapper).update(eq(null), any());
        }

        @Test
        @DisplayName("userId 为空：抛参数异常")
        void markAllAsRead_nullUserId_throwsException() {
            assertThrows(BusinessException.class, () -> notificationService.markAllAsRead(null));
        }
    }

    // ==================== deleteNotification ====================

    @Nested
    @DisplayName("deleteNotification: 删除通知")
    class DeleteNotificationTests {

        @Test
        @DisplayName("删除成功")
        void deleteNotification_success() {
            NotificationEntity entity = new NotificationEntity();
            entity.setId(1L);
            entity.setNotificationId("notif-1");
            when(notificationMapper.selectOne(any())).thenReturn(entity);
            when(notificationMapper.deleteById(1L)).thenReturn(1);

            assertDoesNotThrow(() -> notificationService.deleteNotification("notif-1"));
            verify(notificationMapper).deleteById(1L);
        }

        @Test
        @DisplayName("通知ID为空：抛参数异常")
        void deleteNotification_blankId_throwsException() {
            assertThrows(BusinessException.class, () -> notificationService.deleteNotification(null));
        }

        @Test
        @DisplayName("通知不存在：抛业务异常")
        void deleteNotification_notFound_throwsException() {
            when(notificationMapper.selectOne(any())).thenReturn(null);
            assertThrows(BusinessException.class, () -> notificationService.deleteNotification("missing"));
        }
    }

    // ==================== getUnreadCount ====================

    @Nested
    @DisplayName("getUnreadCount: 未读统计")
    class GetUnreadCountTests {

        @Test
        @DisplayName("返回未读数量")
        void getUnreadCount_success() {
            when(notificationMapper.selectCount(any())).thenReturn(3L);
            Integer count = notificationService.getUnreadCount(1001L);
            assertEquals(3, count);
        }

        @Test
        @DisplayName("selectCount 返回 null：返回 0")
        void getUnreadCount_nullResult_returnsZero() {
            when(notificationMapper.selectCount(any())).thenReturn(null);
            Integer count = notificationService.getUnreadCount(1001L);
            assertEquals(0, count);
        }

        @Test
        @DisplayName("userId 为空：抛参数异常")
        void getUnreadCount_nullUserId_throwsException() {
            assertThrows(BusinessException.class, () -> notificationService.getUnreadCount(null));
        }
    }

    // ==================== broadcastNotification ====================

    @Nested
    @DisplayName("broadcastNotification: 广播通知")
    class BroadcastNotificationTests {

        @Test
        @DisplayName("广播成功：为每个用户发送一条通知")
        void broadcast_success() {
            BroadcastNotificationDTO dto = new BroadcastNotificationDTO();
            dto.setUserIds(List.of(1001L, 1002L, 1003L));
            dto.setSenderId(0L);
            dto.setTitle("系统公告");
            dto.setContent("系统将于今晚维护");
            dto.setType("SYSTEM");
            dto.setLevel("WARN");
            dto.setChannel("IN_APP");

            List<NotificationVO> results = notificationService.broadcastNotification(dto);

            assertNotNull(results);
            assertEquals(3, results.size());
            verify(notificationMapper, times(3)).insert(any(NotificationEntity.class));
        }

        @Test
        @DisplayName("广播部分失败：不影响其他用户")
        void broadcast_partialFailure() {
            BroadcastNotificationDTO dto = new BroadcastNotificationDTO();
            dto.setUserIds(List.of(1001L, 1002L));
            dto.setTitle("公告");
            dto.setContent("内容");
            dto.setType("SYSTEM");
            dto.setLevel("INFO");
            dto.setChannel("IN_APP");

            // 第一次插入抛异常，第二次成功
            when(notificationMapper.insert(any(NotificationEntity.class)))
                    .thenThrow(new RuntimeException("DB 异常"))
                    .thenReturn(1);

            List<NotificationVO> results = notificationService.broadcastNotification(dto);

            assertNotNull(results);
            assertEquals(1, results.size());
        }

        @Test
        @DisplayName("广播空列表：返回空结果")
        void broadcast_emptyList() {
            BroadcastNotificationDTO dto = new BroadcastNotificationDTO();
            dto.setUserIds(Collections.emptyList());
            dto.setTitle("公告");
            dto.setContent("内容");
            dto.setType("SYSTEM");
            dto.setLevel("INFO");
            dto.setChannel("IN_APP");

            List<NotificationVO> results = notificationService.broadcastNotification(dto);

            assertNotNull(results);
            assertTrue(results.isEmpty());
            verify(notificationMapper, never()).insert(any(NotificationEntity.class));
        }
    }

    // ==================== getNotificationStats ====================

    @Nested
    @DisplayName("getNotificationStats: 通知统计")
    class GetNotificationStatsTests {

        @Test
        @DisplayName("统计成功：返回各维度数据")
        void stats_success() {
            // selectCount 多次调用：total + unread + read + 5类型 + 4级别 + 4通道 + 3状态 = 19 次
            when(notificationMapper.selectCount(any())).thenReturn(10L);

            NotificationStatsDTO stats = notificationService.getNotificationStats(1001L);

            assertNotNull(stats);
            assertEquals(10L, stats.getTotal());
            assertEquals(10L, stats.getUnreadCount());
            assertEquals(10L, stats.getReadCount());
            assertNotNull(stats.getByType());
            assertEquals(5, stats.getByType().size());
            assertNotNull(stats.getByLevel());
            assertEquals(4, stats.getByLevel().size());
            assertNotNull(stats.getByChannel());
            assertEquals(4, stats.getByChannel().size());
            assertNotNull(stats.getBySendStatus());
            assertEquals(3, stats.getBySendStatus().size());
        }

        @Test
        @DisplayName("全局统计：userId 为 null")
        void stats_global() {
            when(notificationMapper.selectCount(any())).thenReturn(0L);

            NotificationStatsDTO stats = notificationService.getNotificationStats(null);

            assertNotNull(stats);
            assertEquals(0L, stats.getTotal());
            assertEquals(0.0, stats.getReadRate());
        }

        @Test
        @DisplayName("selectCount 返回 null：使用 0 兜底")
        void stats_nullCount() {
            when(notificationMapper.selectCount(any())).thenReturn(null);

            NotificationStatsDTO stats = assertDoesNotThrow(
                    () -> notificationService.getNotificationStats(1001L));

            assertNotNull(stats);
            assertEquals(0L, stats.getTotal());
            assertEquals(0L, stats.getUnreadCount());
            assertEquals(0L, stats.getReadCount());
            assertEquals(0.0, stats.getReadRate());
        }
    }

    // ==================== cleanupExpired ====================

    @Nested
    @DisplayName("cleanupExpired: 清理过期通知")
    class CleanupExpiredTests {

        @Test
        @DisplayName("清理成功：删除过期未读通知")
        void cleanup_success() {
            NotificationEntity e1 = new NotificationEntity();
            e1.setId(1L);
            e1.setNotificationId("n1");
            NotificationEntity e2 = new NotificationEntity();
            e2.setId(2L);
            e2.setNotificationId("n2");
            when(notificationMapper.selectList(any())).thenReturn(List.of(e1, e2));
            when(notificationMapper.deleteBatchIds(anyList())).thenReturn(2);

            Integer count = notificationService.cleanupExpired();

            assertEquals(2, count);
            verify(notificationMapper).deleteBatchIds(anyList());
        }

        @Test
        @DisplayName("无过期通知：返回 0")
        void cleanup_empty() {
            when(notificationMapper.selectList(any())).thenReturn(Collections.emptyList());

            Integer count = notificationService.cleanupExpired();

            assertEquals(0, count);
            verify(notificationMapper, never()).deleteBatchIds(anyList());
        }
    }

    // ==================== retryFailed ====================

    @Nested
    @DisplayName("retryFailed: 重试失败通知")
    class RetryFailedTests {

        @Test
        @DisplayName("重试 EMAIL 通道成功：状态置为 SENT")
        void retry_emailSuccess() {
            NotificationEntity entity = new NotificationEntity();
            entity.setId(1L);
            entity.setNotificationId("n1");
            entity.setUserId(1001L);
            entity.setChannel("EMAIL");
            entity.setSendStatus("FAILED");
            entity.setRetryCount(1);
            entity.setTitle("标题");
            entity.setContent("内容");
            when(notificationMapper.selectOne(any())).thenReturn(entity);
            when(notificationMapper.updateById(any())).thenReturn(1);

            NotificationVO vo = notificationService.retryFailed("n1");

            assertNotNull(vo);
            assertEquals("SENT", vo.getSendStatus());
            assertEquals(2, vo.getRetryCount());
            verify(mailSender).send(any(SimpleMailMessage.class));
        }

        @Test
        @DisplayName("重试 IM 通道成功：状态置为 SENT")
        void retry_imSuccess() {
            NotificationEntity entity = new NotificationEntity();
            entity.setId(1L);
            entity.setNotificationId("n1");
            entity.setUserId(1001L);
            entity.setChannel("IM");
            entity.setSendStatus("FAILED");
            entity.setRetryCount(0);
            when(notificationMapper.selectOne(any())).thenReturn(entity);
            when(notificationMapper.updateById(any())).thenReturn(1);

            NotificationVO vo = notificationService.retryFailed("n1");

            assertEquals("SENT", vo.getSendStatus());
            assertEquals(1, vo.getRetryCount());
            verify(kafkaTemplate).send(anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("重试 IN_APP 通道：直接标记为 SENT")
        void retry_inAppDirectSuccess() {
            NotificationEntity entity = new NotificationEntity();
            entity.setId(1L);
            entity.setNotificationId("n1");
            entity.setUserId(1001L);
            entity.setChannel("IN_APP");
            entity.setSendStatus("FAILED");
            entity.setRetryCount(0);
            when(notificationMapper.selectOne(any())).thenReturn(entity);
            when(notificationMapper.updateById(any())).thenReturn(1);

            NotificationVO vo = notificationService.retryFailed("n1");

            assertEquals("SENT", vo.getSendStatus());
            assertEquals(1, vo.getRetryCount());
            verifyNoInteractions(mailSender);
            verifyNoInteractions(kafkaTemplate);
        }

        @Test
        @DisplayName("通知ID为空：抛参数异常")
        void retry_blankId_throwsException() {
            assertThrows(BusinessException.class, () -> notificationService.retryFailed(""));
            assertThrows(BusinessException.class, () -> notificationService.retryFailed(null));
        }

        @Test
        @DisplayName("通知不存在：抛业务异常")
        void retry_notFound_throwsException() {
            when(notificationMapper.selectOne(any())).thenReturn(null);
            assertThrows(BusinessException.class, () -> notificationService.retryFailed("missing"));
        }

        @Test
        @DisplayName("通知状态非 FAILED：抛业务异常")
        void retry_notFailedStatus_throwsException() {
            NotificationEntity entity = new NotificationEntity();
            entity.setId(1L);
            entity.setNotificationId("n1");
            entity.setSendStatus("SENT");
            when(notificationMapper.selectOne(any())).thenReturn(entity);

            assertThrows(BusinessException.class, () -> notificationService.retryFailed("n1"));
        }

        @Test
        @DisplayName("重试次数达上限：抛业务异常")
        void retry_maxCountReached_throwsException() {
            NotificationEntity entity = new NotificationEntity();
            entity.setId(1L);
            entity.setNotificationId("n1");
            entity.setChannel("EMAIL");
            entity.setSendStatus("FAILED");
            entity.setRetryCount(3);
            when(notificationMapper.selectOne(any())).thenReturn(entity);

            assertThrows(BusinessException.class, () -> notificationService.retryFailed("n1"));
        }

        @Test
        @DisplayName("重试邮件仍失败：状态保持 FAILED，重试次数递增")
        void retry_emailStillFails() {
            NotificationEntity entity = new NotificationEntity();
            entity.setId(1L);
            entity.setNotificationId("n1");
            entity.setUserId(1001L);
            entity.setChannel("EMAIL");
            entity.setSendStatus("FAILED");
            entity.setRetryCount(0);
            entity.setTitle("标题");
            entity.setContent("内容");
            when(notificationMapper.selectOne(any())).thenReturn(entity);
            when(notificationMapper.updateById(any())).thenReturn(1);
            doThrow(new RuntimeException("SMTP 故障"))
                    .when(mailSender).send(any(SimpleMailMessage.class));

            NotificationVO vo = notificationService.retryFailed("n1");

            assertEquals("FAILED", vo.getSendStatus());
            assertEquals(1, vo.getRetryCount());
        }
    }
}
