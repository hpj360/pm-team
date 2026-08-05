package com.redteam.common.service;

import com.redteam.common.entity.AuditLogEntity;
import com.redteam.common.result.PageResult;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 审计日志服务接口
 *
 * <p>提供审计日志的手动记录、查询、CSV 导出与统计能力。
 * 与 {@link com.redteam.common.annotation.AuditLog} 注解 + AOP 切面配合，
 * 覆盖「自动采集」与「手动记录」两种审计场景。</p>
 *
 * @author 红方团队
 */
public interface AuditLogService {

    /**
     * 手动记录审计日志
     *
     * <p>异步写入，不阻塞调用线程。用户/IP 信息从 {@link com.redteam.common.util.UserContext}
     * 与当前 HTTP 请求上下文中获取。</p>
     *
     * @param action       操作类型：VIEW/UPLOAD/DOWNLOAD/TAG/DELETE/SEARCH/EXPORT/LOGIN
     * @param resourceType 资源类型：FILE/TAG/REPORT/TASK/USER/CONFIG
     * @param resourceId   资源ID（可空）
     * @param resourceName 资源名称（可空）
     * @param detail       操作详情JSON（可空）
     */
    void record(String action, String resourceType, String resourceId,
                String resourceName, String detail);

    /**
     * 查询审计日志（筛选 + 分页）
     *
     * @param userId       用户ID（可空）
     * @param action       操作类型（可空）
     * @param resourceType 资源类型（可空）
     * @param startTime    开始时间（可空）
     * @param endTime      结束时间（可空）
     * @param page         页码（从 1 开始）
     * @param size         每页大小
     * @return 分页审计日志
     */
    PageResult<AuditLogEntity> query(Long userId, String action, String resourceType,
                                      LocalDateTime startTime, LocalDateTime endTime,
                                      int page, int size);

    /**
     * 导出审计日志 CSV
     *
     * <p>按筛选条件查询全部匹配记录（不分页），拼接为 CSV 字符串。</p>
     *
     * @param userId       用户ID（可空）
     * @param action       操作类型（可空）
     * @param resourceType 资源类型（可空）
     * @param startTime    开始时间（可空）
     * @param endTime      结束时间（可空）
     * @return CSV 字符串
     */
    String exportCsv(Long userId, String action, String resourceType,
                     LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 审计统计（按操作类型分组）
     *
     * @param startTime 开始时间（可空）
     * @param endTime   结束时间（可空）
     * @return 操作类型 -> 数量 映射
     */
    Map<String, Long> stats(LocalDateTime startTime, LocalDateTime endTime);
}
