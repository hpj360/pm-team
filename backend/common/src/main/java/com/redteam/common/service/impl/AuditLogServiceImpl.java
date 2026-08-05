package com.redteam.common.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.redteam.common.config.AuditLogAsyncConfig;
import com.redteam.common.entity.AuditLogEntity;
import com.redteam.common.mapper.AuditLogMapper;
import com.redteam.common.result.PageResult;
import com.redteam.common.service.AuditLogService;
import com.redteam.common.util.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.StringWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * 审计日志服务实现
 *
 * <p>基于 {@link AuditLogMapper} 实现审计日志的记录、查询、CSV 导出与统计。
 * 记录操作通过独立线程池异步执行，避免阻塞业务主线程。</p>
 *
 * @author 红方团队
 */
@Slf4j
@Service
public class AuditLogServiceImpl implements AuditLogService {

    /**
     * 未知 IP 标识
     */
    private static final String UNKNOWN = "unknown";

    /**
     * 操作成功状态
     */
    private static final String STATUS_SUCCESS = "SUCCESS";

    /**
     * CSV 日期格式
     */
    private static final DateTimeFormatter CSV_DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * CSV 表头
     */
    private static final String[] CSV_HEADERS = {
            "ID", "用户ID", "用户名", "操作类型", "资源类型",
            "资源ID", "资源名称", "IP地址", "状态", "创建时间"
    };

    private final AuditLogMapper auditLogMapper;

    /**
     * 审计日志异步执行器（用于异步写入）
     */
    private final Executor auditLogExecutor;

    public AuditLogServiceImpl(AuditLogMapper auditLogMapper,
                                @Qualifier(AuditLogAsyncConfig.AUDIT_LOG_EXECUTOR) Executor auditLogExecutor) {
        this.auditLogMapper = auditLogMapper;
        this.auditLogExecutor = auditLogExecutor;
    }

    /**
     * 手动记录审计日志（异步写入）
     */
    @Override
    public void record(String action, String resourceType, String resourceId,
                       String resourceName, String detail) {
        try {
            AuditLogEntity entity = new AuditLogEntity();
            entity.setUserId(UserContext.getUserId());
            entity.setUsername(UserContext.getUsername());
            entity.setAction(action);
            entity.setResourceType(resourceType);
            entity.setResourceId(resourceId);
            entity.setResourceName(resourceName);
            entity.setDetail(detail);
            entity.setStatus(STATUS_SUCCESS);
            entity.setCreatedAt(LocalDateTime.now());

            // 采集请求上下文（IP / User-Agent）
            HttpServletRequest request = getCurrentRequest();
            if (request != null) {
                entity.setIpAddress(getIpAddress(request));
                String userAgent = request.getHeader("User-Agent");
                if (userAgent != null && userAgent.length() > 500) {
                    userAgent = userAgent.substring(0, 500);
                }
                entity.setUserAgent(userAgent);
            }

            asyncInsert(entity);
        } catch (Throwable e) {
            log.error("手动记录审计日志失败: action={}, resourceType={}", action, resourceType, e);
        }
    }

    /**
     * 查询审计日志（筛选 + 分页）
     */
    @Override
    public PageResult<AuditLogEntity> query(Long userId, String action, String resourceType,
                                             LocalDateTime startTime, LocalDateTime endTime,
                                             int page, int size) {
        // 参数兜底
        long pageNo = Math.max(page, 1);
        long pageSize = size <= 0 ? 20L : size;

        Page<AuditLogEntity> pageParam = new Page<>(pageNo, pageSize);
        IPage<AuditLogEntity> result = auditLogMapper.selectByConditionsPage(
                pageParam, userId, action, resourceType, startTime, endTime);

        return PageResult.of(result.getCurrent(), result.getSize(),
                result.getTotal(), result.getRecords());
    }

    /**
     * 导出审计日志 CSV
     */
    @Override
    public String exportCsv(Long userId, String action, String resourceType,
                            LocalDateTime startTime, LocalDateTime endTime) {
        List<AuditLogEntity> list = auditLogMapper.selectByConditions(
                userId, action, resourceType, startTime, endTime);

        StringWriter writer = new StringWriter();
        // 写表头
        writer.append(String.join(",", CSV_HEADERS)).append("\n");

        // 写数据行
        if (list != null) {
            for (AuditLogEntity entity : list) {
                writer.append(toCsvRow(entity)).append("\n");
            }
        }
        return writer.toString();
    }

