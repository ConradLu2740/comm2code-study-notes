package com.conrad.shortlink.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 内存版令牌桶限流器
 *
 * 教学点：
 * 1. 令牌桶核心：tokens + lastRefill 状态
 * 2. 惰性补充：每次请求时按 (now - lastRefill) * rate 补充令牌
 * 3. 不需要后台线程，性能高
 * 4. 内存版只适合单机，分布式场景必须用 Redis 版
 *
 * 对应学习模块：notes/java/04-concurrency + 10-redis
 */
@Service
@ConditionalOnProperty(name = "shortlink.cache.type", havingValue = "memory", matchIfMissing = true)
public class InMemoryRateLimiter implements RateLimiter {

    private record TokenBucket(double tokens, long lastRefillMs) {}

    private final Map<String, AtomicReference<TokenBucket>> buckets = new ConcurrentHashMap<>();

    @Value("${shortlink.rate-limit.capacity:10}")
    private double capacity;

    @Value("${shortlink.rate-limit.rate:5}")
    private double rate;

    @Override
    public boolean tryAcquire(String key) {
        return tryAcquire(key, 1);
    }

    @Override
    public synchronized boolean tryAcquire(String key, int permits) {
        AtomicReference<TokenBucket> ref = buckets.computeIfAbsent(
            key, k -> new AtomicReference<>(new TokenBucket(capacity, System.currentTimeMillis()))
        );

        while (true) {
            TokenBucket old = ref.get();
            long now = System.currentTimeMillis();
            double elapsed = Math.max(0, now - old.lastRefillMs()) / 1000.0;
            double newTokens = Math.min(capacity, old.tokens() + elapsed * rate);

            if (newTokens >= permits) {
                TokenBucket updated = new TokenBucket(newTokens - permits, now);
                if (ref.compareAndSet(old, updated)) {
                    return true;
                }
                // CAS 失败，重试
            } else {
                TokenBucket updated = new TokenBucket(newTokens, now);
                ref.compareAndSet(old, updated);
                return false;
            }
        }
    }
}
