package com.conrad.shortlink.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import java.time.Duration;

/**
 * Redis 版缓存服务 - 生产环境实现
 *
 * 教学点：
 * 1. @ConditionalOnProperty：只在 cache.type=redis 时加载（与内存版互斥）
 * 2. StringRedisTemplate 是 Spring Data Redis 提供的字符串操作模板
 * 3. opsForValue().set(key, value, duration) 一行搞定带 TTL 的 set
 *
 * 对应学习模块：notes/java/10-redis
 */
@Service
@ConditionalOnProperty(name = "shortlink.cache.type", havingValue = "redis")
public class RedisCacheService implements CacheService {

    private final StringRedisTemplate redis;

    public RedisCacheService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public String get(String key) {
        return redis.opsForValue().get(key);
    }

    @Override
    public void set(String key, String value, long ttlSeconds) {
        redis.opsForValue().set(key, value, Duration.ofSeconds(ttlSeconds));
    }

    @Override
    public void delete(String key) {
        redis.delete(key);
    }

    @Override
    public boolean hasKey(String key) {
        Boolean exists = redis.hasKey(key);
        return Boolean.TRUE.equals(exists);
    }
}
