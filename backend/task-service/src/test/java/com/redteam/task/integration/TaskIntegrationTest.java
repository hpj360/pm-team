package com.redteam.task.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redteam.common.exception.BusinessException;
import com.redteam.common.result.ResultCode;
import com.redteam.task.dto.TaskDTO;
import com.redteam.task.dto.TaskQueryDTO;
import com.redteam.task.dto.TaskStatsDTO;
import com.redteam.task.dto.TaskVO;
import com.redteam.task.entity.TaskEntity;
import com.redteam.task.mapper.TaskMapper;
import com.redteam.task.service.TaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
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
 * 任务服务集成测试
 *
 * <p>验证 TaskController → TaskService → TaskMapper 端到端请求链路，
 * 使用 @MockBean 隔离 Mapper 与 Kafka，保留 Spring 容器装配、参数校验、JSON 序列化等真实行为。</p>
 *
 * <p>覆盖场景：</p>
 * <ul>
 *   <li>任务 CRUD：创建、查询、更新、删除</li>
 *   <li>分页查询、任务统计</li>
 *   <li>状态流转：start/pause/complete/cancel</li>
 *   <li>任务分配、状态更新、进度更新</li>
 *   <li>异常路径：参数校验失败、任务不存在、非法状态</li>
 * </ul>
 *
 * @author 红方团队
 */
@SpringJUnitConfig
@Import(TaskIntegrationTest.TestConfig.class)
@TestPropertySource(properties = {
        "spring.kafka.bootstrap-servers=localhost:9092"
})
@DisplayName("任务服务集成测试")
class TaskIntegrationTest {

    @TestConfiguration
    static class TestConfig {
        @Bean
        public TaskService taskService() {
            return new com.redteam.task.service.impl.TaskServiceImpl();
        }

        @Bean
        public com.redteam.task.controller.TaskController taskController(TaskService taskService) {
            return new com.redteam.task.controller.TaskController(taskService);
        }

        @Bean
        public com.redteam.common.exception.GlobalExceptionHandler globalExceptionHandler() {
            return new com.redteam.common.exception.GlobalExceptionHandler();
        }

        @Bean
        @SuppressWarnings("unchecked")
        public KafkaTemplate<String, String> kafkaTemplate() {
            return org.mockito.Mockito.mock(KafkaTemplate.class);
        }
    }

    @MockBean
    private TaskMapper taskMapper;

    @MockBean
    @SuppressWarnings("unchecked")
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private TaskService taskService;

