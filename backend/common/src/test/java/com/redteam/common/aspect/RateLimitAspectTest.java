package com.redteam.common.aspect;

import com.redteam.common.annotation.RateLimit;
import com.redteam.common.exception.BusinessException;
import com.redteam.common.util.RateLimiter;
import com.redteam.common.util.UserContext;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * RateLimitAspect 单元测试
 *
 * <p>覆盖以下场景：
 * <ol>
 *   <li>RateLimiter.tryAcquire 未超限放行</li>
 *   <li>RateLimiter.tryAcquire 超限被拦截</li>
 *   <li>buildKey 用户维度 key 构建</li>
 *   <li>buildKey IP 维度 key 构建</li>
 *   <li>Redis 不可用时降级放行</li>
 *   <li>注解触发限流抛出 BusinessException(429)</li>
 * </ol>
 *
 * @author 红方团队
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RateLimitAspectTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private RateLimiter mockRateLimiter;

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Mock
    private MethodSignature methodSignature;

    /** 真实的 RateLimiter（内部 mock RedisTemplate） */
    private RateLimiter rateLimiter;

    /** 被测切面（内部注入 mockRateLimiter） */
    private RateLimitAspect aspect;

    /**
     * 测试用控制器，提供带 @RateLimit 注解的方法
     */
    public static class TestController {

        @RateLimit(qps = 10, limitType = "USER")
        public String searchUser() {
            return "user-ok";
        }

        @RateLimit(qps = 10, limitType = "IP")
        public String searchIp() {
            return "ip-ok";
        }

        @RateLimit(qps = 5, limitType = "USER", message = "上传太频繁")
        public String upload() {
            return "upload-ok";
        }
    }

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        rateLimiter = new RateLimiter(redisTemplate);
        aspect = new RateLimitAspect();
        // 注入 mockRateLimiter 到切面
        ReflectionTestUtils.setField(aspect, "rateLimiter", mockRateLimiter);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
        RequestContextHolder.resetRequestAttributes();
    }

    // ==================== RateLimiter 测试 ====================

    @Test
    @DisplayName("testTryAcquire_Allowed: 未超限放行")
    void testTryAcquire_Allowed() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(), any()))
                .thenReturn(Boolean.TRUE);
        boolean result = rateLimiter.tryAcquire("rate_limit:user:123:search", 10, 1);
        assertTrue(result, "未超限应放行");
    }

    @Test
    @DisplayName("testTryAcquire_Blocked: 超限被拦截")
    void testTryAcquire_Blocked() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(), any()))
                .thenReturn(Boolean.FALSE);
        boolean result = rateLimiter.tryAcquire("rate_limit:user:123:search", 10, 1);
        assertFalse(result, "超限应被拦截");
    }

    @Test
    @DisplayName("testRedisDown_GracefulFallback: Redis 不可用降级放行")
    void testRedisDown_GracefulFallback() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(), any()))
                .thenThrow(new RuntimeException("Connection refused"));
        boolean result = rateLimiter.tryAcquire("rate_limit:user:123:search", 10, 1);
        assertTrue(result, "Redis 不可用时应降级放行，不阻塞业务");
    }

    // ==================== buildKey 测试 ====================

    @Test
    @DisplayName("testBuildKey_User: 用户维度 key 包含 user:{userId}")
    void testBuildKey_User() throws Throwable {
        UserContext.setUserId(123L);
        setupJoinPoint("searchUser");

        when(mockRateLimiter.tryAcquire(anyString(), anyInt(), anyInt())).thenReturn(true);
        when(joinPoint.proceed()).thenReturn("user-ok");

        aspect.around(joinPoint, getRateLimit("searchUser"));

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockRateLimiter).tryAcquire(keyCaptor.capture(), anyInt(), anyInt());
        String key = keyCaptor.getValue();
        assertTrue(key.startsWith("rate_limit:user:123:"), "用户维度 key 应以 rate_limit:user:123: 开头");
        assertTrue(key.contains("TestController.searchUser"), "key 应包含 ClassName.methodName");
    }

    @Test
    @DisplayName("testBuildKey_IP: IP 维度 key 包含 ip:{ip}")
    void testBuildKey_IP() throws Throwable {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.168.1.100");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        setupJoinPoint("searchIp");

        when(mockRateLimiter.tryAcquire(anyString(), anyInt(), anyInt())).thenReturn(true);
        when(joinPoint.proceed()).thenReturn("ip-ok");

        aspect.around(joinPoint, getRateLimit("searchIp"));

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockRateLimiter).tryAcquire(keyCaptor.capture(), anyInt(), anyInt());
        String key = keyCaptor.getValue();
        assertTrue(key.startsWith("rate_limit:ip:192.168.1.100:"), "IP 维度 key 应以 rate_limit:ip:192.168.1.100: 开头");
        assertTrue(key.contains("TestController.searchIp"), "key 应包含 ClassName.methodName");
    }

    // ==================== 注解触发限流测试 ====================

    @Test
    @DisplayName("testRateLimitAnnotation: 超限时抛出 BusinessException(429)")
    void testRateLimitAnnotation() throws Throwable {
        setupJoinPoint("upload");

        when(mockRateLimiter.tryAcquire(anyString(), anyInt(), anyInt())).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> aspect.around(joinPoint, getRateLimit("upload")));
        assertEquals(429, ex.getCode(), "限流异常码应为 429");
        assertEquals("上传太频繁", ex.getMessage(), "异常消息应使用注解中的 message");

        // 验证被限流时方法未被执行
        verify(joinPoint, never()).proceed();
    }

    // ==================== 辅助方法 ====================

    /**
     * 设置 joinPoint / methodSignature 的 mock 行为
     *
     * @param methodName 测试控制器中的方法名
     */
    private void setupJoinPoint(String methodName) throws Exception {
        Method method = TestController.class.getMethod(methodName);
        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(method);
        when(joinPoint.getArgs()).thenReturn(new Object[0]);
    }

    /**
     * 获取测试方法上的 @RateLimit 注解
     *
     * @param methodName 测试控制器中的方法名
     * @return RateLimit 注解实例
     */
    private RateLimit getRateLimit(String methodName) throws Exception {
        Method method = TestController.class.getMethod(methodName);
        return method.getAnnotation(RateLimit.class);
    }
}
