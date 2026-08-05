package com.redteam.common.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 审计日志实体类
 *
 * <p>对应数据库表 {@code audit_log}，记录用户在平台上的操作行为，
 * 包括查看、上传、下载、打标、删除、检索、导出、登录等操作。</p>
 *
 * <p>审计日志由 {@link com.redteam.common.annotation.AuditLog} 注解 + AOP 切面
 * 自动采集，也可通过 {@link com.redteam.common.service.AuditLogService} 手动记录。</p>
 *
 * @author 红方团队
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("audit_log")
public class AuditLogEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID（数据库自增）
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 操作用户ID
     */
    private Long userId;

    /**
     * 用户名
     */
    private String username;

    /**
     * 操作类型：VIEW/UPLOAD/DOWNLOAD/TAG/DELETE/SEARCH/EXPORT/LOGIN
     */
    private String action;

    /**
     * 资源类型：FILE/TAG/REPORT/TASK/USER/CONFIG
     */
    private String resourceType;

    /**
     * 资源ID
     */
    private String resourceId;

    /**
     * 资源名称
     */
    private String resourceName;

    /**
     * 客户端IP
     */
    private String ipAddress;

    /**
     * User-Agent
     */
    private String userAgent;

    /**
     * 操作详情JSON
     */
    private String detail;

    /**
     * 操作结果：SUCCESS/FAILED
     */
    private String status;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
}