    @Autowired
    private com.redteam.task.controller.TaskController taskController;

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
            baseMapperField.set(taskService, taskMapper);
        } catch (Exception ignored) {
            // 忽略
        }
        // 注入 kafkaTemplate
        try {
            java.lang.reflect.Field kafkaField =
                    com.redteam.task.service.impl.TaskServiceImpl.class.getDeclaredField("kafkaTemplate");
            kafkaField.setAccessible(true);
            kafkaField.set(taskService, kafkaTemplate);
        } catch (Exception ignored) {
            // 忽略 - 字段可能不存在
        }
    }

    // ===================== POST /api/v1/tasks =====================

    @Test
    @DisplayName("集成 - 创建任务应返回任务 VO")
    void testCreateTaskFlow() throws Exception {
        TaskDTO dto = new TaskDTO();
        dto.setTaskName("渗透测试任务");
        dto.setTaskType("PENETRATION_TEST");
        dto.setPriority("HIGH");

        TaskEntity entity = buildEntity("task-001", "渗透测试任务", "PENDING");
        when(taskMapper.insert(any(TaskEntity.class))).thenReturn(1);
        when(taskMapper.selectById(any())).thenReturn(entity);

        mockMvc.perform(post("/api/v1/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("集成 - 创建任务缺少必填字段应返回 400")
    void testCreateTaskValidation() throws Exception {
        TaskDTO dto = new TaskDTO();
        // 缺少 taskName

        mockMvc.perform(post("/api/v1/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    // ===================== GET /api/v1/tasks/{taskId} =====================

    @Test
    @DisplayName("集成 - 查询任务详情应返回 VO")
    void testGetTaskFlow() throws Exception {
        TaskVO vo = buildVO("task-001", "测试任务", "PENDING");
        when(taskService.getTask("task-001")).thenReturn(vo);

        mockMvc.perform(get("/api/v1/tasks/task-001"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.taskId").value("task-001"))
                .andExpect(jsonPath("$.data.taskName").value("测试任务"));
    }

    @Test
    @DisplayName("集成 - 查询不存在任务应返回业务错误码")
    void testGetTaskNotFound() throws Exception {
        when(taskService.getTask("task-missing"))
                .thenThrow(new BusinessException(ResultCode.NOT_FOUND, "任务不存在: task-missing"));

        mockMvc.perform(get("/api/v1/tasks/task-missing"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.NOT_FOUND.getCode()));
    }

    // ===================== PUT /api/v1/tasks/{taskId} =====================

    @Test
    @DisplayName("集成 - 更新任务应返回更新后的 VO")
    void testUpdateTaskFlow() throws Exception {
        TaskDTO dto = new TaskDTO();
        dto.setTaskName("更新后的任务");
        dto.setTaskType("PENETRATION_TEST");
        dto.setPriority("MEDIUM");

        TaskVO vo = buildVO("task-001", "更新后的任务", "PENDING");
        when(taskService.updateTask(eq("task-001"), any(TaskDTO.class))).thenReturn(vo);

        mockMvc.perform(put("/api/v1/tasks/task-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.taskName").value("更新后的任务"));
    }

    // ===================== DELETE /api/v1/tasks/{taskId} =====================

    @Test
    @DisplayName("集成 - 删除任务应返回成功")
    void testDeleteTaskFlow() throws Exception {
        doNothing().when(taskService).deleteTask("task-001");

        mockMvc.perform(delete("/api/v1/tasks/task-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    // ===================== GET /api/v1/tasks =====================

    @Test
    @DisplayName("集成 - 分页查询任务应返回分页结构")
    void testListTasksFlow() throws Exception {
        TaskVO v1 = buildVO("task-1", "任务A", "PENDING");
        TaskVO v2 = buildVO("task-2", "任务B", "RUNNING");

        com.redteam.common.result.PageResult<TaskVO> page =
                com.redteam.common.result.PageResult.of(1L, 10L, 2L, Arrays.asList(v1, v2));
        when(taskService.listTasks(any(TaskQueryDTO.class))).thenReturn(page);

        mockMvc.perform(get("/api/v1/tasks")
                        .param("current", "1")
                        .param("size", "10")
                        .param("status", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(2));
    }

    // ===================== POST /api/v1/tasks/{taskId}/start =====================

    @Test
    @DisplayName("集成 - 启动任务应返回成功")
    void testStartTaskFlow() throws Exception {
        doNothing().when(taskService).startTask("task-001");

        mockMvc.perform(post("/api/v1/tasks/task-001/start"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("集成 - 暂停任务应返回成功")
    void testPauseTaskFlow() throws Exception {
        doNothing().when(taskService).pauseTask("task-001");

        mockMvc.perform(post("/api/v1/tasks/task-001/pause"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("集成 - 完成任务应返回成功")
    void testCompleteTaskFlow() throws Exception {
        doNothing().when(taskService).completeTask("task-001");

        mockMvc.perform(post("/api/v1/tasks/task-001/complete"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("集成 - 取消任务应返回成功")
    void testCancelTaskFlow() throws Exception {
        doNothing().when(taskService).cancelTask("task-001");

        mockMvc.perform(post("/api/v1/tasks/task-001/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    // ===================== POST /api/v1/tasks/{taskId}/assign =====================

    @Test
    @DisplayName("集成 - 分配任务应返回更新后的 VO")
    void testAssignTaskFlow() throws Exception {
        TaskVO vo = buildVO("task-001", "任务A", "PENDING");
        vo.setOwnerId(1001L);
        when(taskService.assignTask("task-001", 1001L)).thenReturn(vo);

        mockMvc.perform(post("/api/v1/tasks/task-001/assign")
                        .param("ownerId", "1001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ownerId").value(1001));
    }

    @Test
    @DisplayName("集成 - 分配任务缺少 ownerId 应返回 400")
    void testAssignTaskValidation() throws Exception {
        mockMvc.perform(post("/api/v1/tasks/task-001/assign"))
                .andExpect(status().isBadRequest());
    }

    // ===================== POST /api/v1/tasks/{taskId}/status =====================

    @Test
    @DisplayName("集成 - 更新任务状态应返回 VO")
    void testUpdateStatusFlow() throws Exception {
        TaskVO vo = buildVO("task-001", "任务A", "RUNNING");
        when(taskService.updateStatus("task-001", "RUNNING")).thenReturn(vo);

        mockMvc.perform(post("/api/v1/tasks/task-001/status")
                        .param("status", "RUNNING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RUNNING"));
    }

    // ===================== POST /api/v1/tasks/{taskId}/progress =====================

    @Test
    @DisplayName("集成 - 更新进度应返回 VO")
    void testUpdateProgressFlow() throws Exception {
        TaskVO vo = buildVO("task-001", "任务A", "RUNNING");
        vo.setProgress(50);
        when(taskService.updateProgress("task-001", 50)).thenReturn(vo);

        mockMvc.perform(post("/api/v1/tasks/task-001/progress")
                        .param("progress", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.progress").value(50));
    }

    @Test
    @DisplayName("集成 - 进度超出 100 应返回 400")
    void testUpdateProgressValidation() throws Exception {
        mockMvc.perform(post("/api/v1/tasks/task-001/progress")
                        .param("progress", "150"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("集成 - 进度小于 0 应返回 400")
    void testUpdateProgressNegativeValidation() throws Exception {
        mockMvc.perform(post("/api/v1/tasks/task-001/progress")
                        .param("progress", "-10"))
                .andExpect(status().isBadRequest());
    }

    // ===================== GET /api/v1/tasks/stats =====================

    @Test
    @DisplayName("集成 - 任务统计应返回完整结构")
    void testGetTaskStatsFlow() throws Exception {
        TaskStatsDTO stats = new TaskStatsDTO();
        stats.setTotal(10L);
        stats.setCompletedCount(5L);
        stats.setRunningCount(3L);
        stats.setPendingCount(2L);
        stats.setCompletionRate(50.0);
        Map<String, Long> byStatus = new HashMap<>();
        byStatus.put("PENDING", 2L);
        byStatus.put("RUNNING", 3L);
        byStatus.put("COMPLETED", 5L);
        stats.setByStatus(byStatus);

        when(taskService.getTaskStats()).thenReturn(stats);

        mockMvc.perform(get("/api/v1/tasks/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(10))
                .andExpect(jsonPath("$.data.completedCount").value(5))
                .andExpect(jsonPath("$.data.completionRate").value(50.0));
    }

    // ===================== 异常路径 =====================

    @Test
    @DisplayName("集成 - 非法状态流转应返回业务错误码")
    void testInvalidStatusTransition() throws Exception {
        when(taskService.updateStatus("task-001", "INVALID"))
                .thenThrow(new BusinessException(ResultCode.PARAM_ERROR, "非法任务状态: INVALID"));

        mockMvc.perform(post("/api/v1/tasks/task-001/status")
                        .param("status", "INVALID"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.PARAM_ERROR.getCode()));
    }

    // ===================== 辅助方法 =====================

    private TaskEntity buildEntity(String taskId, String name, String status) {
        TaskEntity entity = new TaskEntity();
        entity.setTaskId(taskId);
        entity.setTaskName(name);
        entity.setStatus(status);
        entity.setTaskType("PENETRATION_TEST");
        entity.setPriority("HIGH");
        entity.setProgress(0);
        entity.setCreateTime(LocalDateTime.now());
        entity.setUpdateTime(LocalDateTime.now());
        return entity;
    }

    private TaskVO buildVO(String taskId, String name, String status) {
        TaskVO vo = new TaskVO();
        vo.setTaskId(taskId);
        vo.setTaskName(name);
        vo.setStatus(status);
        vo.setTaskType("PENETRATION_TEST");
        vo.setPriority("HIGH");
        vo.setProgress(0);
        vo.setCreateTime(LocalDateTime.now());
        vo.setUpdateTime(LocalDateTime.now());
        return vo;
    }
}
