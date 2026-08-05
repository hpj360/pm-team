package com.redteam.common.aspect;

import cn.hutool.core.util.StrUtil;
import com.redteam.common.annotation.RateLimit;
import com.redteam.common.exception.BusinessException;
import com.redteam.common.result.ResultCode;
import com.redteam.common.util.RateLimiter;
import com.redteam.common.util.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;

/**
 * 限流切面
 *
 * <p>拦截标注了 {@link RateLimit} 注解的方法，根据限流维度（USER / IP / GLOBAL）
 * 构建限流 key，调用 {@link RateLimiter} 进行令牌桶限流。</p>
 *
 * <p>限流 key 规则：
 * <ul>
 *   <li>USER: rate_limit:user:{userId}:{ClassName.methodName}</li>
 *   <li>IP: rate_limit:ip:{ip}:{ClassName.methodName}</li>
 *   <li>GLOBAL: rate_limit:global:{ClassName.methodName}</li>
 * </ul>
 *
 * @author 红方团队
 */
@Aspect
@Component
@Slf4j
public class RateLimitAspect {

    @Autowired
    private RateLimiter rateLimiter;

    /**
     * SpEL 表达式解析器（用于解析注解中的自定义 key）
     */
    private static final SpelExpressionParser PARSER = new SpelExpressionParser();

    /**
     * 环绕通知：执行限流检查
     *
     * @param joinPoint  连接点
     * @param rateLimit  限流注解
     * @return 方法返回值
     * @throws Throwable 方法执行异常或限流异常
     */
    @Around("@annotation(rateLimit)")
    public Object around(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {
        // 1. 构建限流 key
        String key = buildKey(joinPoint, rateLimit);
        // 2. 尝试获取令牌
        boolean allowed = rateLimiter.tryAcquire(key, rateLimit.qps(), rateLimit.window());
        // 3. 被限流时抛出业务异常
        if (!allowed) {
            log.warn("接口被限流: key={}, qps={}, window={}", key, rateLimit.qps(), rateLimit.window());
            throw new BusinessException(ResultCode.RATE_LIMIT_EXCEEDED, rateLimit.message());
        }
        // 4. 放行
        return joinPoint.proceed();
    }

    /**
     * 构建限流 key
     *
     * <p>优先使用注解中指定的 key（支持 SpEL），否则按 limitType 维度构建。</p>
     *
     * @param joinPoint  连接点
     * @param rateLimit  限流注解
     * @return 限流 key
     */
    String buildKey(ProceedingJoinPoint joinPoint, RateLimit rateLimit) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        String methodName = method.getDeclaringClass().getSimpleName() + "." + method.getName();

        // 如果指定了 key，使用 SpEL 解析
        if (StrUtil.isNotBlank(rateLimit.key())) {
            try {
                EvaluationContext context = new MethodBasedEvaluationContext(
                        null, method, joinPoint.getArgs(), new DefaultParameterNameDiscoverer());
                String customKey = PARSER.parseExpression(rateLimit.key()).getValue(context, String.class);
                return "rate_limit:custom:" + (customKey != null ? customKey : rateLimit.key());
            } catch (Exception e) {
                log.warn("SpEL 解析 key 失败，使用原始值: {}", rateLimit.key(), e);
                return "rate_limit:custom:" + rateLimit.key();
            }
        }

        // 按 limitType 构建 key
        String limitType = rateLimit.limitType();
        switch (limitType) {
            case "USER":
                Long userId = UserContext.getUserId();
                String userKey = userId != null ? userId.toString() : "anonymous";
                return "rate_limit:user:" + userKey + ":" + methodName;
            case "IP":
                String ip = getClientIp();
                return "rate_limit:ip:" + ip + ":" + methodName;
            case "GLOBAL":
                return "rate_limit:global:" + methodName;
            default:
                return "rate_limit:global:" + methodName;
        }
    }

    /**
     * 获取客户端 IP 地址
     *
     * <p>支持多级代理场景（X-Forwarded-For 等请求头），取第一个非 unknown 的 IP。</p>
     *
     * @return 客户端 IP，获取失败返回 "unknown"
     */
    private String getClientIp() {
        try {
            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes == null) {
                return "unknown";
            }
            HttpServletRequest request = attributes.getRequest();
            String ip = request.getHeader("X-Forwarded-For");
            if (StrUtil.isBlank(ip) || "unknown".equalsIgnoreCase(ip)) {
                ip = request.getHeader("Proxy-Client-IP");
            }
            if (StrUtil.isBlank(ip) || "unknown".equalsIgnoreCase(ip)) {
                ip = request.getHeader("WL-Proxy-Client-IP");
            }
            if (StrUtil.isBlank(ip) || "unknown".equalsIgnoreCase(ip)) {
                ip = request.getRemoteAddr();
            }
            // 多层代理时取第一个
            if (ip != null && ip.contains(",")) {
                ip = ip.split(",")[0].trim();
            }
            return StrUtil.isBlank(ip) ? "unknown" : ip;
        } catch (Exception e) {
            return "unknown";
        }
    }
}
