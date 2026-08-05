package com.redteam.task.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.redteam.common.exception.BusinessException;
import com.redteam.task.dto.TaskDTO;
import com.redteam.task.dto.TaskQueryDTO;
import com.redteam.task.dto.TaskStatsDTO;
import com.redteam.task.dto.TaskVO;
import com.redteam.task.entity.TaskEntity;
import com.redteam.task.mapper.TaskMapper;
import com.redteam.task.service.impl.TaskServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 任务服务单元测试
 * <p>
 * 使用 JUnit 5 + Mockito 对 TaskServiceImpl 进行隔离测试，
 * 覆盖创建、查询、更新、删除、分页及状态流转等核心逻辑。
 * </p>
 *
 * @author 红方团队
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("任务服务测试")
class TaskServiceTest {

    @Mock
    private TaskMapper taskMapper;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @InjectMocks
    private TaskServiceImpl taskService;

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
        dto.setDescription("单元测试任务");
        return dto;
    }

    /**
     * 构造测试用 TaskEntity
     */
    private TaskEntity buildEntity(String taskId, String status) {
        TaskEntity entity = new TaskEntity();
        entity.setId(1L);
        entity.setTaskId(taskId);
        entity.setTaskName("测试任务");
        entity.setTaskType("RECON");
        entity.setStatus(status);
        entity.setPriority(3);
        entity.setTargetId("target-001");
        entity.setOwnerId(1001L);
        entity.setDescription("单元测试任务");
        return entity;
    }

    @Nested
    @DisplayName("创建任务测试")
    class CreateTaskTest {

        @Test
        @DisplayName("创建任务 - 成功生成UUID并发布事件")
        void testCreateTaskSuccess() {
            TaskDTO dto = buildDTO();

            TaskVO result = taskService.createTask(dto);

            assertNotNull(result);
            assertNotNull(result.getTaskId());
            assertEquals("测试任务", result.getTaskName());
            assertEquals("RECON", result.getTaskType());
            assertEquals("PENDING", result.getStatus());
            assertEquals(3, result.getPriority());
            assertEquals("target-001", result.getTargetId());
            assertEquals(1001L, result.getOwnerId());

            verify(taskMapper, times(1)).insert(any(TaskEntity.class));
            verify(kafkaTemplate, times(1)).send(eq("redteam.task.events"), anyString(), anyString());
        }

        @Test
        @DisplayName("创建任务 - 事件发送异常不影响主流程")
        void testCreateTaskEventSendFailure() {
            TaskDTO dto = buildDTO();
            doThrow(new RuntimeException("Kafka不可用"))
                    .when(kafkaTemplate).send(anyString(), anyString(), anyString());

            TaskVO result = assertDoesNotThrow(() -> taskService.createTask(dto));
            assertNotNull(result);
            verify(taskMapper, times(1)).insert(any(TaskEntity.class));
        }
    }

    @Nested
    @DisplayName("查询任务测试")
    class GetTaskTest {

        @Test
        @DisplayName("查询任务 - 任务存在")
        void testGetTaskFound() {
            TaskEntity entity = buildEntity("task-001", "PENDING");
            when(taskMapper.selectOne(any(Wrapper.class))).thenReturn(entity);

            TaskVO result = taskService.getTask("task-001");

            assertNotNull(result);
            assertEquals("task-001", result.getTaskId());
            assertEquals("PENDING", result.getStatus());
            verify(taskMapper, times(1)).selectOne(any(Wrapper.class));
        }

        @Test
        @DisplayName("查询任务 - 任务不存在抛出业务异常")
        void testGetTaskNotFound() {
            when(taskMapper.selectOne(any(Wrapper.class))).thenReturn(null);

            assertThrows(BusinessException.class, () -> taskService.getTask("not-exist"));
        }
    }

    @Nested
    @DisplayName("更新任务测试")
    class UpdateTaskTest {

        @Test
        @DisplayName("更新任务 - 成功并发布事件")
        void testUpdateTaskSuccess() {
            TaskEntity entity = buildEntity("task-001", "PENDING");
            when(taskMapper.selectOne(any(Wrapper.class))).thenReturn(entity);
            when(taskMapper.updateById(any(TaskEntity.class))).thenReturn(1);

            TaskDTO dto = buildDTO();
            dto.setTaskName("更新后的任务");

            TaskVO result = taskService.updateTask("task-001", dto);

            assertNotNull(result);
            assertEquals("更新后的任务", result.getTaskName());
            verify(taskMapper, times(1)).updateById(any(TaskEntity.class));
            verify(kafkaTemplate, times(1)).send(eq("redteam.task.events"), anyString(), anyString());
        }

        @Test
        @DisplayName("更新任务 - 任务不存在抛出异常")
        void testUpdateTaskNotFound() {
            when(taskMapper.selectOne(any(Wrapper.class))).thenReturn(null);

            assertThrows(BusinessException.class, () -> taskService.updateTask("not-exist", buildDTO()));
        }
    }

    @Nested
    @DisplayName("删除任务测试")
    class DeleteTaskTest {

        @Test
        @DisplayName("删除任务 - 逻辑删除成功")
        void testDeleteTaskSuccess() {
            TaskEntity entity = buildEntity("task-001", "PENDING");
            when(taskMapper.selectOne(any(Wrapper.class))).thenReturn(entity);
            when(taskMapper.deleteById(anyLong())).thenReturn(1);

            assertDoesNotThrow(() -> taskService.deleteTask("task-001"));

            verify(taskMapper, times(1)).deleteById(1L);
            verify(kafkaTemplate, times(1)).send(eq("redteam.task.events"), anyString(), anyString());
        }

        @Test
        @DisplayName("删除任务 - 任务不存在抛出异常")
        void testDeleteTaskNotFound() {
            when(taskMapper.selectOne(any(Wrapper.class))).thenReturn(null);

            assertThrows(BusinessException.class, () -> taskService.deleteTask("not-exist"));
        }
    }

    @Nested
    @DisplayName("分页查询测试")
    class ListTasksTest {

        @Test
        @DisplayName("分页查询 - 正常返回结果")
        void testListTasks() {
            TaskQueryDTO query = new TaskQueryDTO();
            query.setPageNum(1L);
            query.setPageSize(10L);

            Page<TaskEntity> page = new Page<>(1, 10);
            page.setTotal(1);
            page.setRecords(List.of(buildEntity("task-001", "PENDING")));
            when(taskMapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(page);

            var result = taskService.listTasks(query);

            assertNotNull(result);
            assertEquals(1L, result.getTotal());
            assertEquals(1, result.getRecords().size());
            assertEquals("task-001", result.getRecords().get(0).getTaskId());
            verify(taskMapper, times(1)).selectPage(any(Page.class), any(Wrapper.class));
        }

        @Test
        @DisplayName("分页查询 - 带筛选条件")
        void testListTasksWithCondition() {
            TaskQueryDTO query = new TaskQueryDTO();
            query.setPageNum(1L);
            query.setPageSize(10L);
            query.setStatus("PENDING");
            query.setTaskType("RECON");
            query.setKeyword("测试");

            Page<TaskEntity> page = new Page<>(1, 10);
            page.setTotal(0);
            page.setRecords(Collections.emptyList());
            when(taskMapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(page);

            var result = taskService.listTasks(query);

            assertNotNull(result);
            assertEquals(0L, result.getTotal());
            assertTrue(result.getRecords().isEmpty());
        }

        @Test
        @DisplayName("分页查询 - 按负责人筛选")
        void testListTasksByOwner() {
            TaskQueryDTO query = new TaskQueryDTO();
            query.setPageNum(1L);
            query.setPageSize(10L);
            query.setOwnerId(1001L);
            query.setTargetId("target-001");

            Page<TaskEntity> page = new Page<>(1, 10);
            page.setTotal(0);
            page.setRecords(Collections.emptyList());
            when(taskMapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(page);

            var result = taskService.listTasks(query);

            assertNotNull(result);
            assertEquals(0L, result.getTotal());
        }
    }

    @Nested
    @DisplayName("状态流转测试")
    class StateTransitionTest {

        @Test
        @DisplayName("启动任务 - PENDING → RUNNING")
        void testStartTaskSuccess() {
            TaskEntity entity = buildEntity("task-001", "PENDING");
            when(taskMapper.selectOne(any(Wrapper.class))).thenReturn(entity);
            when(taskMapper.updateById(any(TaskEntity.class))).thenReturn(1);

            taskService.startTask("task-001");

            ArgumentCaptor<TaskEntity> captor = ArgumentCaptor.forClass(TaskEntity.class);
            verify(taskMapper).updateById(captor.capture());
            assertEquals("RUNNING", captor.getValue().getStatus());
            assertNotNull(captor.getValue().getStartTime());
            verify(kafkaTemplate, times(1)).send(eq("redteam.task.events"), anyString(), anyString());
        }

        @Test
        @DisplayName("启动任务 - 非PENDING状态拒绝")
        void testStartTaskInvalidState() {
            TaskEntity entity = buildEntity("task-001", "RUNNING");
            when(taskMapper.selectOne(any(Wrapper.class))).thenReturn(entity);

            assertThrows(BusinessException.class, () -> taskService.startTask("task-001"));
            verify(taskMapper, never()).updateById(any(TaskEntity.class));
        }

        @Test
        @DisplayName("启动任务 - 任务不存在抛出异常")
        void testStartTaskNotFound() {
            when(taskMapper.selectOne(any(Wrapper.class))).thenReturn(null);

            assertThrows(BusinessException.class, () -> taskService.startTask("not-exist"));
        }

        @Test
        @DisplayName("暂停任务 - RUNNING → PAUSED")
        void testPauseTaskSuccess() {
            TaskEntity entity = buildEntity("task-001", "RUNNING");
            when(taskMapper.selectOne(any(Wrapper.class))).thenReturn(entity);
            when(taskMapper.updateById(any(TaskEntity.class))).thenReturn(1);

            taskService.pauseTask("task-001");

            ArgumentCaptor<TaskEntity> captor = ArgumentCaptor.forClass(TaskEntity.class);
            verify(taskMapper).updateById(captor.capture());
            assertEquals("PAUSED", captor.getValue().getStatus());
            verify(kafkaTemplate, times(1)).send(eq("redteam.task.events"), anyString(), anyString());
        }

        @Test
        @DisplayName("暂停任务 - 非RUNNING状态拒绝")
        void testPauseTaskInvalidState() {
            TaskEntity entity = buildEntity("task-001", "PENDING");
            when(taskMapper.selectOne(any(Wrapper.class))).thenReturn(entity);

            assertThrows(BusinessException.class, () -> taskService.pauseTask("task-001"));
            verify(taskMapper, never()).updateById(any(TaskEntity.class));
        }

        @Test
        @DisplayName("完成任务 - RUNNING → COMPLETED 并记录结束时间")
        void testCompleteTaskFromRunning() {
            TaskEntity entity = buildEntity("task-001", "RUNNING");
            when(taskMapper.selectOne(any(Wrapper.class))).thenReturn(entity);
            when(taskMapper.updateById(any(TaskEntity.class))).thenReturn(1);

            taskService.completeTask("task-001");

            ArgumentCaptor<TaskEntity> captor = ArgumentCaptor.forClass(TaskEntity.class);
            verify(taskMapper).updateById(captor.capture());
            assertEquals("COMPLETED", captor.getValue().getStatus());
            assertNotNull(captor.getValue().getEndTime());
            verify(kafkaTemplate, times(1)).send(eq("redteam.task.events"), anyString(), anyString());
        }

        @Test
        @DisplayName("完成任务 - PAUSED → COMPLETED")
        void testCompleteTaskFromPaused() {
            TaskEntity entity = buildEntity("task-001", "PAUSED");
            when(taskMapper.selectOne(any(Wrapper.class))).thenReturn(entity);
            when(taskMapper.updateById(any(TaskEntity.class))).thenReturn(1);

            assertDoesNotThrow(() -> taskService.completeTask("task-001"));
            verify(kafkaTemplate, times(1)).send(eq("redteam.task.events"), anyString(), anyString());
        }

        @Test
        @DisplayName("完成任务 - PENDING状态拒绝")
        void testCompleteTaskInvalidState() {
            TaskEntity entity = buildEntity("task-001", "PENDING");
            when(taskMapper.selectOne(any(Wrapper.class))).thenReturn(entity);

            assertThrows(BusinessException.class, () -> taskService.completeTask("task-001"));
            verify(taskMapper, never()).updateById(any(TaskEntity.class));
        }

        @Test
        @DisplayName("取消任务 - PENDING → CANCELLED")
        void testCancelTaskFromPending() {
            TaskEntity entity = buildEntity("task-001", "PENDING");
            when(taskMapper.selectOne(any(Wrapper.class))).thenReturn(entity);
            when(taskMapper.updateById(any(TaskEntity.class))).thenReturn(1);

            taskService.cancelTask("task-001");

            ArgumentCaptor<TaskEntity> captor = ArgumentCaptor.forClass(TaskEntity.class);
            verify(taskMapper).updateById(captor.capture());
            assertEquals("CANCELLED", captor.getValue().getStatus());
            verify(kafkaTemplate, times(1)).send(eq("redteam.task.events"), anyString(), anyString());
        }

        @Test
        @DisplayName("取消任务 - RUNNING → CANCELLED")
        void testCancelTaskFromRunning() {
            TaskEntity entity = buildEntity("task-001", "RUNNING");
            when(taskMapper.selectOne(any(Wrapper.class))).thenReturn(entity);
            when(taskMapper.updateById(any(TaskEntity.class))).thenReturn(1);

            assertDoesNotThrow(() -> taskService.cancelTask("task-001"));
        }

        @Test
        @DisplayName("取消任务 - COMPLETED状态拒绝")
        void testCancelTaskFromCompleted() {
            TaskEntity entity = buildEntity("task-001", "COMPLETED");
            when(taskMapper.selectOne(any(Wrapper.class))).thenReturn(entity);

            assertThrows(BusinessException.class, () -> taskService.cancelTask("task-001"));
            verify(taskMapper, never()).updateById(any(TaskEntity.class));
        }

        @Test
        @DisplayName("取消任务 - CANCELLED状态拒绝")
        void testCancelTaskFromCancelled() {
            TaskEntity entity = buildEntity("task-001", "CANCELLED");
            when(taskMapper.selectOne(any(Wrapper.class))).thenReturn(entity);

            assertThrows(BusinessException.class, () -> taskService.cancelTask("task-001"));
            verify(taskMapper, never()).updateById(any(TaskEntity.class));
        }
    }

    @Nested
    @DisplayName("分配任务测试")
    class AssignTaskTest {

        @Test
        @DisplayName("分配任务 - 成功更新负责人并发布事件")
        void testAssignTaskSuccess() {
            TaskEntity entity = buildEntity("task-001", "PENDING");
            when(taskMapper.selectOne(any(Wrapper.class))).thenReturn(entity);
            when(taskMapper.updateById(any(TaskEntity.class))).thenReturn(1);

            TaskVO result = taskService.assignTask("task-001", 2002L);

            assertNotNull(result);
            assertEquals(2002L, result.getOwnerId());
            ArgumentCaptor<TaskEntity> captor = ArgumentCaptor.forClass(TaskEntity.class);
            verify(taskMapper).updateById(captor.capture());
            assertEquals(2002L, captor.getValue().getOwnerId());
            verify(kafkaTemplate, times(1)).send(eq("redteam.task.events"), anyString(), anyString());
        }

        @Test
        @DisplayName("分配任务 - ownerId为空时抛出参数异常")
        void testAssignTaskNullOwner() {
            assertThrows(BusinessException.class, () -> taskService.assignTask("task-001", null));
            verify(taskMapper, never()).updateById(any(TaskEntity.class));
        }

        @Test
        @DisplayName("分配任务 - 任务不存在抛出异常")
        void testAssignTaskNotFound() {
            when(taskMapper.selectOne(any(Wrapper.class))).thenReturn(null);

            assertThrows(BusinessException.class, () -> taskService.assignTask("not-exist", 2002L));
        }
    }

    @Nested
    @DisplayName("更新状态测试")
    class UpdateStatusTest {

        @Test
        @DisplayName("更新状态 - PENDING → RUNNING 设置开始时间")
        void testUpdateStatusPendingToRunning() {
            TaskEntity entity = buildEntity("task-001", "PENDING");
            when(taskMapper.selectOne(any(Wrapper.class))).thenReturn(entity);
            when(taskMapper.updateById(any(TaskEntity.class))).thenReturn(1);

            TaskVO result = taskService.updateStatus("task-001", "RUNNING");

            assertEquals("RUNNING", result.getStatus());
            assertNotNull(result.getStartTime());
            verify(kafkaTemplate, times(1)).send(eq("redteam.task.events"), anyString(), anyString());
        }

        @Test
        @DisplayName("更新状态 - RUNNING → COMPLETED 设置结束时间且进度置100")
        void testUpdateStatusRunningToCompleted() {
            TaskEntity entity = buildEntity("task-001", "RUNNING");
            entity.setProgress(50);
            when(taskMapper.selectOne(any(Wrapper.class))).thenReturn(entity);
            when(taskMapper.updateById(any(TaskEntity.class))).thenReturn(1);

            TaskVO result = taskService.updateStatus("task-001", "COMPLETED");

            assertEquals("COMPLETED", result.getStatus());
            assertNotNull(result.getEndTime());
            assertEquals(100, result.getProgress());
        }

        @Test
        @DisplayName("更新状态 - 非法状态字符串拒绝")
        void testUpdateStatusInvalidStatus() {
            assertThrows(BusinessException.class, () -> taskService.updateStatus("task-001", "INVALID"));
            verify(taskMapper, never()).updateById(any(TaskEntity.class));
        }

        @Test
        @DisplayName("更新状态 - 非法流转 COMPLETED → RUNNING 拒绝")
        void testUpdateStatusIllegalTransition() {
            TaskEntity entity = buildEntity("task-001", "COMPLETED");
            when(taskMapper.selectOne(any(Wrapper.class))).thenReturn(entity);

            assertThrows(BusinessException.class, () -> taskService.updateStatus("task-001", "RUNNING"));
            verify(taskMapper, never()).updateById(any(TaskEntity.class));
        }

        @Test
        @DisplayName("更新状态 - PAUSED → RUNNING 允许恢复")
        void testUpdateStatusPausedToRunning() {
            TaskEntity entity = buildEntity("task-001", "PAUSED");
            entity.setStartTime(java.time.LocalDateTime.now());
            when(taskMapper.selectOne(any(Wrapper.class))).thenReturn(entity);
            when(taskMapper.updateById(any(TaskEntity.class))).thenReturn(1);

            TaskVO result = taskService.updateStatus("task-001", "RUNNING");

            assertEquals("RUNNING", result.getStatus());
        }

        @Test
        @DisplayName("更新状态 - 任务不存在抛出异常")
        void testUpdateStatusNotFound() {
            when(taskMapper.selectOne(any(Wrapper.class))).thenReturn(null);

            assertThrows(BusinessException.class, () -> taskService.updateStatus("not-exist", "RUNNING"));
        }
    }

    @Nested
    @DisplayName("更新进度测试")
    class UpdateProgressTest {

        @Test
        @DisplayName("更新进度 - 正常更新成功")
        void testUpdateProgressSuccess() {
            TaskEntity entity = buildEntity("task-001", "RUNNING");
            entity.setProgress(20);
            when(taskMapper.selectOne(any(Wrapper.class))).thenReturn(entity);
            when(taskMapper.updateById(any(TaskEntity.class))).thenReturn(1);

            TaskVO result = taskService.updateProgress("task-001", 60);

            assertEquals(60, result.getProgress());
            assertEquals("RUNNING", result.getStatus());
            verify(kafkaTemplate, times(1)).send(eq("redteam.task.events"), anyString(), anyString());
        }

        @Test
        @DisplayName("更新进度 - 进度100且RUNNING自动转为COMPLETED")
        void testUpdateProgressAutoComplete() {
            TaskEntity entity = buildEntity("task-001", "RUNNING");
            entity.setProgress(80);
            when(taskMapper.selectOne(any(Wrapper.class))).thenReturn(entity);
            when(taskMapper.updateById(any(TaskEntity.class))).thenReturn(1);

            TaskVO result = taskService.updateProgress("task-001", 100);

            assertEquals(100, result.getProgress());
            assertEquals("COMPLETED", result.getStatus());
            assertNotNull(result.getEndTime());
        }

        @Test
        @DisplayName("更新进度 - 进度100且PAUSED自动转为COMPLETED")
        void testUpdateProgressAutoCompleteFromPaused() {
            TaskEntity entity = buildEntity("task-001", "PAUSED");
            entity.setProgress(90);
            when(taskMapper.selectOne(any(Wrapper.class))).thenReturn(entity);
            when(taskMapper.updateById(any(TaskEntity.class))).thenReturn(1);

            TaskVO result = taskService.updateProgress("task-001", 100);

            assertEquals("COMPLETED", result.getStatus());
        }

        @Test
        @DisplayName("更新进度 - 进度100且PENDING不自动完成")
        void testUpdateProgressNoAutoCompleteFromPending() {
            TaskEntity entity = buildEntity("task-001", "PENDING");
            when(taskMapper.selectOne(any(Wrapper.class))).thenReturn(entity);
            when(taskMapper.updateById(any(TaskEntity.class))).thenReturn(1);

            TaskVO result = taskService.updateProgress("task-001", 100);

            assertEquals(100, result.getProgress());
            assertEquals("PENDING", result.getStatus());
        }

        @Test
        @DisplayName("更新进度 - progress为空抛出异常")
        void testUpdateProgressNull() {
            assertThrows(BusinessException.class, () -> taskService.updateProgress("task-001", null));
            verify(taskMapper, never()).updateById(any(TaskEntity.class));
        }

        @Test
        @DisplayName("更新进度 - progress小于0抛出异常")
        void testUpdateProgressNegative() {
            assertThrows(BusinessException.class, () -> taskService.updateProgress("task-001", -1));
        }

        @Test
        @DisplayName("更新进度 - progress大于100抛出异常")
        void testUpdateProgressOver100() {
            assertThrows(BusinessException.class, () -> taskService.updateProgress("task-001", 101));
        }

        @Test
        @DisplayName("更新进度 - 任务不存在抛出异常")
        void testUpdateProgressNotFound() {
            when(taskMapper.selectOne(any(Wrapper.class))).thenReturn(null);

            assertThrows(BusinessException.class, () -> taskService.updateProgress("not-exist", 50));
        }

        @Test
        @DisplayName("更新进度 - 边界值0成功")
        void testUpdateProgressZero() {
            TaskEntity entity = buildEntity("task-001", "RUNNING");
            entity.setProgress(50);
            when(taskMapper.selectOne(any(Wrapper.class))).thenReturn(entity);
            when(taskMapper.updateById(any(TaskEntity.class))).thenReturn(1);

            TaskVO result = taskService.updateProgress("task-001", 0);

            assertEquals(0, result.getProgress());
        }
    }

    @Nested
    @DisplayName("任务统计测试")
    class TaskStatsTest {

        @Test
        @DisplayName("任务统计 - 正常返回各维度统计")
        void testGetTaskStatsSuccess() {
            // selectCount 多次调用（total + 5状态 + 5优先级 = 11 次），统一返回 10L
            when(taskMapper.selectCount(any(Wrapper.class))).thenReturn(10L);
            // 按负责人分组：selectList 返回 3 条任务，ownerId 分别 1001/1002/1001
            TaskEntity t1 = buildEntity("t1", "PENDING");
            t1.setOwnerId(1001L);
            TaskEntity t2 = buildEntity("t2", "RUNNING");
            t2.setOwnerId(1002L);
            TaskEntity t3 = buildEntity("t3", "COMPLETED");
            t3.setOwnerId(1001L);
            when(taskMapper.selectList(any())).thenReturn(List.of(t1, t2, t3));

            TaskStatsDTO stats = taskService.getTaskStats();

            assertNotNull(stats);
            assertEquals(10L, stats.getTotal());
            assertNotNull(stats.getByStatus());
            assertEquals(5, stats.getByStatus().size());
            assertNotNull(stats.getByPriority());
            assertEquals(5, stats.getByPriority().size());
            assertNotNull(stats.getByOwner());
            // 完成率：completedCount * 100.0 / total
            assertTrue(stats.getCompletionRate() >= 0.0);
            Mockito.verify(taskMapper, Mockito.atLeast(1)).selectCount(any(Wrapper.class));
        }

        @Test
        @DisplayName("任务统计 - total 为 0 时完成率为 0")
        void testGetTaskStatsEmpty() {
            when(taskMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
            when(taskMapper.selectList(any())).thenReturn(Collections.emptyList());

            TaskStatsDTO stats = taskService.getTaskStats();

            assertNotNull(stats);
            assertEquals(0L, stats.getTotal());
            assertEquals(0.0, stats.getCompletionRate());
            assertTrue(stats.getByOwner().isEmpty());
        }

        @Test
        @DisplayName("任务统计 - selectCount 返回 null 时使用 0 兜底")
        void testGetTaskStatsNullCount() {
            when(taskMapper.selectCount(any(Wrapper.class))).thenReturn(null);
            when(taskMapper.selectList(any())).thenReturn(Collections.emptyList());

            TaskStatsDTO stats = assertDoesNotThrow(() -> taskService.getTaskStats());

            assertNotNull(stats);
            assertEquals(0L, stats.getTotal());
            assertEquals(0L, stats.getPendingCount());
            assertEquals(0L, stats.getRunningCount());
            assertEquals(0L, stats.getCompletedCount());
        }

        @Test
        @DisplayName("任务统计 - 按负责人分组聚合正确")
        void testGetTaskStatsByOwner() {
            when(taskMapper.selectCount(any(Wrapper.class))).thenReturn(3L);
            TaskEntity t1 = buildEntity("t1", "PENDING");
            t1.setOwnerId(1001L);
            TaskEntity t2 = buildEntity("t2", "RUNNING");
            t2.setOwnerId(1001L);
            TaskEntity t3 = buildEntity("t3", "COMPLETED");
            t3.setOwnerId(1002L);
            TaskEntity t4 = buildEntity("t4", "PENDING");
            t4.setOwnerId(null); // 不计入 byOwner
            when(taskMapper.selectList(any())).thenReturn(List.of(t1, t2, t3, t4));

            TaskStatsDTO stats = taskService.getTaskStats();

            assertNotNull(stats.getByOwner());
            assertEquals(2L, stats.getByOwner().get("1001"));
            assertEquals(1L, stats.getByOwner().get("1002"));
            assertTrue(!stats.getByOwner().containsKey("null"));
        }
    }
}
