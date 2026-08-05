package com.redteam.task.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.redteam.common.result.PageResult;
import com.redteam.task.dto.TaskDTO;
import com.redteam.task.dto.TaskQueryDTO;
import com.redteam.task.dto.TaskVO;
import com.redteam.task.service.TaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 任务控制器测试
 * <p>
 * 使用 MockMvc (standalone 模式) 对 TaskController 进行测试，
 * 验证路由、请求参数绑定、响应序列化及与 Service 的交互。
 * </p>
 *
 * @author 红方团队
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("任务控制器测试")
class TaskControllerTest {

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Mock
    private TaskService taskService;

    @InjectMocks
    private TaskController taskController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(taskController).build();
    }

    /**
     * 构造测试用 TaskVO
     */
    private TaskVO buildVO() {
        TaskVO vo = new TaskVO();
        vo.setId(1L);
        vo.setTaskId("task-001");
        vo.setTaskName("测试任务");
        vo.setTaskType("RECON");
        vo.setStatus("PENDING");
        vo.setPriority(3);
        vo.setTargetId("target-001");
        vo.setOwnerId(1001L);
        vo.setStartTime(LocalDateTime.now());
        vo.setDescription("单元测试");
        vo.setCreateTime(LocalDateTime.now());
        return vo;
    }

    /**
     * 构造测试用 TaskDTO
     */
    private TaskDTO buildDTO() {
        TaskDTO dto = new TaskDTO();
        dto.setTaskName("测试任务");
        dto.setTaskType("RECON");
        dto.setPriority(3);
        dto.setTargetId("target-001");
        dto.setOwnerId(1001L);
        dto.setDescription("单元测试");
        return dto;
    }

    @Test
    @DisplayName("POST /api/v1/tasks - 创建任务成功")
    void testCreateTask() throws Exception {
        when(taskService.createTask(any(TaskDTO.class))).thenReturn(buildVO());

        mockMvc.perform(post("/api/v1/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildDTO())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.taskId").value("task-001"))
                .andExpect(jsonPath("$.data.taskName").value("测试任务"))
                .andExpect(jsonPath("$.data.status").value("PENDING"));

        verify(taskService, times(1)).createTask(any(TaskDTO.class));
    }

    @Test
    @DisplayName("POST /api/v1/tasks - 参数校验失败(空任务名)")
    void testCreateTaskValidationFail() throws Exception {
        TaskDTO dto = new TaskDTO();
        dto.setTaskName("");
        dto.setTaskType("INVALID");
        dto.setPriority(10);

        mockMvc.perform(post("/api/v1/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());

        verify(taskService, never()).createTask(any(TaskDTO.class));
    }

    @Test
    @DisplayName("GET /api/v1/tasks/{taskId} - 查询任务成功")
    void testGetTask() throws Exception {
        when(taskService.getTask("task-001")).thenReturn(buildVO());

        mockMvc.perform(get("/api/v1/tasks/task-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.taskId").value("task-001"))
                .andExpect(jsonPath("$.data.taskType").value("RECON"));

        verify(taskService, times(1)).getTask("task-001");
    }

    @Test
    @DisplayName("PUT /api/v1/tasks/{taskId} - 更新任务成功")
    void testUpdateTask() throws Exception {
        when(taskService.updateTask(anyString(), any(TaskDTO.class))).thenReturn(buildVO());

        mockMvc.perform(put("/api/v1/tasks/task-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildDTO())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.taskId").value("task-001"));

        verify(taskService, times(1)).updateTask(eq("task-001"), any(TaskDTO.class));
    }

    @Test
    @DisplayName("DELETE /api/v1/tasks/{taskId} - 删除任务成功")
    void testDeleteTask() throws Exception {
        doNothing().when(taskService).deleteTask(anyString());

        mockMvc.perform(delete("/api/v1/tasks/task-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(taskService, times(1)).deleteTask("task-001");
    }

    @Test
    @DisplayName("GET /api/v1/tasks - 分页查询成功")
    void testListTasks() throws Exception {
        PageResult<TaskVO> pageResult = PageResult.of(1L, 10L, 1L, List.of(buildVO()));
        when(taskService.listTasks(any(TaskQueryDTO.class))).thenReturn(pageResult);

        mockMvc.perform(get("/api/v1/tasks")
                        .param("pageNum", "1")
                        .param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].taskId").value("task-001"));

        verify(taskService, times(1)).listTasks(any(TaskQueryDTO.class));
    }

    @Test
    @DisplayName("GET /api/v1/tasks - 带筛选条件分页查询")
    void testListTasksWithFilter() throws Exception {
        PageResult<TaskVO> pageResult = PageResult.of(1L, 10L, 0L, List.of());
        when(taskService.listTasks(any(TaskQueryDTO.class))).thenReturn(pageResult);

        mockMvc.perform(get("/api/v1/tasks")
                        .param("pageNum", "1")
                        .param("pageSize", "10")
                        .param("status", "PENDING")
                        .param("taskType", "RECON"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(0));

        verify(taskService, times(1)).listTasks(any(TaskQueryDTO.class));
    }

    @Test
    @DisplayName("POST /api/v1/tasks/{taskId}/start - 启动任务成功")
    void testStartTask() throws Exception {
        doNothing().when(taskService).startTask(anyString());

        mockMvc.perform(post("/api/v1/tasks/task-001/start"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(taskService, times(1)).startTask("task-001");
    }

    @Test
    @DisplayName("POST /api/v1/tasks/{taskId}/pause - 暂停任务成功")
    void testPauseTask() throws Exception {
        doNothing().when(taskService).pauseTask(anyString());

        mockMvc.perform(post("/api/v1/tasks/task-001/pause"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(taskService, times(1)).pauseTask("task-001");
    }

    @Test
    @DisplayName("POST /api/v1/tasks/{taskId}/complete - 完成任务成功")
    void testCompleteTask() throws Exception {
        doNothing().when(taskService).completeTask(anyString());

        mockMvc.perform(post("/api/v1/tasks/task-001/complete"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(taskService, times(1)).completeTask("task-001");
    }

    @Test
    @DisplayName("POST /api/v1/tasks/{taskId}/cancel - 取消任务成功")
    void testCancelTask() throws Exception {
        doNothing().when(taskService).cancelTask(anyString());

        mockMvc.perform(post("/api/v1/tasks/task-001/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(taskService, times(1)).cancelTask("task-001");
    }
}
