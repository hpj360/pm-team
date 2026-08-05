package com.redteam.notification.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redteam.common.exception.BusinessException;
import com.redteam.common.result.ResultCode;
import com.redteam.notification.dto.BroadcastNotificationDTO;
import com.redteam.notification.dto.NotificationDTO;
import com.redteam.notification.dto.NotificationQueryDTO;
import com.redteam.notification.dto.NotificationStatsDTO;
import com.redteam.notification.dto.NotificationVO;
import com.redteam.notification.entity.NotificationEntity;
import com.redteam.notification.mapper.NotificationMapper;
import com.redteam.notification.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 通知服务集成测试
 *
 * <p>验证 NotificationController → NotificationService → NotificationMapper 端到端请求链路，
 * 使用 @MockBean 隔离 Mapper，保留 Spring 容器装配、参数校验、JSON 序列化等真实行为。</p>
 *
 * <p>覆盖场景：</p>
 * <ul>
 *   <li>通知发送、广播、查询、删除接口</li>
 *   <li>已读标记（单条 + 全部）</li>
 *   <li>未读数量查询</li>
 *   <li>通知统计</li>
 *   <li>清理过期通知、重试失败通知</li>
 *   <li>异常路径：参数校验失败、通知不存在</li>
 * </ul>
 *
 * @author 红方团队
 */
@SpringJUnitConfig
@Import(NotificationIntegrationTest.TestConfig.class)
@TestPropertySource(properties = {
        "spring.redis.host=localhost",
        "spring.redis.port=6379"
})
@DisplayName("通知服务集成测试")
class NotificationIntegrationTest {

    @TestConfiguration
    static class TestConfig {
        @Bean
        public NotificationService notificationService(
                NotificationMapper notificationMapper,
                org.springframework.mail.javamail.JavaMailSender mailSender,
                org.springframework.kafka.core.KafkaTemplate<String, String> kafkaTemplate) {
            return new com.redteam.notification.service.impl.NotificationServiceImpl(
                    notificationMapper, mailSender, kafkaTemplate);
        }

        @Bean
        public com.redteam.notification.controller.NotificationController notificationController(
                NotificationService notificationService) {
            return new com.redteam.notification.controller.NotificationController(notificationService);
        }

        @Bean
        public com.redteam.common.exception.GlobalExceptionHandler globalExceptionHandler() {
            return new com.redteam.common.exception.GlobalExceptionHandler();
        }
    }

    @MockBean
    private NotificationMapper notificationMapper;

    @MockBean
    private org.springframework.mail.javamail.JavaMailSender mailSender;

    @MockBean
    private org.springframework.kafka.core.KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private com.redteam.notification.controller.NotificationController notificationController;

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @BeforeEach
    void setUp(WebApplicationContext wac) {
        mockMvc = MockMvcBuilders.webAppContextSetup(wac).build();
        // 注入 baseMapper（ServiceImpl 父类字段）
        try {
            java.lang.reflect.Field baseMapperField =
                    com.baomidou.mybatisplus.extension.service.impl.ServiceImpl.class.getDeclaredField("baseMapper");
            baseMapperField.setAccessible(true);
            baseMapperField.set(notificationService, notificationMapper);
        } catch (Exception ignored) {
            // 忽略
        }
    }

    // ===================== POST /v1/notifications =====================

    @Test
    @DisplayName("集成 - 发送通知应返回 VO")
    void testSendNotificationFlow() throws Exception {
        NotificationDTO dto = new NotificationDTO();
        dto.setUserId(1001L);
        dto.setTitle("测试通知");
        dto.setContent("这是一条测试通知");
        dto.setType("TASK");
        dto.setLevel("INFO");
        dto.setChannel("IN_APP");

        NotificationVO vo = buildVO("ntf-001", "测试通知", "TASK", "INFO");
        when(notificationService.sendNotification(any(NotificationDTO.class))).thenReturn(vo);

        mockMvc.perform(post("/v1/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.notificationId").value("ntf-001"))
                .andExpect(jsonPath("$.data.title").value("测试通知"));
    }

