package com.redteam.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 审计日志注解
 *
 * <p>标记在 Controller 方法上，由 {@link com.redteam.common.aspect.AuditLogAspect} 切面拦截，
 * 自动采集操作用户、IP、参数等信息并写入 {@code audit_log} 表。</p>
 *
 * <p>使用示例：</p>
 * <pre>
 * {@literal @}AuditLog(action = "SEARCH", resourceType = "FILE")
 * public Result&lt;SearchResultVO&gt; search(@RequestBody SearchRequestDTO request) { ... }
 *
 * {@literal @}AuditLog(action = "TAG", resourceType = "FILE", resourceIdParam = "fileId")
 * public Result&lt;Void&gt; addFileTags(@PathVariable Long fileId, @RequestBody List&lt;Long&gt; tagIds) { ... }
 * </pre>
 *
 * @author 红方团队
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditLog {

    /**
     * 操作类型：VIEW/UPLOAD/DOWNLOAD/TAG/DELETE/SEARCH/EXPORT/LOGIN
     *
     * @return 操作类型
     */
    String action();

    /**
     * 资源类型：FILE/TAG/REPORT/TASK/USER/CONFIG
     *
     * @return 资源类型
     */
    String resourceType();

    /**
     * resourceId 参数名（从方法参数中提取资源ID）
     *
     * @return 参数名，默认 "id"
     */
    String resourceIdParam() default "id";

    /**
     * resourceName 参数名（可 SpEL，如 "#result.name" 或 "#dto.fileName"）
     *
     * @return 参数名或 SpEL 表达式，默认空字符串表示不记录
     */
    String resourceNameParam() default "";

    /**
     * 操作描述
     *
     * @return 描述信息
     */
    String description() default "";
}
