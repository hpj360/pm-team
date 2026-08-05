package com.redteam.common.aspect;

import cn.hutool.core.util.StrUtil;
import com.redteam.common.annotation.AuditLog;
import com.redteam.common.config.AuditLogAsyncConfig;
import com.redteam.common.entity.AuditLogEntity;
import com.redteam.common.mapper.AuditLogMapper;
import com.redteam.common.util.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * 审计日志切面
 *
 * <p>拦截标注 {@link AuditLog} 注解的 Controller 方法，在方法返回后或抛出异常后
 * 异步采集操作上下文（用户、IP、参数等）并写入 {@code audit_log} 表。</p>
 *
 * <p>设计约束：</p>
 * <ul>
 *   <li>切面不抛出异常，审计失败仅记录日志，不影响业务流程；</li>
 *   <li>使用独立线程池异步写入，避免阻塞主线程；</li>
 *   <li>IP 获取支持 X-Forwarded-For / X-Real-IP 等代理转发头。</li>
 * </ul>
 *
 * @author 红方团队
 */
@Slf4j
@Aspect
@Component
public class AuditLogAspect {

    /**
     * 操作成功状态
     */
    private static final String STATUS_SUCCESS = "SUCCESS";

    /**
     * 操作失败状态
     */
    private static final String STATUS_FAILED = "FAILED";

    /**
     * 未知 IP 标识
     */
    private static final String UNKNOWN = "unknown";

    /**
     * 参数名发现器（基于反射 + 调试信息获取方法参数名）
     */
    private final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

    /**
     * SpEL 表达式解析器
     */
    private final ExpressionParser spelParser = new SpelExpressionParser();

    @Autowired
    private AuditLogMapper auditLogMapper;

    @Autowired
    @Qualifier(AuditLogAsyncConfig.AUDIT_LOG_EXECUTOR)
    private Executor auditLogExecutor;

    /**
     * 方法正常返回后记录审计日志
     *
     * @param joinPoint 连接点
     * @param auditLog  注解
     * @param result    返回值
     */
    @AfterReturning(pointcut = "@annotation(auditLog)", returning = "result")
    public void afterReturning(JoinPoint joinPoint, AuditLog auditLog, Object result) {
        recordAuditLog(joinPoint, auditLog, STATUS_SUCCESS, null, result);
    }

    /**
     * 方法抛出异常后记录审计日志
     *
     * @param joinPoint 连接点
     * @param auditLog  注解
     * @param ex        异常
     */
    @AfterThrowing(pointcut = "@annotation(auditLog)", throwing = "ex")
    public void afterThrowing(JoinPoint joinPoint, AuditLog auditLog, Exception ex) {
        recordAuditLog(joinPoint, auditLog, STATUS_FAILED,
                ex == null ? null : ex.getMessage(), null);
    }

    /**
     * 记录审计日志（构建实体 + 异步写入）
     *
     * <p>所有异常均被捕获并记录日志，不向调用方抛出。</p>
     *
     * @param joinPoint 连接点
     * @param auditLog  注解
     * @param status    操作结果状态
     * @param errorMsg  错误消息（成功时为 null）
     * @param result    方法返回值（失败时为 null）
     */
    private void recordAuditLog(JoinPoint joinPoint, AuditLog auditLog, String status,
                                String errorMsg, Object result) {
        try {
            // 1. 构建审计日志实体
            AuditLogEntity entity = buildAuditLogEntity(joinPoint, auditLog, status, errorMsg, result);
            // 2. 异步写入数据库
            CompletableFutureInsert(entity);
        } catch (Throwable e) {
            // 审计日志失败不影响业务
            log.error("记录审计日志失败: action={}, resourceType={}",
                    auditLog.action(), auditLog.resourceType(), e);
        }
    }

