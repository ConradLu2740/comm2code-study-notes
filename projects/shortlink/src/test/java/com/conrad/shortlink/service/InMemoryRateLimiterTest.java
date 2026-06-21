package com.conrad.shortlink.service;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 内存限流器测试
 */
class InMemoryRateLimiterTest {

    @Test
    void shouldAllowUpToCapacity() {
        InMemoryRateLimiter limiter = new InMemoryRateLimiter();
        ReflectionTestUtils.setField(limiter, "capacity", 5.0);
        ReflectionTestUtils.setField(limiter, "rate", 1.0);

        for (int i = 0; i < 5; i++) {
            assertTrue(limiter.tryAcquire("user1"), "第 " + (i + 1) + " 次应该通过");
        }
        assertFalse(limiter.tryAcquire("user1"), "第 6 次应该被限流");
    }

    @Test
    void differentKeysShouldHaveSeparateBuckets() {
        InMemoryRateLimiter limiter = new InMemoryRateLimiter();
        ReflectionTestUtils.setField(limiter, "capacity", 2.0);
        ReflectionTestUtils.setField(limiter, "rate", 0.1);

        assertTrue(limiter.tryAcquire("user1"));
        assertTrue(limiter.tryAcquire("user1"));
        assertFalse(limiter.tryAcquire("user1"));

        // user2 桶是独立的
        assertTrue(limiter.tryAcquire("user2"));
        assertTrue(limiter.tryAcquire("user2"));
    }

    @Test
    void shouldRefillOverTime() throws InterruptedException {
        InMemoryRateLimiter limiter = new InMemoryRateLimiter();
        ReflectionTestUtils.setField(limiter, "capacity", 2.0);
        ReflectionTestUtils.setField(limiter, "rate", 10.0);  // 每秒补充 10 个

        // 用完令牌
        assertTrue(limiter.tryAcquire("user"));
        assertTrue(limiter.tryAcquire("user"));
        assertFalse(limiter.tryAcquire("user"));

        // 等 200ms 应该补充约 2 个令牌
        Thread.sleep(200);
        assertTrue(limiter.tryAcquire("user"), "补充后应该能通过");
    }
}