    /**
     * 审计统计（按操作类型分组）
     */
    @Override
    public Map<String, Long> stats(LocalDateTime startTime, LocalDateTime endTime) {
        List<Map<String, Object>> rows = auditLogMapper.countGroupByAction(startTime, endTime);
        Map<String, Long> result = new LinkedHashMap<>();
        if (rows != null) {
            for (Map<String, Object> row : rows) {
                Object actionObj = row.get("action");
                Object cntObj = row.get("cnt");
                String actionName = actionObj == null ? "UNKNOWN" : String.valueOf(actionObj);
                Long count = cntObj == null ? 0L : toLong(cntObj);
                result.put(actionName, count);
            }
        }
        return result;
    }

    // ==================== 私有方法 ====================

    /**
     * 异步插入审计日志（捕获所有异常）
     *
     * @param entity 审计日志实体
     */
    private void asyncInsert(AuditLogEntity entity) {
        try {
            java.util.concurrent.CompletableFuture.runAsync(() -> {
                try {
                    auditLogMapper.insert(entity);
                } catch (Throwable e) {
                    log.error("异步写入审计日志失败: action={}, userId={}",
                            entity.getAction(), entity.getUserId(), e);
                }
            }, auditLogExecutor);
        } catch (Throwable e) {
            log.error("提交审计日志异步任务失败", e);
        }
    }

    /**
     * 实体转 CSV 行（字段以逗号分隔，值用双引号包裹并转义内部双引号）
     *
     * @param entity 审计日志实体
     * @return CSV 行字符串
     */
    private String toCsvRow(AuditLogEntity entity) {
        StringBuilder sb = new StringBuilder();
        sb.append(safe(entity.getId())).append(",");
        sb.append(quote(safe(entity.getUserId()))).append(",");
        sb.append(quote(safe(entity.getUsername()))).append(",");
        sb.append(quote(safe(entity.getAction()))).append(",");
        sb.append(quote(safe(entity.getResourceType()))).append(",");
        sb.append(quote(safe(entity.getResourceId()))).append(",");
        sb.append(quote(safe(entity.getResourceName()))).append(",");
        sb.append(quote(safe(entity.getIpAddress()))).append(",");
        sb.append(quote(safe(entity.getStatus()))).append(",");
        sb.append(quote(entity.getCreatedAt() == null ? "" : CSV_DATE_FORMAT.format(entity.getCreatedAt())));
        return sb.toString();
    }

    /**
     * CSV 字段值转义：双引号包裹，内部双引号转义为两个双引号
     *
     * @param value 原始值
     * @return 转义后的 CSV 字段
     */
    private String quote(String value) {
        if (value == null) {
            return "\"\"";
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    /**
     * 对象转字符串（null 返回空串）
     *
     * @param obj 对象
     * @return 字符串
     */
    private String safe(Object obj) {
        return obj == null ? "" : String.valueOf(obj);
    }

    /**
     * 将 Number/Object 安全转为 Long
     *
     * @param value 值
     * @return Long
     */
    private Long toLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    /**
     * 获取当前 HTTP 请求
     *
     * @return HttpServletRequest，非 HTTP 上下文时返回 null
     */
    private HttpServletRequest getCurrentRequest() {
        try {
            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                return attributes.getRequest();
            }
        } catch (Throwable e) {
            log.debug("获取当前 HTTP 请求失败", e);
        }
        return null;
    }

    /**
     * 获取客户端 IP 地址
     *
     * <p>优先级：X-Forwarded-For &gt; X-Real-IP &gt; Proxy-Client-IP &gt; RemoteAddr。</p>
     *
     * @param request HTTP 请求
     * @return 客户端 IP
     */
    private String getIpAddress(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (StrUtil.isBlank(ip) || UNKNOWN.equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (StrUtil.isBlank(ip) || UNKNOWN.equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (StrUtil.isBlank(ip) || UNKNOWN.equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