    /**
     * 构建审计日志实体
     *
     * @param joinPoint 连接点
     * @param auditLog  注解
     * @param status    状态
     * @param errorMsg  错误消息
     * @param result    返回值
     * @return 审计日志实体
     */
    private AuditLogEntity buildAuditLogEntity(JoinPoint joinPoint, AuditLog auditLog,
                                               String status, String errorMsg, Object result) {
        AuditLogEntity entity = new AuditLogEntity();
        // 用户信息
        entity.setUserId(UserContext.getUserId());
        entity.setUsername(UserContext.getUsername());
        // 操作信息
        entity.setAction(auditLog.action());
        entity.setResourceType(auditLog.resourceType());
        entity.setStatus(status);
        entity.setCreatedAt(LocalDateTime.now());

        // 请求信息（IP / User-Agent）
        HttpServletRequest request = getCurrentRequest();
        if (request != null) {
            entity.setIpAddress(getIpAddress(request));
            String userAgent = request.getHeader("User-Agent");
            if (userAgent != null && userAgent.length() > 500) {
                userAgent = userAgent.substring(0, 500);
            }
            entity.setUserAgent(userAgent);
        }

        // 从方法参数提取 resourceId / resourceName
        extractResourceInfo(joinPoint, auditLog, result, entity);

        // 详情（包含描述与错误信息）
        entity.setDetail(buildDetail(auditLog, errorMsg));

        return entity;
    }

    /**
     * 异步插入审计日志（捕获所有异常）
     *
     * @param entity 审计日志实体
     */
    private void CompletableFutureInsert(AuditLogEntity entity) {
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
     * 从方法参数提取 resourceId / resourceName
     *
     * <p>参数名发现策略（优先级从高到低）：</p>
     * <ol>
     *   <li>{@code @PathVariable("name")} / {@code @RequestParam("name")} 注解的 value；</li>
     *   <li>Java 反射参数名（需 -parameters 编译选项）；</li>
     *   <li>Spring ASM 本地变量表参数名发现器。</li>
     * </ol>
     *
     * @param joinPoint 连接点
     * @param auditLog  注解
     * @param result    返回值
     * @param entity    审计日志实体（写入 resourceId / resourceName）
     */
    private void extractResourceInfo(JoinPoint joinPoint, AuditLog auditLog, Object result,
                                     AuditLogEntity entity) {
        try {
            MethodSignature methodSignature = (MethodSignature) joinPoint.getSignature();
            Method method = methodSignature.getMethod();
            Object[] args = joinPoint.getArgs();

            // 构建参数名 -> 参数值 的映射（兼容注解与反射两种发现方式）
            Map<String, Object> paramMap = buildParamMap(method, args);

            // 提取 resourceId
            String resourceId = getValueFromMap(auditLog.resourceIdParam(), paramMap);
            if (resourceId != null) {
                entity.setResourceId(resourceId);
            }

            // 提取 resourceName（支持 SpEL）
            String resourceNameParam = auditLog.resourceNameParam();
            if (StrUtil.isNotBlank(resourceNameParam)) {
                String resourceName;
                if (resourceNameParam.startsWith("#")) {
                    resourceName = evaluateSpel(resourceNameParam, paramMap, result);
                } else {
                    resourceName = getValueFromMap(resourceNameParam, paramMap);
                }
                if (resourceName != null) {
                    entity.setResourceName(resourceName);
                }
            }
        } catch (Throwable e) {
            log.warn("提取审计日志资源信息失败: action={}", auditLog.action(), e);
        }
    }

    /**
     * 构建参数名 -> 参数值 的映射
     *
     * <p>依次尝试：注解（@PathVariable/@RequestParam）值、反射参数名、ASM 参数名。</p>
     *
     * @param method 方法
     * @param args   参数值数组
     * @return 参数名 -> 参数值 映射
     */
    private Map<String, Object> buildParamMap(Method method, Object[] args) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (method == null || args == null) {
            return map;
        }
        Parameter[] parameters = method.getParameters();
        String[] paramNames = parameterNameDiscoverer.getParameterNames(method);

        for (int i = 0; i < parameters.length && i < args.length; i++) {
            Object value = args[i];

            // 1. 从 @PathVariable 注解提取参数名
            PathVariable pathVariable = parameters[i].getAnnotation(PathVariable.class);
            if (pathVariable != null) {
                String name = StrUtil.isBlank(pathVariable.value()) ? pathVariable.name() : pathVariable.value();
                if (StrUtil.isNotBlank(name)) {
                    map.putIfAbsent(name, value);
                }
            }

            // 2. 从 @RequestParam 注解提取参数名
            RequestParam requestParam = parameters[i].getAnnotation(RequestParam.class);
            if (requestParam != null) {
                String name = StrUtil.isBlank(requestParam.value()) ? requestParam.name() : requestParam.value();
                if (StrUtil.isNotBlank(name)) {
                    map.putIfAbsent(name, value);
                }
            }

            // 3. 从反射参数名提取（需 -parameters 编译选项）
            if (parameters[i].isNamePresent()) {
                map.putIfAbsent(parameters[i].getName(), value);
            }

            // 4. 从 ASM 参数名发现器提取
            if (paramNames != null && i < paramNames.length && paramNames[i] != null) {
                map.putIfAbsent(paramNames[i], value);
            }
        }
        return map;
    }