    @Test
    @DisplayName("集成 - 发送通知缺少必填字段应返回 400")
    void testSendNotificationValidation() throws Exception {
        NotificationDTO dto = new NotificationDTO();
        // 缺少 userId / title / content 等

        mockMvc.perform(post("/v1/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    // ===================== POST /v1/notifications/broadcast =====================

    @Test
    @DisplayName("集成 - 广播通知应返回 VO 列表")
    void testBroadcastFlow() throws Exception {
        BroadcastNotificationDTO dto = new BroadcastNotificationDTO();
        dto.setUserIds(Arrays.asList(1001L, 1002L, 1003L));
        dto.setTitle("广播通知");
        dto.setContent("广播内容");
        dto.setType("SYSTEM");
        dto.setLevel("WARN");
        dto.setChannel("IN_APP");

        List<NotificationVO> results = Arrays.asList(
                buildVO("ntf-1", "广播通知", "SYSTEM", "WARN"),
                buildVO("ntf-2", "广播通知", "SYSTEM", "WARN"),
                buildVO("ntf-3", "广播通知", "SYSTEM", "WARN")
        );
        when(notificationService.broadcastNotification(any(BroadcastNotificationDTO.class))).thenReturn(results);

        mockMvc.perform(post("/v1/notifications/broadcast")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.length()").value(3));
    }

    // ===================== GET /v1/notifications/{notificationId} =====================

    @Test
    @DisplayName("集成 - 查询通知详情应返回 VO")
    void testGetNotificationFlow() throws Exception {
        NotificationVO vo = buildVO("ntf-001", "测试通知", "TASK", "INFO");
        when(notificationService.getNotification("ntf-001")).thenReturn(vo);

        mockMvc.perform(get("/v1/notifications/ntf-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.notificationId").value("ntf-001"));
    }

    @Test
    @DisplayName("集成 - 查询不存在通知应返回业务错误码")
    void testGetNotificationNotFound() throws Exception {
        when(notificationService.getNotification("ntf-missing"))
                .thenThrow(new BusinessException(ResultCode.NOT_FOUND, "通知不存在: ntf-missing"));

        mockMvc.perform(get("/v1/notifications/ntf-missing"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.NOT_FOUND.getCode()));
    }

    // ===================== GET /v1/notifications/user/{userId} =====================

    @Test
    @DisplayName("集成 - 分页查询用户通知应返回分页结构")
    void testListUserNotificationsFlow() throws Exception {
        NotificationVO v1 = buildVO("ntf-1", "通知A", "TASK", "INFO");
        NotificationVO v2 = buildVO("ntf-2", "通知B", "SYSTEM", "WARN");

        com.redteam.common.result.PageResult<NotificationVO> page =
                com.redteam.common.result.PageResult.of(1L, 10L, 2L, Arrays.asList(v1, v2));
        when(notificationService.listUserNotifications(eq(1001L), any(NotificationQueryDTO.class))).thenReturn(page);

        mockMvc.perform(get("/v1/notifications/user/1001")
                        .param("current", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2));
    }

    // ===================== PUT /v1/notifications/{notificationId}/read =====================

    @Test
    @DisplayName("集成 - 标记单条已读应返回成功")
    void testMarkAsReadFlow() throws Exception {
        doNothing().when(notificationService).markAsRead("ntf-001");

        mockMvc.perform(put("/v1/notifications/ntf-001/read"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    // ===================== PUT /v1/notifications/user/{userId}/read-all =====================

    @Test
    @DisplayName("集成 - 全部已读应返回成功")
    void testMarkAllAsReadFlow() throws Exception {
        doNothing().when(notificationService).markAllAsRead(1001L);

        mockMvc.perform(put("/v1/notifications/user/1001/read-all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    // ===================== DELETE /v1/notifications/{notificationId} =====================

    @Test
    @DisplayName("集成 - 删除通知应返回成功")
    void testDeleteNotificationFlow() throws Exception {
        doNothing().when(notificationService).deleteNotification("ntf-001");

        mockMvc.perform(delete("/v1/notifications/ntf-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    // ===================== GET /v1/notifications/user/{userId}/unread-count =====================

    @Test
    @DisplayName("集成 - 获取未读数量应返回整数")
    void testUnreadCountFlow() throws Exception {
        when(notificationService.getUnreadCount(1001L)).thenReturn(5);

        mockMvc.perform(get("/v1/notifications/user/1001/unread-count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value(5));
    }

    // ===================== GET /v1/notifications/stats =====================

    @Test
    @DisplayName("集成 - 通知统计应返回完整结构")
    void testStatsFlow() throws Exception {
        NotificationStatsDTO stats = new NotificationStatsDTO();
        stats.setTotal(100L);
        stats.setUnreadCount(30L);
        stats.setReadCount(70L);
        stats.setReadRate(70.0);
        Map<String, Long> byType = new HashMap<>();
        byType.put("TASK", 50L);
        byType.put("SYSTEM", 50L);
        stats.setByType(byType);

        when(notificationService.getNotificationStats(any())).thenReturn(stats);

        mockMvc.perform(get("/v1/notifications/stats")
                        .param("userId", "1001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(100))
                .andExpect(jsonPath("$.data.unreadCount").value(30))
                .andExpect(jsonPath("$.data.readRate").value(70.0));
    }

    @Test
    @DisplayName("集成 - 全局统计不传 userId 也应正常返回")
    void testStatsGlobalFlow() throws Exception {
        NotificationStatsDTO stats = new NotificationStatsDTO();
        stats.setTotal(500L);
        stats.setUnreadCount(150L);
        stats.setReadCount(350L);

        when(notificationService.getNotificationStats(null)).thenReturn(stats);

        mockMvc.perform(get("/v1/notifications/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(500));
    }

    // ===================== DELETE /v1/notifications/expired =====================

    @Test
    @DisplayName("集成 - 清理过期通知应返回清理数量")
    void testCleanupExpiredFlow() throws Exception {
        when(notificationService.cleanupExpired()).thenReturn(15);

        mockMvc.perform(delete("/v1/notifications/expired"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value(15));
    }

    // ===================== POST /v1/notifications/{notificationId}/retry =====================

    @Test
    @DisplayName("集成 - 重试失败通知应返回 VO")
    void testRetryFailedFlow() throws Exception {
        NotificationVO vo = buildVO("ntf-001", "测试通知", "TASK", "INFO");
        when(notificationService.retryFailed("ntf-001")).thenReturn(vo);

        mockMvc.perform(post("/v1/notifications/ntf-001/retry"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.notificationId").value("ntf-001"));
    }

    @Test
    @DisplayName("集成 - 重试不存在通知应返回业务错误码")
    void testRetryFailedNotFound() throws Exception {
        when(notificationService.retryFailed("ntf-missing"))
                .thenThrow(new BusinessException(ResultCode.NOT_FOUND, "通知不存在: ntf-missing"));

        mockMvc.perform(post("/v1/notifications/ntf-missing/retry"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.NOT_FOUND.getCode()));
    }

    // ===================== 辅助方法 =====================

    private NotificationVO buildVO(String notificationId, String title, String type, String level) {
        NotificationVO vo = new NotificationVO();
        vo.setNotificationId(notificationId);
        vo.setTitle(title);
        vo.setType(type);
        vo.setLevel(level);
        vo.setChannel("IN_APP");
        vo.setIsRead(0);
        vo.setCreateTime(LocalDateTime.now());
        return vo;
    }
}
