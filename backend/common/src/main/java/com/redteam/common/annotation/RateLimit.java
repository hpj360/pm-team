package com.redteam.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * API 限流注解
 *
 * <p>基于 Redis 令牌桶（固定窗口计数）实现，通过 AOP 切面 {@code RateLimitAspect} 拦截标注了
 * 本注解的方法。支持按用户、IP、全局三种维度限流，支持 SpEL 表达式自定义 key。</p>
 *
 * <pre>
 * 示例：
 * {@code @RateLimit(qps = 10, limitType = "USER")}
 * public Result<?> search(SearchRequestDTO request) { ... }
 * </pre>
 *
 * @author 红方团队
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    /**
     * 限流 key（支持 SpEL），默认按用户 + 方法名
     */
    String key() default "";

    /**
     * 每秒允许的请求数（QPS）
     */
    int qps() default 10;

    /**
     * 限流窗口（秒），默认 1 秒
     */
    int window() default 1;

    /**
     * 限流维度：USER / IP / GLOBAL
     */
    String limitType() default "USER";

    /**
     * 提示消息
     */
    String message() default "请求过于频繁，请稍后重试";
}
