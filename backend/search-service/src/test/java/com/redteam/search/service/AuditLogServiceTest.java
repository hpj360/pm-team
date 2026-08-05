package com.redteam.search.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.redteam.common.annotation.AuditLog;
import com.redteam.common.aspect.AuditLogAspect;
import com.redteam.common.entity.AuditLogEntity;
import com.redteam.common.mapper.AuditLogMapper;
import com.redteam.common.result.PageResult;
import com.redteam.common.service.impl.AuditLogServiceImpl;
import com.redteam.common.util.UserContext;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 审计日志服务单元测试
 *
 * <p>覆盖 {@link AuditLogServiceImpl} 的手动记录、查询、CSV 导出、统计，
 * 以及 {@link AuditLogAspect} 切面拦截逻辑。</p>
 *
 * <p>使用同步执行器（{@code Runnable::run}）替代真实线程池，
 * 使异步插入在调用线程内同步完成，便于 Mockito 立即验证。</p>
 *
 * @author 红方团队
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuditLogServiceTest {

    /**
     * 同步执行器：在调用线程内立即执行 Runnable，便于测试验证
     */
    private static final Executor SYNC_EXECUTOR = Runnable::run;

    @Mock
    private AuditLogMapper auditLogMapper;

    private AuditLogServiceImpl auditLogService;

    /**
     * 测试前设置：注入同步执行器，确保异步任务同步完成
     */
    @BeforeEach
    void setUp() {
        auditLogService = new AuditLogServiceImpl(auditLogMapper, SYNC_EXECUTOR);
        // 设置用户上下文
        UserContext.setUserId(100L);
        UserContext.setUsername("admin");
    }

    /**
     * 测试后清理：清除用户上下文与请求上下文
     */
    @AfterEach
    void tearDown() {
        UserContext.clear();
        RequestContextHolder.resetRequestAttributes();
    }

    // ==================== 1. testRecord_Success ====================

    /**
     * 手动记录审计日志成功：应构建实体并异步（同步执行器）插入
     */
    @Test
    @DisplayName("手动记录审计日志：应调用 insert 并填充用户信息")
    void testRecord_Success() {
        // 模拟 insert 返回 1
        when(auditLogMapper.insert(any(AuditLogEntity.class))).thenReturn(1);

        // 调用记录
        auditLogService.record("UPLOAD", "FILE", "1001", "test.pdf", "{\"size\":1024}");

        // 验证 insert 被调用
        ArgumentCaptor<AuditLogEntity> captor = ArgumentCaptor.forClass(AuditLogEntity.class);
        verify(auditLogMapper, times(1)).insert(captor.capture());

        AuditLogEntity saved = captor.getValue();
        assertEquals("UPLOAD", saved.getAction(), "action 应为 UPLOAD");
        assertEquals("FILE", saved.getResourceType(), "resourceType 应为 FILE");
        assertEquals("1001", saved.getResourceId(), "resourceId 应为 1001");
        assertEquals("test.pdf", saved.getResourceName(), "resourceName 应为 test.pdf");
        assertEquals("SUCCESS", saved.getStatus(), "status 应为 SUCCESS");
        assertEquals(100L, saved.getUserId(), "userId 应为当前用户 100");
        assertEquals("admin", saved.getUsername(), "username 应为 admin");
        assertNotNull(saved.getCreatedAt(), "createdAt 不应为空");
    }

    // ==================== 2. testQuery_ByAction ====================

    /**
     * 按操作类型筛选查询审计日志
     */
    @Test
    @DisplayName("查询审计日志：按操作类型筛选应传递 action 参数")
    void testQuery_ByAction() {
        // 构造分页返回数据
        Page<AuditLogEntity> mockPage = new Page<>(1, 20, 2L);
        List<AuditLogEntity> records = Arrays.asList(
                buildEntity(1L, 100L, "admin", "SEARCH", "FILE"),
                buildEntity(2L, 200L, "guest", "SEARCH", "FILE"));
        mockPage.setRecords(records);

        when(auditLogMapper.selectByConditionsPage(any(), eq(100L), eq("SEARCH"),
                eq("FILE"), any(), any())).thenReturn(mockPage);

        PageResult<AuditLogEntity> result = auditLogService.query(
                100L, "SEARCH", "FILE", null, null, 1, 20);

        // 验证分页结果
        assertNotNull(result, "返回结果不应为 null");
        assertEquals(1L, result.getCurrent(), "当前页应为 1");
        assertEquals(20L, result.getSize(), "每页大小应为 20");
        assertEquals(2L, result.getTotal(), "总数应为 2");
        assertEquals(2, result.getRecords().size(), "记录数应为 2");
        assertEquals("SEARCH", result.getRecords().get(0).getAction(), "第一条 action 应为 SEARCH");

        // 验证 mapper 被正确调用
        verify(auditLogMapper, times(1)).selectByConditionsPage(any(), eq(100L),
                eq("SEARCH"), eq("FILE"), eq(null), eq(null));
    }

    // ==================== 3. testQuery_ByUser ====================

    /**
     * 按用户筛选查询审计日志
     */
    @Test
    @DisplayName("查询审计日志：按用户筛选应传递 userId 参数")
    void testQuery_ByUser() {
        Page<AuditLogEntity> mockPage = new Page<>(1, 20, 1L);
        mockPage.setRecords(Collections.singletonList(
                buildEntity(1L, 100L, "admin", "DOWNLOAD", "FILE")));

        when(auditLogMapper.selectByConditionsPage(any(), eq(100L), eq(null),
                eq(null), any(), any())).thenReturn(mockPage);

        PageResult<AuditLogEntity> result = auditLogService.query(
                100L, null, null, null, null, 1, 20);

        assertNotNull(result, "返回结果不应为 null");
        assertEquals(1L, result.getTotal(), "总数应为 1");
        assertEquals(1, result.getRecords().size(), "记录数应为 1");
        assertEquals(100L, result.getRecords().get(0).getUserId(), "userId 应为 100");

        verify(auditLogMapper, times(1)).selectByConditionsPage(any(), eq(100L),
                eq(null), eq(null), eq(null), eq(null));
    }

    // ==================== 4. testExportCsv ====================

    /**
     * 导出审计日志 CSV：应包含表头与数据行
     */
    @Test
    @DisplayName("导出CSV：应包含表头及数据行，字段以双引号包裹")
    void testExportCsv() {
        List<AuditLogEntity> list = Arrays.asList(
                buildEntity(1L, 100L, "admin", "SEARCH", "FILE"),
                buildEntity(2L, 200L, "guest", "DOWNLOAD", "FILE"));

        when(auditLogMapper.selectByConditions(eq(100L), eq("SEARCH"),
                eq("FILE"), any(), any())).thenReturn(list);

        String csv = auditLogService.exportCsv(100L, "SEARCH", "FILE", null, null);

        // 验证 CSV 内容
        assertNotNull(csv, "CSV 不应为 null");
        String[] lines = csv.split("\n");
        assertTrue(lines.length >= 3, "CSV 应至少包含表头 + 2 行数据");
        // 表头包含操作类型
        assertTrue(lines[0].contains("操作类型"), "表头应包含'操作类型'");
        // 数据行包含操作值
        assertTrue(lines[1].contains("SEARCH"), "第一行应包含 SEARCH");
        assertTrue(lines[2].contains("DOWNLOAD"), "第二行应包含 DOWNLOAD");
        // 验证字段以双引号包裹
        assertTrue(lines[1].contains("\"SEARCH\""), "SEARCH 字段应以双引号包裹");

        verify(auditLogMapper, times(1)).selectByConditions(eq(100L), eq("SEARCH"),
                eq("FILE"), eq(null), eq(null));
    }

    // ==================== 5. testStats ====================

    /**
     * 审计统计：按操作类型分组应返回 action -> count 映射
     */
    @Test
    @DisplayName("审计统计：应按操作类型分组返回数量映射")
    void testStats() {
        // 构造分组统计返回数据
        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, Object> row1 = new HashMap<>();
        row1.put("action", "SEARCH");
        row1.put("cnt", 50L);
        rows.add(row1);
        Map<String, Object> row2 = new HashMap<>();
        row2.put("action", "DOWNLOAD");
        row2.put("cnt", 30L);
        rows.add(row2);

        when(auditLogMapper.countGroupByAction(any(), any())).thenReturn(rows);

        Map<String, Long> stats = auditLogService.stats(null, null);

        assertNotNull(stats, "统计结果不应为 null");
        assertEquals(2, stats.size(), "应包含 2 种操作类型");
        assertEquals(50L, stats.get("SEARCH"), "SEARCH 数量应为 50");
        assertEquals(30L, stats.get("DOWNLOAD"), "DOWNLOAD 数量应为 30");

        verify(auditLogMapper, times(1)).countGroupByAction(eq(null), eq(null));
    }

    // ==================== 6. testAspect_AfterReturning ====================

    /**
     * AOP 切面拦截成功：方法返回后应记录 SUCCESS 审计日志
     */
    @Test
    @DisplayName("AOP切面：方法正常返回后应异步（同步执行器）插入审计日志")
    void testAspect_AfterReturning() throws Exception {
        // 构造切面实例并注入 mock mapper + 同步执行器
        AuditLogAspect aspect = new AuditLogAspect();
        ReflectionTestUtils.setField(aspect, "auditLogMapper", auditLogMapper);
        ReflectionTestUtils.setField(aspect, "auditLogExecutor", SYNC_EXECUTOR);

        // 设置 HTTP 请求上下文（用于采集 IP / User-Agent）
        MockHttpServletRequest mockRequest = new MockHttpServletRequest();
        mockRequest.addHeader("X-Forwarded-For", "192.168.1.100");
        mockRequest.addHeader("User-Agent", "JUnit-Test-Agent");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(mockRequest));

        // 构造 mock JoinPoint（指向测试目标方法）
        Method testMethod = TestTargetService.class.getMethod("viewFile", Long.class, String.class);
        MethodSignature signature = mock(MethodSignature.class);
        when(signature.getMethod()).thenReturn(testMethod);
        JoinPoint joinPoint = mock(JoinPoint.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(joinPoint.getArgs()).thenReturn(new Object[]{999L, "secret.pdf"});

        // 构造 AuditLog 注解（mock）
        AuditLog auditLog = mock(AuditLog.class);
        when(auditLog.action()).thenReturn("VIEW");
        when(auditLog.resourceType()).thenReturn("FILE");
        when(auditLog.resourceIdParam()).thenReturn("id");
        when(auditLog.resourceNameParam()).thenReturn("fileName");
        when(auditLog.description()).thenReturn("查看文件");

        // 模拟 insert 返回
        when(auditLogMapper.insert(any(AuditLogEntity.class))).thenReturn(1);

        // 触发切面通知
        aspect.afterReturning(joinPoint, auditLog, "result-value");

        // 验证 insert 被调用
        ArgumentCaptor<AuditLogEntity> captor = ArgumentCaptor.forClass(AuditLogEntity.class);
        verify(auditLogMapper, times(1)).insert(captor.capture());

        AuditLogEntity entity = captor.getValue();
        assertEquals("VIEW", entity.getAction(), "action 应为 VIEW");
        assertEquals("FILE", entity.getResourceType(), "resourceType 应为 FILE");
        assertEquals("SUCCESS", entity.getStatus(), "status 应为 SUCCESS（方法正常返回）");
        assertEquals("999", entity.getResourceId(), "resourceId 应从 id 参数提取为 999");
        assertEquals("secret.pdf", entity.getResourceName(), "resourceName 应从 fileName 参数提取");
        assertEquals("192.168.1.100", entity.getIpAddress(), "IP 应从 X-Forwarded-For 获取");
        assertEquals("JUnit-Test-Agent", entity.getUserAgent(), "User-Agent 应匹配");
        assertEquals(100L, entity.getUserId(), "userId 应为当前用户 100");
        assertEquals("admin", entity.getUsername(), "username 应为 admin");
    }

    // ==================== 辅助方法与类 ====================

    /**
     * 构造测试用审计日志实体
     *
     * @param id           主键ID
     * @param userId       用户ID
     * @param username      用户名
     * @param action        操作类型
     * @param resourceType  资源类型
     * @return 审计日志实体
     */
    private AuditLogEntity buildEntity(Long id, Long userId, String username,
                                       String action, String resourceType) {
        AuditLogEntity entity = new AuditLogEntity();
        entity.setId(id);
        entity.setUserId(userId);
        entity.setUsername(username);
        entity.setAction(action);
        entity.setResourceType(resourceType);
        entity.setStatus("SUCCESS");
        entity.setCreatedAt(LocalDateTime.now());
        return entity;
    }

    /**
     * 测试目标服务类（用于构造 mock JoinPoint 的 Method）
     *
     * <p>方法参数使用 {@code @PathVariable} 注解标注，与真实 Controller 一致，
     * 切面通过注解 value 提取参数名（不依赖 -parameters 编译选项）。</p>
     */
    public static class TestTargetService {

        /**
         * 查看文件（用于切面测试）
         *
         * @param id       文件ID
         * @param fileName 文件名
         * @return 结果
         */
        @AuditLog(action = "VIEW", resourceType = "FILE")
        public String viewFile(@PathVariable("id") Long id,
                                @PathVariable("fileName") String fileName) {
            return "ok";
        }
    }
}
