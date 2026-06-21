package com.conrad.shortlink.service;

/**
 * 限流器抽象接口
 *
 * 教学点：
 * 1. 令牌桶算法：桶里放 N 个令牌，每秒补充 R 个，请求消耗 1 个
 * 2. 桶空时拒绝请求
 * 3. Redis 版用 Lua 脚本保证原子性，内存版用 synchronized
 */
public interface RateLimiter {

    /**
     * 尝试获取令牌
     * @param key 限流维度（IP / 用户 ID 等）
     * @return true 放行，false 限流
     */
    boolean tryAcquire(String key);

    /**
     * 尝试获取多个令牌
     * @param key 限流维度
     * @param permits 需要的令牌数
     */
    boolean tryAcquire(String key, int permits);
}
