package com.redteam.report.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.redteam.common.exception.BusinessException;
import com.redteam.common.result.PageResult;
import com.redteam.report.dto.ReportGenerateDTO;
import com.redteam.report.dto.ReportScheduleCreateDTO;
import com.redteam.report.dto.ReportScheduleVO;
import com.redteam.report.dto.ReportVO;
import com.redteam.report.entity.ReportEntity;
import com.redteam.report.entity.ReportScheduleEntity;
import com.redteam.report.mapper.ReportMapper;
import com.redteam.report.mapper.ReportScheduleMapper;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ScheduledFuture;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ReportSchedulerService} 与 {@link EmailService} 单元测试
 *
 * <p>覆盖调度扫描、Cron 表达式验证、邮件发送成功/失败、定时报告 CRUD、启停切换、
 * 调度执行全流程（含报告生成 + 邮件推送 + 状态更新）。</p>
 *
 * <p>使用 Mockito 隔离 Mapper / ReportService / EmailService / TaskScheduler，
 * 使用 {@link TempDir} 隔离文件系统。</p>
 *
 * @author 红方团队
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReportSchedulerServiceTest {

    @Mock
    private ReportScheduleMapper scheduleMapper;

    @Mock
    private ReportService reportService;

    @Mock
    private EmailService emailService;

    @Mock
    private ReportMapper reportMapper;

    @Mock
    private TaskScheduler taskScheduler;

    @Mock
    private HolidayCalendarService holidayCalendarService;

    @Mock
    private SlackWebhookService slackWebhookService;

    @Mock
    private DingTalkWebhookService dingTalkWebhookService;

    @Mock
    private JavaMailSender mailSender;

    @InjectMocks
    private ReportSchedulerService schedulerService;

    /**
     * 直接测试 EmailService 用的真实实例（注入 mock 的 JavaMailSender）
     */
    private EmailService realEmailService;

    @TempDir
    Path tempDir;

    /**
     * 测试前公共初始化：注入 @Value 字段，配置 TaskScheduler mock。
     */
    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(schedulerService, "pollIntervalMs", 0L);
        ReflectionTestUtils.setField(schedulerService, "maxPollAttempts", 2);
        ReflectionTestUtils.setField(schedulerService, "downloadBaseUrl", "http://localhost:8092/api/v1/reports");

        realEmailService = new EmailService(mailSender);
        ReflectionTestUtils.setField(realEmailService, "fromAddress", "noreply@redteam.com");

        // 默认放行节假日判断（普通工作日），保证现有调度执行测试可进入主流程
        when(holidayCalendarService.shouldExecuteReport(any(LocalDate.class))).thenReturn(true);

        when(taskScheduler.schedule(any(Runnable.class), any(Trigger.class)))
                .thenReturn(mock(ScheduledFuture.class));
    }

    // ===================== Cron 表达式验证 =====================

    /**
     * 测试创建定时报告 - 合法 Cron 表达式应成功
     */
    @Test
    @DisplayName("创建定时报告 - 合法 Cron 应成功并注册")
    void testCreateScheduleWithValidCron() {
        ReportScheduleCreateDTO dto = buildCreateDTO("每周报告", "0 0 9 * * MON");
        when(scheduleMapper.insert(any(ReportScheduleEntity.class))).thenAnswer(invocation -> {
            ReportScheduleEntity entity = invocation.getArgument(0);
            entity.setId(1L);
            return 1;
        });

        ReportScheduleVO vo = schedulerService.createSchedule(dto);

        assertNotNull(vo);
        assertEquals("每周报告", vo.getReportName());
        assertEquals("0 0 9 * * MON", vo.getCronExpression());
        assertEquals("ACTIVE", vo.getStatus());
        verify(scheduleMapper).insert(any(ReportScheduleEntity.class));
        verify(taskScheduler).schedule(any(Runnable.class), any(Trigger.class));
        assertTrue(schedulerService.isRegistered(1L));
    }

    /**
     * 测试创建定时报告 - 非法 Cron 表达式应抛出 BusinessException
     */
    @Test
    @DisplayName("创建定时报告 - 非法 Cron 应抛出 BusinessException")
    void testCreateScheduleWithInvalidCron() {
        ReportScheduleCreateDTO dto = buildCreateDTO("非法报告", "not a cron");
        assertThrows(BusinessException.class, () -> schedulerService.createSchedule(dto));
        verify(scheduleMapper, never()).insert(any());
    }

    /**
     * 测试 Spring CronExpression 解析合法表达式
     */
    @Test
    @DisplayName("Cron 表达式验证 - Spring CronExpression 解析合法表达式")
    void testCronExpressionParseValid() {
        assertDoesNotThrow(() -> CronExpression.parse("0 0 9 * * MON"));
        assertDoesNotThrow(() -> CronExpression.parse("0 */5 * * * *"));
        assertDoesNotThrow(() -> CronExpression.parse("0 0 0 1 1 *"));
    }

    /**
     * 测试 Spring CronExpression 解析非法表达式应抛出异常
     */
    @Test
    @DisplayName("Cron 表达式验证 - 非法表达式应抛出 IllegalArgumentException")
    void testCronExpressionParseInvalid() {
        assertThrows(IllegalArgumentException.class, () -> CronExpression.parse("not a cron"));
        assertThrows(IllegalArgumentException.class, () -> CronExpression.parse("0 0 0 0 0 0"));
    }

    // ===================== 定时报告 CRUD =====================

    /**
     * 测试获取调度详情 - 成功
     */
    @Test
    @DisplayName("获取调度详情 - 应返回 VO")
    void testGetScheduleSuccess() {
        ReportScheduleEntity entity = buildEntity(1L, "ACTIVE");
        when(scheduleMapper.selectById(1L)).thenReturn(entity);

        ReportScheduleVO vo = schedulerService.getSchedule(1L);

        assertNotNull(vo);
        assertEquals(1L, vo.getId());
        assertEquals("每周报告", vo.getReportName());
        assertEquals("ACTIVE", vo.getStatus());
    }

    /**
     * 测试获取调度详情 - 不存在时抛出异常
     */
    @Test
    @DisplayName("获取调度详情 - 不存在时应抛出 BusinessException")
    void testGetScheduleNotFound() {
        when(scheduleMapper.selectById(999L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> schedulerService.getSchedule(999L));
    }

    /**
     * 测试分页查询调度列表
     */
    @Test
    @DisplayName("分页查询调度列表 - 应返回分页结果")
    @SuppressWarnings("unchecked")
    void testListSchedules() {
        ReportScheduleEntity e1 = buildEntity(1L, "ACTIVE");
        ReportScheduleEntity e2 = buildEntity(2L, "INACTIVE");
        Page<ReportScheduleEntity> page = new Page<>(1L, 10L, 2L);
        page.setRecords(Arrays.asList(e1, e2));

        when(scheduleMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

        PageResult<ReportScheduleVO> result = schedulerService.listSchedules(1L, 10L);

        assertNotNull(result);
        assertEquals(2L, result.getTotal());
        assertEquals(2, result.getRecords().size());
        assertEquals(1L, result.getRecords().get(0).getId());
    }

    /**
     * 测试删除调度 - 成功
     */
    @Test
    @DisplayName("删除调度 - 应取消注册并删除记录")
    void testDeleteScheduleSuccess() {
        ReportScheduleEntity entity = buildEntity(1L, "ACTIVE");
        when(scheduleMapper.selectById(1L)).thenReturn(entity);
        when(scheduleMapper.deleteById(1L)).thenReturn(1);

        schedulerService.deleteSchedule(1L);

        verify(scheduleMapper).deleteById(1L);
    }

    /**
     * 测试删除调度 - 不存在时抛出异常
     */
    @Test
    @DisplayName("删除调度 - 不存在时应抛出 BusinessException")
    void testDeleteScheduleNotFound() {
        when(scheduleMapper.selectById(999L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> schedulerService.deleteSchedule(999L));
        verify(scheduleMapper, never()).deleteById(anyLong());
    }

    // ===================== 启停切换 =====================

    /**
     * 测试启停切换 - ACTIVE → INACTIVE
     */
    @Test
    @DisplayName("启停切换 - ACTIVE 应切换为 INACTIVE 并取消注册")
    void testToggleActiveToInactive() {
        ReportScheduleEntity entity = buildEntity(1L, "ACTIVE");
        when(scheduleMapper.selectById(1L)).thenReturn(entity);
        when(scheduleMapper.updateById(any())).thenReturn(1);

        // 先注册
        schedulerService.registerSchedule(entity);
        assertTrue(schedulerService.isRegistered(1L));

        ReportScheduleVO vo = schedulerService.toggleSchedule(1L);

        assertEquals("INACTIVE", vo.getStatus());
        assertFalse(schedulerService.isRegistered(1L));
        verify(scheduleMapper).updateById(any());
    }

    /**
     * 测试启停切换 - INACTIVE → ACTIVE
     */
    @Test
    @DisplayName("启停切换 - INACTIVE 应切换为 ACTIVE 并注册")
    void testToggleInactiveToActive() {
        ReportScheduleEntity entity = buildEntity(1L, "INACTIVE");
        when(scheduleMapper.selectById(1L)).thenReturn(entity);
        when(scheduleMapper.updateById(any())).thenReturn(1);

        assertFalse(schedulerService.isRegistered(1L));

        ReportScheduleVO vo = schedulerService.toggleSchedule(1L);

        assertEquals("ACTIVE", vo.getStatus());
        assertTrue(schedulerService.isRegistered(1L));
        verify(scheduleMapper).updateById(any());
        verify(taskScheduler).schedule(any(Runnable.class), any(Trigger.class));
    }

    /**
     * 测试启停切换 - 不存在时抛出异常
     */
    @Test
    @DisplayName("启停切换 - 不存在时应抛出 BusinessException")
    void testToggleNotFound() {
        when(scheduleMapper.selectById(999L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> schedulerService.toggleSchedule(999L));
    }

    // ===================== 调度执行 =====================

    /**
     * 测试调度执行 - 成功路径：生成报告 → 发送邮件 → 更新状态为 SUCCESS
     */
    @Test
    @DisplayName("调度执行 - 成功应发送邮件并更新状态为 SUCCESS")
    void testExecuteScheduleSuccess() {
        ReportScheduleEntity schedule = buildEntity(1L, "ACTIVE");
        File reportFile = tempDir.resolve("test-report.pdf").toFile();
        assertDoesNotThrow(() -> reportFile.createNewFile());

        ReportVO pending = new ReportVO();
        pending.setReportId("rpt-001");
        pending.setStatus("PENDING");

        ReportVO completed = new ReportVO();
        completed.setReportId("rpt-001");
        completed.setStatus("COMPLETED");
        completed.setFilePath(reportFile.getAbsolutePath());

        when(scheduleMapper.selectById(1L)).thenReturn(schedule);
        when(reportService.generateReport(any(ReportGenerateDTO.class))).thenReturn(pending);
        when(reportService.getReport("rpt-001")).thenReturn(completed);
        when(scheduleMapper.updateById(any())).thenReturn(1);

        schedulerService.executeSchedule(1L);

        verify(reportService).generateReport(any(ReportGenerateDTO.class));
        verify(emailService).sendReport(eq("alice@redteam.com,bob@redteam.com"), anyString(), anyString(),
                any(), anyString());
        verify(scheduleMapper, atLeastOnce()).updateById(any());
    }

    /**
     * 测试调度执行 - 报告生成失败时状态应为 FAILED
     */
    @Test
    @DisplayName("调度执行 - 报告生成失败应更新状态为 FAILED 且不发送邮件")
    void testExecuteScheduleReportFailed() {
        ReportScheduleEntity schedule = buildEntity(1L, "ACTIVE");
        ReportVO pending = new ReportVO();
        pending.setReportId("rpt-fail");
        pending.setStatus("PENDING");

        ReportVO failed = new ReportVO();
        failed.setReportId("rpt-fail");
        failed.setStatus("FAILED");

        when(scheduleMapper.selectById(1L)).thenReturn(schedule);
        when(reportService.generateReport(any(ReportGenerateDTO.class))).thenReturn(pending);
        when(reportService.getReport("rpt-fail")).thenReturn(failed);
        when(scheduleMapper.updateById(any())).thenReturn(1);

        schedulerService.executeSchedule(1L);

        verify(emailService, never()).sendReport(anyString(), anyString(), anyString(), any(), anyString());
        verify(scheduleMapper, atLeastOnce()).updateById(any());
    }

    /**
     * 测试调度执行 - 调度不存在时应安全返回且不更新状态
     */
    @Test
    @DisplayName("调度执行 - 调度不存在时应安全返回且不更新状态")
    void testExecuteScheduleNotFound() {
        when(scheduleMapper.selectById(999L)).thenReturn(null);

        assertDoesNotThrow(() -> schedulerService.executeSchedule(999L));

        verify(reportService, never()).generateReport(any());
        verify(scheduleMapper, never()).updateById(any());
    }

    /**
     * 测试调度执行 - 非 ACTIVE 状态应跳过执行
     */
    @Test
    @DisplayName("调度执行 - INACTIVE 状态应跳过执行")
    void testExecuteScheduleInactive() {
        ReportScheduleEntity schedule = buildEntity(1L, "INACTIVE");
        when(scheduleMapper.selectById(1L)).thenReturn(schedule);

        schedulerService.executeSchedule(1L);

        verify(reportService, never()).generateReport(any());
        verify(scheduleMapper, never()).updateById(any());
    }

    /**
     * 测试调度执行 - generateReport 抛出异常时状态应为 FAILED
     */
    @Test
    @DisplayName("调度执行 - 报告生成异常应更新状态为 FAILED")
    void testExecuteScheduleWithException() {
        ReportScheduleEntity schedule = buildEntity(1L, "ACTIVE");
        when(scheduleMapper.selectById(1L)).thenReturn(schedule);
        when(reportService.generateReport(any(ReportGenerateDTO.class)))
                .thenThrow(new RuntimeException("生成服务异常"));
        when(scheduleMapper.updateById(any())).thenReturn(1);

        assertDoesNotThrow(() -> schedulerService.executeSchedule(1L));

        verify(scheduleMapper, atLeastOnce()).updateById(any());
    }

    // ===================== 调度扫描 =====================

    /**
     * 测试扫描调度 - 应重新注册未注册的 ACTIVE 调度
     */
    @Test
    @DisplayName("扫描调度 - 应重新注册未注册的 ACTIVE 调度")
    void testScanScheduledReportsReRegister() {
        ReportScheduleEntity active1 = buildEntity(1L, "ACTIVE");
        ReportScheduleEntity active2 = buildEntity(2L, "ACTIVE");
        when(scheduleMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Arrays.asList(active1, active2));

        schedulerService.scanScheduledReports();

        verify(taskScheduler, times(2)).schedule(any(Runnable.class), any(Trigger.class));
        assertTrue(schedulerService.isRegistered(1L));
        assertTrue(schedulerService.isRegistered(2L));
    }

    /**
     * 测试扫描调度 - 扫描异常不中断服务
     */
    @Test
    @DisplayName("扫描调度 - 异常应被捕获不影响后续执行")
    void testScanScheduledReportsWithException() {
        when(scheduleMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenThrow(new RuntimeException("数据库异常"));

        assertDoesNotThrow(() -> schedulerService.scanScheduledReports());
    }

    // ===================== 注册/取消注册 =====================

    /**
     * 测试注册调度 - INACTIVE 状态不应注册
     */
    @Test
    @DisplayName("注册调度 - INACTIVE 状态不应注册")
    void testRegisterScheduleInactive() {
        ReportScheduleEntity entity = buildEntity(1L, "INACTIVE");
        schedulerService.registerSchedule(entity);
        assertFalse(schedulerService.isRegistered(1L));
        verify(taskScheduler, never()).schedule(any(Runnable.class), any(Trigger.class));
    }

    /**
     * 测试注册调度 - 非法 Cron 表达式不应注册
     */
    @Test
    @DisplayName("注册调度 - 非法 Cron 表达式应跳过注册")
    void testRegisterScheduleInvalidCron() {
        ReportScheduleEntity entity = buildEntity(1L, "ACTIVE");
        entity.setCronExpression("invalid cron");
        schedulerService.registerSchedule(entity);
        assertFalse(schedulerService.isRegistered(1L));
    }

    /**
     * 测试取消注册 - 已注册的任务应被取消
     */
    @Test
    @DisplayName("取消注册 - 已注册任务应被取消")
    void testUnregisterSchedule() {
        ReportScheduleEntity entity = buildEntity(1L, "ACTIVE");
        schedulerService.registerSchedule(entity);
        assertTrue(schedulerService.isRegistered(1L));

        schedulerService.unregisterSchedule(1L);
        assertFalse(schedulerService.isRegistered(1L));
    }

    // ===================== 执行历史 =====================

    /**
     * 测试执行历史 - 应返回关联报告列表
     */
    @Test
    @DisplayName("执行历史 - 应返回按报告名匹配的历史记录")
    void testGetHistory() {
        ReportScheduleEntity schedule = buildEntity(1L, "ACTIVE");
        when(scheduleMapper.selectById(1L)).thenReturn(schedule);

        ReportEntity r1 = new ReportEntity();
        r1.setReportName("每周报告");
        r1.setStatus("COMPLETED");
        ReportEntity r2 = new ReportEntity();
        r2.setReportName("每周报告");
        r2.setStatus("FAILED");

        when(reportMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Arrays.asList(r1, r2));

        List<ReportScheduleVO> history = schedulerService.getHistory(1L);

        assertNotNull(history);
        assertEquals(2, history.size());
    }

    // ===================== EmailService 直接测试 =====================

    /**
     * 测试邮件发送 - 报告邮件发送成功
     */
    @Test
    @DisplayName("邮件发送 - 报告邮件应调用 JavaMailSender.send")
    void testEmailSendReportSuccess() {
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage((Session) null));

        assertDoesNotThrow(() -> realEmailService.sendReport(
                "alice@redteam.com", "测试报告", "<html>内容</html>",
                "附件内容".getBytes(), "report.pdf"));

        verify(mailSender).send(any(MimeMessage.class));
    }

    /**
     * 测试邮件发送 - 空收件人应跳过发送
     */
    @Test
    @DisplayName("邮件发送 - 空收件人应跳过发送")
    void testEmailSendReportEmptyRecipient() {
        realEmailService.sendReport("", "主题", "<html>内容</html>", null, null);
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    /**
     * 测试邮件发送 - 异常应被捕获不抛出
     */
    @Test
    @DisplayName("邮件发送 - 异常应被捕获不影响调用方")
    void testEmailSendReportFailure() {
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage((Session) null));
        doThrow(new RuntimeException("SMTP 连接失败"))
                .when(mailSender).send(any(MimeMessage.class));

        assertDoesNotThrow(() -> realEmailService.sendReport(
                "alice@redteam.com", "测试报告", "<html>内容</html>",
                null, null));
    }

    /**
     * 测试简单邮件发送 - 成功
     */
    @Test
    @DisplayName("简单邮件发送 - 应调用 JavaMailSender.send")
    void testEmailSendSimpleMailSuccess() {
        assertDoesNotThrow(() -> realEmailService.sendSimpleMail(
                "alice@redteam.com", "通知", "正文内容"));

        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    /**
     * 测试简单邮件发送 - 异常应被捕获
     */
    @Test
    @DisplayName("简单邮件发送 - 异常应被捕获不影响调用方")
    void testEmailSendSimpleMailFailure() {
        doThrow(new RuntimeException("SMTP 异常"))
                .when(mailSender).send(any(SimpleMailMessage.class));

        assertDoesNotThrow(() -> realEmailService.sendSimpleMail(
                "alice@redteam.com", "通知", "正文"));
    }

    // ===================== 辅助方法 =====================

    /**
     * 构造测试用创建 DTO
     *
     * @param name 报告名称
     * @param cron cron 表达式
     * @return 创建 DTO
     */
    private ReportScheduleCreateDTO buildCreateDTO(String name, String cron) {
        ReportScheduleCreateDTO dto = new ReportScheduleCreateDTO();
        dto.setReportName(name);
        dto.setReportType("PENETRATION_TEST");
        dto.setCronExpression(cron);
        dto.setRecipients("alice@redteam.com,bob@redteam.com");
        dto.setTemplateName("penetration-test");
        dto.setTargetId(2001L);
        dto.setCreatedBy("admin");
        return dto;
    }

    /**
     * 构造测试用调度实体
     *
     * @param id     调度ID
     * @param status 状态
     * @return 调度实体
     */
    private ReportScheduleEntity buildEntity(Long id, String status) {
        ReportScheduleEntity entity = new ReportScheduleEntity();
        entity.setId(id);
        entity.setReportName("每周报告");
        entity.setReportType("PENETRATION_TEST");
        entity.setCronExpression("0 0 9 * * MON");
        entity.setRecipients("alice@redteam.com,bob@redteam.com");
        entity.setTemplateName("penetration-test");
        entity.setTargetId(2001L);
        entity.setStatus(status);
        entity.setCreatedBy("admin");
        return entity;
    }
}
