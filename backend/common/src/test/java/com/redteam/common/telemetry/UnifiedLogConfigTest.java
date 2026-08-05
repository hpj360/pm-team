package com.redteam.common.telemetry;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * UnifiedLogConfig 单元测试 (v5.4)
 *
 * <p>覆盖场景：</p>
 * <ol>
 *   <li>{@code applyServiceMdc} 把服务名写入 MDC</li>
 *   <li>{@code applyServiceMdc(null)} 不写入、不抛异常</li>
 *   <li>{@code clearServiceMdc} 清除 MDC 中的 service 字段</li>
 *   <li>{@code getServiceName} 返回配置的服务名</li>
 *   <li>{@code unifiedLogMdcFilter} 请求期间 MDC 含 service，结束后被清理</li>
 *   <li>Filter 链路异常时 finally 仍清理 MDC（异常被包装为 RuntimeException 抛出）</li>
 * </ol>
 *
 * @author 红方团队
 */
class UnifiedLogConfigTest {

    private UnifiedLogConfig config;

    @BeforeEach
    void setUp() {
        config = new UnifiedLogConfig();
        ReflectionTestUtils.setField(config, "serviceName", "parse-service");
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    @DisplayName("testApplyServiceMdc: 把服务名写入 MDC")
    void testApplyServiceMdc() {
        UnifiedLogConfig.applyServiceMdc("analyze-service");
        assertEquals("analyze-service", MDC.get(LogFieldConstants.MDC_SERVICE),
                "MDC service 应为 analyze-service");
    }

    @Test
    @DisplayName("testApplyServiceMdc_Null: null 服务名不写入、不抛异常")
    void testApplyServiceMdc_Null() {
        MDC.put(LogFieldConstants.MDC_SERVICE, "pre-existing");
        assertDoesNotThrow(() -> UnifiedLogConfig.applyServiceMdc(null));
        assertEquals("pre-existing", MDC.get(LogFieldConstants.MDC_SERVICE),
                "传入 null 时不应覆盖既有 service 值");
    }

    @Test
    @DisplayName("testClearServiceMdc: 清除 MDC 中的 service 字段")
    void testClearServiceMdc() {
        MDC.put(LogFieldConstants.MDC_SERVICE, "some-service");
        UnifiedLogConfig.clearServiceMdc();
        assertNull(MDC.get(LogFieldConstants.MDC_SERVICE), "clearServiceMdc 后 service 应为 null");
    }

    @Test
    @DisplayName("testGetServiceName: 返回配置的服务名")
    void testGetServiceName() {
        assertEquals("parse-service", config.getServiceName(), "应返回反射注入的服务名");
    }

    @Test
    @DisplayName("testUnifiedLogMdcFilter_RequestScope: 请求期间 MDC 含 service，结束后清理")
    void testUnifiedLogMdcFilter_RequestScope() throws Exception {
        OncePerRequestFilter filter = config.unifiedLogMdcFilter();
        HttpServletRequest request = new MockHttpServletRequest();
        HttpServletResponse response = new MockHttpServletResponse();

        FilterChain chain = mock(FilterChain.class);
        // 在 doFilter 调用时断言 MDC 已写入 service
        doAnswer(invocation -> {
            assertEquals("parse-service", MDC.get(LogFieldConstants.MDC_SERVICE),
                    "请求处理期间 MDC 应含 service=parse-service");
            return null;
        }).when(chain).doFilter(request, response);

        filter.doFilter(request, response, chain);

        assertNull(MDC.get(LogFieldConstants.MDC_SERVICE), "请求结束后 MDC service 应被清理");
        verify(chain, times(1)).doFilter(request, response);
    }

    @Test
    @DisplayName("testUnifiedLogMdcFilter_ExceptionStillCleansMdc: 链路抛异常时 finally 仍清理 MDC")
    void testUnifiedLogMdcFilter_ExceptionStillCleansMdc() throws Exception {
        OncePerRequestFilter filter = config.unifiedLogMdcFilter();
        HttpServletRequest request = new MockHttpServletRequest();
        HttpServletResponse response = new MockHttpServletResponse();

        FilterChain chain = mock(FilterChain.class);
        doThrow(new RuntimeException("downstream error"))
                .when(chain).doFilter(request, response);

        // UnifiedLogConfig 用 new RuntimeException(e) 包装下游异常，
        // 故外层 getMessage() 为 cause 的 toString，真实消息在 getCause() 中
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> filter.doFilter(request, response, chain));
        assertNotNull(ex.getCause(), "应保留原始 cause");
        assertEquals("downstream error", ex.getCause().getMessage(), "cause 消息应为 downstream error");
        assertNull(MDC.get(LogFieldConstants.MDC_SERVICE),
                "即使链路抛异常，finally 也应清理 MDC service");
    }
}
