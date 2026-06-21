package com.conrad.shortlink.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

/**
 * Redis 配置 - 只在启用 Redis 的 profile 生效
 *
 * 教学点：
 * - @ConditionalOnProperty：条件装配，shortlink.cache.type=redis 时才加载这个配置
 * - 这样默认 profile 不需要 Redis 也能跑（用 ConcurrentHashMap 内存版）
 * - Lua 脚本用于原子性令牌桶限流（Redis 单线程执行 Lua 脚本天然原子）
 *
 * 对应学习模块：notes/java/10-redis + 04-concurrency
 */
@Configuration
@ConditionalOnProperty(name = "shortlink.cache.type", havingValue = "redis")
public class RedisConfig {

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory factory) {
        return new StringRedisTemplate(factory);
    }

    /**
     * 令牌桶限流 Lua 脚本
     * 每次请求执行一次：检查并扣减令牌
     *
     * KEYS[1] = 桶的 key
     * ARGV[1] = 桶容量
     * ARGV[2] = 速率（每秒补充的令牌数）
     * ARGV[3] = 当前时间戳（毫秒）
     * ARGV[4] = 请求消耗的令牌数
     *
     * 返回 1 表示通过，0 表示限流
     */
    @Bean
    public DefaultRedisScript<Long> rateLimitScript() {
        String lua = """
                local key = KEYS[1]
                local capacity = tonumber(ARGV[1])
                local rate = tonumber(ARGV[2])
                local now = tonumber(ARGV[3])
                local requested = tonumber(ARGV[4])

                local bucket = redis.call('HMGET', key, 'tokens', 'last_refill')
                local tokens = tonumber(bucket[1]) or capacity
                local last_refill = tonumber(bucket[2]) or now

                -- 计算补充：距上次补充过去多少秒 * 速率
                local elapsed = math.max(0, now - last_refill) / 1000.0
                tokens = math.min(capacity, tokens + elapsed * rate)

                if tokens >= requested then
                    tokens = tokens - requested
                    redis.call('HMSET', key, 'tokens', tokens, 'last_refill', now)
                    redis.call('EXPIRE', key, math.ceil(capacity / rate) + 1)
                    return 1
                else
                    redis.call('HMSET', key, 'tokens', tokens, 'last_refill', now)
                    redis.call('EXPIRE', key, math.ceil(capacity / rate) + 1)
                    return 0
                end
                """;
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(lua);
        script.setResultType(Long.class);
        return script;
    }
}
