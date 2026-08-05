package com.redteam.report.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.redteam.common.exception.BusinessException;
import com.redteam.common.result.PageResult;
import com.redteam.common.result.ResultCode;
import com.redteam.report.dto.ReportGenerateDTO;
import com.redteam.report.dto.ReportScheduleCreateDTO;
import com.redteam.report.dto.ReportScheduleVO;
import com.redteam.report.dto.ReportVO;
import com.redteam.report.entity.ReportEntity;
import com.redteam.report.entity.ReportScheduleEntity;
import com.redteam.report.mapper.ReportMapper;
import com.redteam.report.mapper.ReportScheduleMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.stream.Collectors;

/**
 * 定时报告调度服务
 *
 * <p>基于 Spring {@link TaskScheduler} 动态注册 Cron 任务，实现定时报告的自动生成与邮件推送。
 * 核心能力：</p>
 * <ul>
 *   <li>启动时加载所有 {@code ACTIVE} 调度并注册到 {@link TaskScheduler}</li>
 *   <li>新增/启停/删除调度时动态注册或取消</li>
 *   <li>每分钟扫描兜底，恢复因异常未注册的调度</li>
 *   <li>触发报告生成 → 等待完成 → 邮件推送 → 更新执行状态</li>
 * </ul>
 *
 * <p><b>容错策略：</b></p>
 * <ul>
 *   <li>邮件发送失败不影响报告生成</li>
 *   <li>单次执行异常不中断调度器</li>
 *   <li>Cron 表达式使用 Spring {@link CronExpression} 验证</li>
 * </ul>
 *
 * @author 红方团队
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportSchedulerService {

    /**
     * ACTIVE 状态常量
     */
    private static final String STATUS_ACTIVE = "ACTIVE";

    /**
     * INACTIVE 状态常量
     */
    private static final String STATUS_INACTIVE = "INACTIVE";

    /**
     * 执行成功状态
     */
    private static final String RUN_STATUS_SUCCESS = "SUCCESS";

    /**
     * 执行失败状态
     */
    private static final String RUN_STATUS_FAILED = "FAILED";

    /**
     * 定时报告默认使用 PDF 格式生成（便于邮件附件发送）
     */
    private static final String DEFAULT_FORMAT = "PDF";

    /**
     * 历史记录查询上限
     */
    private static final int HISTORY_LIMIT = 20;

    /**
     * 推送通道：邮件
     */
    private static final String WEBHOOK_EMAIL = "EMAIL";

    /**
     * 推送通道：Slack
     */
    private static final String WEBHOOK_SLACK = "SLACK";

    /**
     * 推送通道：钉钉
     */
    private static final String WEBHOOK_DINGTALK = "DINGTALK";

    /**
     * 推送通道：全部
     */
    private static final String WEBHOOK_ALL = "ALL";

    private final ReportScheduleMapper scheduleMapper;
    private final ReportService reportService;
    private final EmailService emailService;
    private final ReportMapper reportMapper;
    private final TaskScheduler taskScheduler;
    private final HolidayCalendarService holidayCalendarService;
    private final SlackWebhookService slackWebhookService;
    private final DingTalkWebhookService dingTalkWebhookService;

    /**
     * 已注册任务句柄，key 为 scheduleId，用于动态取消
     */
    private final Map<Long, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();

    /**
     * 报告生成完成状态轮询间隔（毫秒），默认 2 秒
     */
    @Value("${report.schedule.poll-interval-ms:2000}")
    private long pollIntervalMs;

    /**
     * 报告生成完成状态轮询最大次数，默认 150 次（5 分钟超时）
     */
    @Value("${report.schedule.max-poll-attempts:150}")
    private int maxPollAttempts;

    /**
     * 报告下载基础 URL（用于 webhook 推送链接）
     */
    @Value("${report.download-base-url:http://localhost:8092/api/v1/reports}")
    private String downloadBaseUrl;

    /**
     * 服务启动后加载所有 ACTIVE 调度并注册。
     *
     * <p>由 Spring 在依赖注入完成后调用，确保调度器在服务启动后立即生效。</p>
     */
    @PostConstruct
    public void init() {
        try {
            loadAndRegisterAll();
        } catch (Exception e) {
            log.error("启动时加载定时报告调度失败", e);
        }
    }

    /**
     * 服务销毁前取消所有已注册任务，避免线程泄漏。
     */
    @PreDestroy
    public void destroy() {
        for (Map.Entry<Long, ScheduledFuture<?>> entry : scheduledTasks.entrySet()) {
            try {
                entry.getValue().cancel(false);
            } catch (Exception e) {
                log.warn("取消定时任务异常: scheduleId={}", entry.getKey(), e);
            }
        }
        scheduledTasks.clear();
        log.info("所有定时报告任务已取消");
    }

    /**
     * 每 1 分钟扫描一次待执行的定时报告（兜底机制）。
     *
     * <p>主要用于恢复因异常未注册到 {@link TaskScheduler} 的 ACTIVE 调度。
     * 已注册的调度不会重复注册。</p>
     */
    @Scheduled(fixedDelay = 60000)
    public void scanScheduledReports() {
        try {
            LambdaQueryWrapper<ReportScheduleEntity> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(ReportScheduleEntity::getStatus, STATUS_ACTIVE);
            List<ReportScheduleEntity> actives = scheduleMapper.selectList(wrapper);
            for (ReportScheduleEntity schedule : actives) {
                if (!scheduledTasks.containsKey(schedule.getId())) {
                    log.info("扫描发现未注册的 ACTIVE 调度，重新注册: id={}", schedule.getId());
                    registerSchedule(schedule);
                }
            }
        } catch (Exception e) {
            log.error("扫描定时报告调度异常", e);
        }
    }

    /**
     * 加载所有 ACTIVE 调度并注册到 {@link TaskScheduler}。
     */
    public void loadAndRegisterAll() {
        LambdaQueryWrapper<ReportScheduleEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ReportScheduleEntity::getStatus, STATUS_ACTIVE);
        List<ReportScheduleEntity> actives = scheduleMapper.selectList(wrapper);
        for (ReportScheduleEntity schedule : actives) {
            registerSchedule(schedule);
        }
        log.info("已加载 {} 条 ACTIVE 定时报告调度", actives.size());
    }

    /**
     * 注册单条调度到 {@link TaskScheduler}。
     *
     * <p>若已存在同 ID 的任务，先取消旧任务再注册新任务。
     * 仅 {@code ACTIVE} 状态的调度会被注册。</p>
     *
     * @param schedule 调度配置
     */
    public void registerSchedule(ReportScheduleEntity schedule) {
        if (schedule == null || schedule.getId() == null) {
            return;
        }
        Long id = schedule.getId();
        unregisterSchedule(id);
        if (!STATUS_ACTIVE.equals(schedule.getStatus())) {
            return;
        }
        try {
            CronExpression.parse(schedule.getCronExpression());
            CronTrigger trigger = new CronTrigger(schedule.getCronExpression());
            ScheduledFuture<?> future = taskScheduler.schedule(() -> executeSchedule(id), trigger);
            scheduledTasks.put(id, future);
            log.info("定时报告已注册: id={}, cron={}", id, schedule.getCronExpression());
        } catch (IllegalArgumentException e) {
            log.error("Cron 表达式不合法，注册失败: id={}, cron={}", id, schedule.getCronExpression(), e);
        } catch (Exception e) {
            log.error("注册定时报告失败: id={}, cron={}", id, schedule.getCronExpression(), e);
        }
    }

    /**
     * 取消指定调度的注册。
     *
     * @param id 调度ID
     */
    public void unregisterSchedule(Long id) {
        ScheduledFuture<?> future = scheduledTasks.remove(id);
        if (future != null) {
            try {
                future.cancel(false);
                log.info("定时报告已取消注册: id={}", id);
            } catch (Exception e) {
                log.warn("取消定时任务异常: id={}", id, e);
            }
        }
    }

    /**
     * 判断指定调度是否已注册。
     *
     * @param id 调度ID
     * @return 是否已注册
     */
    public boolean isRegistered(Long id) {
        return scheduledTasks.containsKey(id);
    }

    /**
     * 创建定时报告配置。
     *
     * <p>校验 Cron 表达式后入库，并立即注册到 {@link TaskScheduler}。</p>
     *
     * @param dto 创建请求
     * @return 创建后的 VO
     */
    public ReportScheduleVO createSchedule(ReportScheduleCreateDTO dto) {
        validateCronExpression(dto.getCronExpression());
        ReportScheduleEntity entity = new ReportScheduleEntity();
        entity.setReportName(dto.getReportName());
        entity.setReportType(dto.getReportType());
        entity.setCronExpression(dto.getCronExpression());
        entity.setRecipients(dto.getRecipients());
        entity.setTemplateName(dto.getTemplateName());
        entity.setTargetId(dto.getTargetId());
        entity.setWebhookType(StrUtil.isBlank(dto.getWebhookType()) ? WEBHOOK_EMAIL : dto.getWebhookType().toUpperCase());
        entity.setStatus(STATUS_ACTIVE);
        entity.setCreatedBy(dto.getCreatedBy());
        scheduleMapper.insert(entity);
        log.info("定时报告配置已创建: id={}, name={}, webhookType={}",
                entity.getId(), entity.getReportName(), entity.getWebhookType());
        registerSchedule(entity);
        return toVO(entity);
    }

    /**
     * 切换调度的启停状态（ACTIVE ↔ INACTIVE）。
     *
     * @param id 调度ID
     * @return 切换后的 VO
     */
    public ReportScheduleVO toggleSchedule(Long id) {
        ReportScheduleEntity entity = getScheduleEntityById(id);
        String newStatus = STATUS_ACTIVE.equals(entity.getStatus()) ? STATUS_INACTIVE : STATUS_ACTIVE;
        entity.setStatus(newStatus);
        entity.setUpdatedAt(LocalDateTime.now());
        scheduleMapper.updateById(entity);
        if (STATUS_ACTIVE.equals(newStatus)) {
            registerSchedule(entity);
        } else {
            unregisterSchedule(id);
        }
        log.info("定时报告状态切换: id={}, newStatus={}", id, newStatus);
        return toVO(entity);
    }

    /**
     * 删除定时报告配置。
     *
     * <p>先取消注册，再从数据库删除。</p>
     *
     * @param id 调度ID
     */
    public void deleteSchedule(Long id) {
        getScheduleEntityById(id);
        unregisterSchedule(id);
        scheduleMapper.deleteById(id);
        log.info("定时报告已删除: id={}", id);
    }

    /**
     * 获取调度详情。
     *
     * @param id 调度ID
     * @return 调度 VO
     */
    public ReportScheduleVO getSchedule(Long id) {
        return toVO(getScheduleEntityById(id));
    }

    /**
     * 分页查询调度列表。
     *
     * @param current 当前页码
     * @param size    每页大小
     * @return 分页结果
     */
    public PageResult<ReportScheduleVO> listSchedules(Long current, Long size) {
        Page<ReportScheduleEntity> page = new Page<>(current, size);
        LambdaQueryWrapper<ReportScheduleEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(ReportScheduleEntity::getCreatedAt);
        Page<ReportScheduleEntity> result = scheduleMapper.selectPage(page, wrapper);
        List<ReportScheduleVO> records = result.getRecords().stream().map(this::toVO).collect(Collectors.toList());
        return PageResult.of(current, size, result.getTotal(), records);
    }

    /**
     * 查询调度执行历史。
     *
     * <p>基于报告名称匹配，返回最近生成的关联报告列表（最多 {@value #HISTORY_LIMIT} 条）。</p>
     *
     * @param id 调度ID
     * @return 关联报告 VO 列表
     */
    public List<ReportScheduleVO> getHistory(Long id) {
        ReportScheduleEntity entity = getScheduleEntityById(id);
        LambdaQueryWrapper<ReportEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ReportEntity::getReportName, entity.getReportName());
        wrapper.orderByDesc(ReportEntity::getCreateTime);
        wrapper.last("LIMIT " + HISTORY_LIMIT);
        List<ReportEntity> reports = reportMapper.selectList(wrapper);
        return reports.stream().map(re -> {
            ReportScheduleVO vo = toVO(entity);
            vo.setLastRunTime(re.getGeneratedAt());
            vo.setLastRunStatus("COMPLETED".equals(re.getStatus()) ? RUN_STATUS_SUCCESS : RUN_STATUS_FAILED);
            return vo;
        }).collect(Collectors.toList());
    }

    /**
     * 执行单次定时报告任务。
     *
     * <p>核心执行流程：</p>
     * <ol>
     *   <li>节假日/周末判断，非工作日直接跳过（调休补班日除外）</li>
     *   <li>查询调度配置，非 ACTIVE 直接跳过</li>
     *   <li>构建 {@link ReportGenerateDTO} 并调用 {@link ReportService#generateReport}</li>
     *   <li>轮询报告状态直到完成或超时</li>
     *   <li>根据 {@code webhookType} 分发推送：EMAIL / SLACK / DINGTALK / ALL</li>
     *   <li>更新 last_run_time 和 last_run_status</li>
     * </ol>
     *
     * <p>所有异常均被捕获，仅记录日志，不向上抛出，确保不中断调度器。</p>
     *
     * @param scheduleId 调度ID
     */
    public void executeSchedule(Long scheduleId) {
        String runStatus = RUN_STATUS_SUCCESS;
        boolean executed = false;
        try {
            // 节假日/周末跳过（调休补班日除外）
            if (!holidayCalendarService.shouldExecuteReport(LocalDate.now())) {
                log.info("当前日期为节假日或周末，跳过定时报告执行: scheduleId={}, date={}",
                        scheduleId, LocalDate.now());
                return;
            }
            ReportScheduleEntity schedule = scheduleMapper.selectById(scheduleId);
            if (schedule == null) {
                log.warn("定时报告配置不存在，跳过执行: scheduleId={}", scheduleId);
                return;
            }
            if (!STATUS_ACTIVE.equals(schedule.getStatus())) {
                log.info("定时报告非 ACTIVE 状态，跳过执行: scheduleId={}, status={}",
                        scheduleId, schedule.getStatus());
                return;
            }
            executed = true;

            ReportGenerateDTO dto = new ReportGenerateDTO();
            dto.setReportName(schedule.getReportName());
            dto.setReportType(schedule.getReportType());
            dto.setTargetId(schedule.getTargetId() == null ? null : String.valueOf(schedule.getTargetId()));
            dto.setTemplateId(schedule.getTemplateName());
            dto.setFormat(DEFAULT_FORMAT);

            ReportVO initial = reportService.generateReport(dto);
            String reportId = initial.getReportId();
            log.info("定时报告已触发生成: scheduleId={}, reportId={}", scheduleId, reportId);

            ReportVO completed = waitForCompletion(reportId);
            if (completed != null && "COMPLETED".equals(completed.getStatus())
                    && StrUtil.isNotBlank(completed.getFilePath())) {
                dispatchNotification(schedule, completed);
            } else {
                runStatus = RUN_STATUS_FAILED;
                log.warn("定时报告生成未完成或文件路径为空: scheduleId={}, reportId={}, status={}",
                        scheduleId, reportId, completed == null ? "null" : completed.getStatus());
            }
        } catch (Exception e) {
            runStatus = RUN_STATUS_FAILED;
            log.error("定时报告执行异常: scheduleId={}", scheduleId, e);
        } finally {
            if (executed) {
                updateRunStatus(scheduleId, runStatus);
            }
        }
    }

    /**
     * 轮询等待报告生成完成。
     *
     * @param reportId 报告ID
     * @return 完成状态的报告 VO，超时则返回最后一次查询结果
     */
    private ReportVO waitForCompletion(String reportId) {
        ReportVO last = null;
        for (int i = 0; i < maxPollAttempts; i++) {
            try {
                last = reportService.getReport(reportId);
                if (last == null) {
                    return null;
                }
                String status = last.getStatus();
                if ("COMPLETED".equals(status) || "FAILED".equals(status)) {
                    return last;
                }
                if (pollIntervalMs > 0) {
                    Thread.sleep(pollIntervalMs);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("轮询等待报告完成被中断: reportId={}", reportId);
                return last;
            } catch (Exception e) {
                log.warn("轮询报告状态异常: reportId={}, attempt={}", reportId, i, e);
            }
        }
        log.warn("等待报告完成超时: reportId={}, maxAttempts={}", reportId, maxPollAttempts);
        return last;
    }

    /**
     * 读取报告文件并以邮件附件形式发送。
     *
     * @param schedule 调度配置
     * @param report   完成的报告 VO
     */
    private void sendReportEmail(ReportScheduleEntity schedule, ReportVO report) {
        try {
            File file = new File(report.getFilePath());
            if (!file.exists()) {
                log.warn("报告文件不存在，跳过邮件发送: reportId={}, path={}",
                        report.getReportId(), report.getFilePath());
                return;
            }
            byte[] attachment = Files.readAllBytes(file.toPath());
            String subject = "定时报告: " + schedule.getReportName();
            String html = "<html><body><h1>" + schedule.getReportName()
                    + "</h1><p>报告已自动生成，请查看附件。</p>"
                    + "<p>报告ID: " + report.getReportId() + "</p>"
                    + "<p>生成时间: " + LocalDateTime.now() + "</p>"
                    + "</body></html>";
            String attachmentName = schedule.getReportName() + "_" + System.currentTimeMillis() + ".pdf";
            emailService.sendReport(schedule.getRecipients(), subject, html, attachment, attachmentName);
        } catch (Exception e) {
            log.error("发送定时报告邮件异常: scheduleId={}, reportId={}",
                    schedule.getId(), report.getReportId(), e);
        }
    }

    /**
     * 根据调度配置的 {@code webhookType} 分发推送通道。
     *
     * <p>支持 EMAIL / SLACK / DINGTALK / ALL 四种取值，未知值默认走 EMAIL。
     * 单个通道异常不影响其他通道。</p>
     *
     * @param schedule 调度配置
     * @param report   完成的报告 VO
     */
    private void dispatchNotification(ReportScheduleEntity schedule, ReportVO report) {
        String webhookType = schedule.getWebhookType();
        if (StrUtil.isBlank(webhookType)) {
            webhookType = WEBHOOK_EMAIL;
        }
        webhookType = webhookType.toUpperCase();
        switch (webhookType) {
            case WEBHOOK_EMAIL:
                sendReportEmail(schedule, report);
                break;
            case WEBHOOK_SLACK:
                sendSlackNotification(schedule, report);
                break;
            case WEBHOOK_DINGTALK:
                sendDingTalkNotification(schedule, report);
                break;
            case WEBHOOK_ALL:
                sendReportEmail(schedule, report);
                sendSlackNotification(schedule, report);
                sendDingTalkNotification(schedule, report);
                break;
            default:
                log.warn("未知推送通道，默认发送邮件: scheduleId={}, webhookType={}",
                        schedule.getId(), webhookType);
                sendReportEmail(schedule, report);
        }
    }

    /**
     * 发送 Slack Webhook 推送。
     *
     * @param schedule 调度配置
     * @param report   完成的报告 VO
     */
    private void sendSlackNotification(ReportScheduleEntity schedule, ReportVO report) {
        try {
            String reportUrl = buildReportDownloadUrl(report);
            String summary = StrUtil.isBlank(report.getSummary()) ? "报告已生成，详见下载链接" : report.getSummary();
            slackWebhookService.sendNotification(schedule.getReportName(), reportUrl, summary);
        } catch (Exception e) {
            log.error("发送 Slack 推送异常: scheduleId={}, reportId={}",
                    schedule.getId(), report.getReportId(), e);
        }
    }

    /**
     * 发送钉钉 Webhook 推送。
     *
     * @param schedule 调度配置
     * @param report   完成的报告 VO
     */
    private void sendDingTalkNotification(ReportScheduleEntity schedule, ReportVO report) {
        try {
            String reportUrl = buildReportDownloadUrl(report);
            String summary = StrUtil.isBlank(report.getSummary()) ? "报告已生成，详见下载链接" : report.getSummary();
            dingTalkWebhookService.sendNotification(schedule.getReportName(), reportUrl, summary);
        } catch (Exception e) {
            log.error("发送钉钉推送异常: scheduleId={}, reportId={}",
                    schedule.getId(), report.getReportId(), e);
        }
    }

    /**
     * 构造报告下载链接。
     *
     * @param report 报告 VO
     * @return 下载链接
     */
    private String buildReportDownloadUrl(ReportVO report) {
        String base = downloadBaseUrl == null ? "" : downloadBaseUrl;
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/" + report.getReportId();
    }

    /**
     * 更新调度的执行状态。
     *
     * @param scheduleId 调度ID
     * @param runStatus  执行状态
     */
    private void updateRunStatus(Long scheduleId, String runStatus) {
        try {
            ReportScheduleEntity update = new ReportScheduleEntity();
            update.setId(scheduleId);
            update.setLastRunTime(LocalDateTime.now());
            update.setLastRunStatus(runStatus);
            update.setUpdatedAt(LocalDateTime.now());
            scheduleMapper.updateById(update);
        } catch (Exception e) {
            log.error("更新调度执行状态失败: scheduleId={}, status={}", scheduleId, runStatus, e);
        }
    }

    /**
     * 校验 Cron 表达式是否合法。
     *
     * @param cronExpression cron 表达式
     */
    private void validateCronExpression(String cronExpression) {
        try {
            CronExpression.parse(cronExpression);
        } catch (IllegalArgumentException e) {
            throw BusinessException.of(ResultCode.PARAM_ERROR, "Cron 表达式不合法: " + cronExpression);
        }
    }

    /**
     * 按ID查询调度实体，不存在时抛出业务异常。
     *
     * @param id 调度ID
     * @return 调度实体
     */
    private ReportScheduleEntity getScheduleEntityById(Long id) {
        ReportScheduleEntity entity = scheduleMapper.selectById(id);
        if (entity == null) {
            throw BusinessException.of(ResultCode.NOT_FOUND, "定时报告配置不存在: " + id);
        }
        return entity;
    }

    /**
     * 实体转 VO。
     *
     * @param entity 调度实体
     * @return 调度 VO
     */
    private ReportScheduleVO toVO(ReportScheduleEntity entity) {
        ReportScheduleVO vo = new ReportScheduleVO();
        vo.setId(entity.getId());
        vo.setReportName(entity.getReportName());
        vo.setReportType(entity.getReportType());
        vo.setCronExpression(entity.getCronExpression());
        vo.setRecipients(entity.getRecipients());
        vo.setTemplateName(entity.getTemplateName());
        vo.setTargetId(entity.getTargetId());
        vo.setStatus(entity.getStatus());
        vo.setLastRunTime(entity.getLastRunTime());
        vo.setLastRunStatus(entity.getLastRunStatus());
        vo.setWebhookType(entity.getWebhookType());
        vo.setCreatedBy(entity.getCreatedBy());
        vo.setCreatedAt(entity.getCreatedAt());
        vo.setUpdatedAt(entity.getUpdatedAt());
        return vo;
    }
}
