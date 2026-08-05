package com.redteam.common.telemetry;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 统一 JSON 日志配置 (v5.4)
 *
 * <p>配合 {@code logback-spring.xml} 中的 {@code LogstashEncoder} 输出结构化 JSON 日志，
 * 字段约定见 {@link LogFieldConstants}（timestamp/level/traceId/service/msg/fields）。</p>
 *
 * <p>本类负责在每条请求的 MDC 中写入 {@code service} 字段，使 JSON 日志携带服务名；
 * {@code traceId}/{@code spanId} 由 {@link TraceContextPropagator} 写入。
 * 静态方法 {@link #applyServiceMdc(String)} / {@link #clearServiceMdc()} 供非 HTTP 线程
 * （如定时任务、Kafka 消费）主动同步服务名到 MDC。</p>
 *
 * @author 红方团队
 */
@Slf4j
@Configuration
@ConditionalOnProperty(prefix = "redteam.telemetry", name = "enabled", havingValue = "true", matchIfMissing = true)
public class UnifiedLogConfig {

    @Value("${redteam.telemetry.service-name:${spring.application.name:redteam-service}}")
    private String serviceName;

    /**
     * 服务名 MDC 过滤器：每条请求写入 service 字段，结束后清理
     *
     * @return OncePerRequestFilter
     */
    @Bean
    public OncePerRequestFilter unifiedLogMdcFilter() {
        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request,
                                            HttpServletResponse response,
                                            FilterChain filterChain) {
                applyServiceMdc(serviceName);
                try {
                    filterChain.doFilter(request, response);
                } catch (Throwable e) {
                    throw new RuntimeException(e);
                } finally {
                    clearServiceMdc();
                }
            }
        };
    }

    /**
     * 把服务名写入 MDC（供非 HTTP 线程主动调用）
     *
     * @param service 服务名
     */
    public static void applyServiceMdc(String service) {
        if (service != null) {
            MDC.put(LogFieldConstants.MDC_SERVICE, service);
        }
    }

    /**
     * 清除 MDC 中的服务名
     */
    public static void clearServiceMdc() {
        MDC.remove(LogFieldConstants.MDC_SERVICE);
    }

    /**
     * 获取配置的服务名（供测试）
     *
     * @return 服务名
     */
    public String getServiceName() {
        return serviceName;
    }
}
