package com.conrad.shortlink.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import java.util.List;

/**
 * Redis 版令牌桶限流器 - 生产环境
 *
 * 教学点：
 * 1. 用 Lua 脚本保证「读 - 改 - 写」原子性（Redis 单线程执行 Lua）
 * 2. 分布式友好：多个应用实例共享 Redis 限流状态
 * 3. 状态存在 Redis Hash 里：tokens + last_refill
 */
@Service
@ConditionalOnProperty(name = "shortlink.cache.type", havingValue = "redis")
public class RedisRateLimiter implements RateLimiter {

    private final StringRedisTemplate redis;
    private final DefaultRedisScript<Long> script;

    @Value("${shortlink.rate-limit.capacity:10}")
    private long capacity;

    @Value("${shortlink.rate-limit.rate:5}")
    private long rate;

    public RedisRateLimiter(StringRedisTemplate redis, DefaultRedisScript<Long> rateLimitScript) {
        this.redis = redis;
        this.script = rateLimitScript;
    }

    @Override
    public boolean tryAcquire(String key) {
        return tryAcquire(key, 1);
    }

    @Override
    public boolean tryAcquire(String key, int permits) {
        String fullKey = "ratelimit:" + key;
        Long result = redis.execute(
            script,
            List.of(fullKey),
            String.valueOf(capacity),
            String.valueOf(rate),
            String.valueOf(System.currentTimeMillis()),
            String.valueOf(permits)
        );
        return result != null && result == 1L;
    }
}
