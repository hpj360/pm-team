package com.redteam.common.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.List;

/**
 * 基于 Redis 的限流器
 *
 * <p>使用 Redis Lua 脚本实现原子化的固定窗口计数限流：
 * <ol>
 *   <li>对限流 key 执行 INCR，得到当前窗口内累计请求数</li>
 *   <li>如果是窗口内第一个请求，设置 TTL 为窗口时长</li>
 *   <li>判断计数是否 <= 限流阈值，返回是否放行</li>
 * </ol>
 *
 * <p>降级策略：当 Redis 不可用（连接失败等）时，限流降级为放行（返回 true），
 * 不阻塞业务流程，仅记录警告日志。</p>
 *
 * @author 红方团队
 */
@Slf4j
public class RateLimiter {

    /**
     * 令牌桶限流 Lua 脚本
     *
     * <p>KEYS[1] = 限流 key
     * ARGV[1] = 限流阈值（qps）
     * ARGV[2] = 窗口时长（秒）</p>
     *
     * <p>返回 1（true）表示允许通过，0（false）表示被限流</p>
     */
    private static final String LUA_SCRIPT =
            "local key = KEYS[1]\n" +
            "local limit = tonumber(ARGV[1])\n" +
            "local window = tonumber(ARGV[2])\n" +
            "local current = redis.call('INCR', key)\n" +
            "if current == 1 then\n" +
            "    redis.call('EXPIRE', key, window)\n" +
            "end\n" +
            "return current <= limit";

    private static final DefaultRedisScript<Boolean> RATE_LIMIT_SCRIPT;

    static {
        RATE_LIMIT_SCRIPT = new DefaultRedisScript<>();
        RATE_LIMIT_SCRIPT.setScriptText(LUA_SCRIPT);
        RATE_LIMIT_SCRIPT.setResultType(Boolean.class);
    }

    private final StringRedisTemplate redisTemplate;

    public RateLimiter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 令牌桶限流
     *
     * <p>在当前时间窗口内对 key 计数 +1，如果计数 <= qps 返回 true（放行），
     * 否则返回 false（限流）。窗口结束时 key 自动过期。</p>
     *
     * @param key    限流 key（如 "rate_limit:user:123:search"）
     * @param qps    每秒允许请求数
     * @param window 窗口秒数
     * @return true 允许通过，false 被限流
     */
    public boolean tryAcquire(String key, int qps, int window) {
        try {
            // 按窗口起点对齐时间戳，确保同一窗口内的请求使用相同的 Redis key
            long nowSeconds = System.currentTimeMillis() / 1000;
            long windowStart = nowSeconds / window * window;
            String redisKey = key + ":" + windowStart;

            List<String> keys = Collections.singletonList(redisKey);
            Boolean allowed = redisTemplate.execute(
                    RATE_LIMIT_SCRIPT, keys, String.valueOf(qps), String.valueOf(window));

            if (allowed == null) {
                log.warn("限流脚本返回 null，降级放行: key={}", key);
                return true;
            }
            return allowed;
        } catch (Exception e) {
            // Redis 不可用时降级放行，不阻塞业务
            log.warn("Redis 不可用，限流降级放行: {}", key, e);
            return true;
        }
    }
}
