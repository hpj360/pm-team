package com.redteam.search.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * 检索结果缓存配置
 *
 * <p>基于 Redisson 提供检索结果缓存能力，命中缓存可显著降低 ES + Milvus 混合检索延迟。
 * 缓存 key 命名规范：{@code search:cache:{searchType}:{md5(queryJson)}}。</p>
 *
 * @author 红方团队
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class SearchCacheConfig {

    private final SearchProperties searchProperties;

    /**
     * 检索缓存助手
     *
     * @return SearchCache
     */
    @Bean
    public SearchCache searchCache(RedissonClient redissonClient) {
        return new SearchCache(redissonClient, searchProperties.getCache().getTtlSeconds());
    }

    /**
     * 检索缓存封装
     *
     * <p>提供 get/put/delete 三个基础操作，封装 Redisson 调用细节。</p>
     *
     * @author 红方团队
     */
    public static class SearchCache {

        private final RedissonClient redissonClient;
        private final int ttlSeconds;

        /**
         * 构造方法
         *
         * @param redissonClient Redisson 客户端
         * @param ttlSeconds     缓存过期时间（秒）
         */
        public SearchCache(RedissonClient redissonClient, int ttlSeconds) {
            this.redissonClient = redissonClient;
            this.ttlSeconds = ttlSeconds;
        }

        /**
         * 读取缓存
         *
         * @param key 缓存键
         * @param <T> 值类型
         * @return 缓存值，不存在返回 null
         */
        public <T> T get(String key) {
            try {
                RBucket<T> bucket = redissonClient.getBucket(key);
                return bucket.get();
            } catch (Exception e) {
                log.warn("读取检索缓存失败: key={}", key, e);
                return null;
            }
        }

        /**
         * 写入缓存
         *
         * @param key   缓存键
         * @param value 缓存值
         * @param <T>   值类型
         */
        public <T> void put(String key, T value) {
            if (value == null) {
                return;
            }
            try {
                redissonClient.getBucket(key).set(value, ttlSeconds, TimeUnit.SECONDS);
            } catch (Exception e) {
                log.warn("写入检索缓存失败: key={}", key, e);
            }
        }

        /**
         * 删除缓存
         *
         * @param key 缓存键
         */
        public void delete(String key) {
            try {
                redissonClient.getBucket(key).delete();
            } catch (Exception e) {
                log.warn("删除检索缓存失败: key={}", key, e);
            }
        }
    }
}