    /**
     * 从参数映射中按名提取值（转为字符串）
     *
     * @param paramName 参数名
     * @param paramMap  参数名 -> 参数值 映射
     * @return 参数值字符串，找不到时返回 null
     */
    private String getValueFromMap(String paramName, Map<String, Object> paramMap) {
        if (StrUtil.isBlank(paramName) || paramMap == null) {
            return null;
        }
        Object value = paramMap.get(paramName);
        return value == null ? null : String.valueOf(value);
    }

    /**
     * 计算 SpEL 表达式
     *
     * <p>上下文变量：方法参数（按参数名绑定）、{@code #result}（返回值）。</p>
     *
     * @param expression SpEL 表达式
     * @param paramMap   参数名 -> 参数值 映射
     * @param result     返回值
     * @return 计算结果字符串，异常时返回 null
     */
    private String evaluateSpel(String expression, Map<String, Object> paramMap, Object result) {
        try {
            EvaluationContext context = new StandardEvaluationContext();
            // 绑定返回值
            context.setVariable("result", result);
            // 绑定方法参数
            if (paramMap != null) {
                for (Map.Entry<String, Object> entry : paramMap.entrySet()) {
                    context.setVariable(entry.getKey(), entry.getValue());
                }
            }
            Expression exp = spelParser.parseExpression(expression);
            Object value = exp.getValue(context);
            return value == null ? null : String.valueOf(value);
        } catch (Throwable e) {
            log.warn("解析 SpEL 表达式失败: expr={}", expression, e);
            return null;
        }
    }

    /**
     * 构建操作详情 JSON
     *
     * @param auditLog 注解
     * @param errorMsg 错误消息
     * @return 详情 JSON 字符串
     */
    private String buildDetail(AuditLog auditLog, String errorMsg) {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"description\":\"").append(escapeJson(auditLog.description())).append("\"");
        if (StrUtil.isNotBlank(errorMsg)) {
            sb.append(",\"error\":\"").append(escapeJson(errorMsg)).append("\"");
        }
        sb.append("}");
        return sb.toString();
    }

    /**
     * 简单转义 JSON 字符串中的特殊字符
     *
     * @param value 原始字符串
     * @return 转义后的字符串
     */
    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
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
     * <p>优先级：X-Forwarded-For &gt; X-Real-IP &gt; Proxy-Client-IP &gt; RemoteAddr。
     * X-Forwarded-For 可能包含多个 IP，取第一个（最原始客户端）。</p>
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
        // X-Forwarded-For 可能含多个 IP，取第一个
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
