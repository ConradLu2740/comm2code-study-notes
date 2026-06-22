package com.conrad.shortlink.id;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * RedisIdGenerator 单元测试（Mock 方式）
 *
 * 教学点：
 * 1. 为什么用 Mock 而不是 embedded redis？
 *    - embedded redis（it.ozimov、redis-mock）依赖繁琐，启动慢，CI 环境容易踩坑
 *    - Mock 方式只验证"调用了 INCR"这个契约，测试更轻、跑得更快
 *    - 这是经典的"测行为不测实现"思路
 * 2. Mock 框架：Mockito（Spring Boot 默认带的）
 *    - mock() 创建 mock 对象，所有方法返回默认值
 *    - when().thenReturn() 桩方法返回值
 *    - verify() 验证方法被调用
 *    - ArgumentCaptor 捕获调用参数
 * 3. 验证点：
 *    - nextId() 调用了 redis.opsForValue().increment(key)
 *    - key 用的是 "shortlink:id:counter"
 *    - 返回值正确传递给调用方
 * 4. 生产可以引入 com.github.codemonstur:embedded-redis 等做集成测试，
 *    但本项目只做单元测试，Mock 足够覆盖核心逻辑
 *
 * 对应学习模块：notes/java/11-test（Mock 测试）+ 10-redis（INCR 命令）
 */
class RedisIdGeneratorTest {

    /**
     * 核心测试：nextId 必须调用 Redis INCR
     *
     * 教学点：
     * - mock StringRedisTemplate，模拟 Redis 返回递增的值
     * - 验证 RedisIdGenerator 真的走 Redis 而不是本地生成
     */
    @Test
    void nextIdShouldCallRedisIncr() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> ops = mock(ValueOperations.class);

        when(redisTemplate.opsForValue()).thenReturn(ops);
        // 模拟 Redis INCR 自增：第一次返回 1，第二次返回 2
        when(ops.increment("shortlink:id:counter")).thenReturn(1L, 2L, 3L);

        RedisIdGenerator generator = new RedisIdGenerator(redisTemplate);

        long id1 = generator.nextId();
        long id2 = generator.nextId();
        long id3 = generator.nextId();

        assertEquals(1L, id1);
        assertEquals(2L, id2);
        assertEquals(3L, id3);

        // 验证 INCR 被调用了 3 次
        verify(ops, times(3)).increment("shortlink:id:counter");
    }

    /**
     * 验证 key 前缀正确
     *
     * 教学点：
     * - 用 ArgumentCaptor 拿到传给 INCR 的实际 key
     * - 确保用了正确的命名空间，避免和 cache/rate-limit 的 key 撞车
     */
    @Test
    void shouldUseCorrectCounterKey() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> ops = mock(ValueOperations.class);

        when(redisTemplate.opsForValue()).thenReturn(ops);
        when(ops.increment(anyString())).thenReturn(100L);

        RedisIdGenerator generator = new RedisIdGenerator(redisTemplate);
        generator.nextId();

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(ops).increment(keyCaptor.capture());

        assertEquals("shortlink:id:counter", keyCaptor.getValue(),
            "counter key 应该用 shortlink:id:counter 前缀，避免业务冲突");
    }

    /**
     * generateCode 应该调用 INCR 并做 Base62 编码
     *
     * 教学点：
     * - generateCode() = nextId() + encodeBase62()
     * - 验证短码形态符合 Base62 字符集
     */
    @Test
    void generateCodeShouldReturnBase62String() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> ops = mock(ValueOperations.class);

        when(redisTemplate.opsForValue()).thenReturn(ops);
        when(ops.increment("shortlink:id:counter")).thenReturn(1234567890L);

        RedisIdGenerator generator = new RedisIdGenerator(redisTemplate);
        String code = generator.generateCode();

        assertNotNull(code);
        assertFalse(code.isEmpty());
        assertTrue(code.matches("^[0-9A-Za-z]+$"), "短码应只包含 Base62 字符: " + code);
        verify(ops).increment("shortlink:id:counter");
    }

    /**
     * Redis 返回 null 应该抛异常（不要静默失败）
     *
     * 教学点：fail-fast 原则，错误立即抛出，不要给调用方一个看似正常的 null
     */
    @Test
    void shouldThrowWhenRedisReturnsNull() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> ops = mock(ValueOperations.class);

        when(redisTemplate.opsForValue()).thenReturn(ops);
        when(ops.increment(anyString())).thenReturn(null);

        RedisIdGenerator generator = new RedisIdGenerator(redisTemplate);

        assertThrows(IllegalStateException.class, generator::nextId,
            "Redis 返回 null 应该抛 IllegalStateException，避免静默生成 0");
    }

    /**
     * Snowflake + Redis 模式：从 Redis 分配 workerId
     *
     * 教学点：
     * - 验证这个构造器会先调一次 INCR 拿 workerId
     * - 之后 nextId() 走本地 Snowflake，不再访问 Redis
     */
    @Test
    void snowflakeModeShouldAllocateWorkerIdFromRedis() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> ops = mock(ValueOperations.class);

        when(redisTemplate.opsForValue()).thenReturn(ops);
        // 分配 workerId 时返回 5（说明这是第 6 个实例启动）
        when(ops.increment("shortlink:id:worker:assigned")).thenReturn(6L);

        RedisIdGenerator generator = new RedisIdGenerator(redisTemplate, 1L);

        // 验证 workerId 分配确实调了 INCR
        verify(ops, times(1)).increment("shortlink:id:worker:assigned");

        // Snowflake 模式下，后续生成不调 Redis
        long id1 = generator.nextId();
        long id2 = generator.nextId();
        assertNotEquals(id1, id2);
        assertTrue(id1 > 0);

        // 后续 nextId 不应该再调 Redis INCR（除了 workerId 分配那次）
        // counter key 不应该被调用
        verify(ops, never()).increment("shortlink:id:counter");
    }

    /**
     * Snowflake 模式的 short code 也应该是合法的 Base62
     */
    @Test
    void snowflakeModeShouldGenerateBase62Code() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> ops = mock(ValueOperations.class);

        when(redisTemplate.opsForValue()).thenReturn(ops);
        when(ops.increment("shortlink:id:worker:assigned")).thenReturn(1L);

        RedisIdGenerator generator = new RedisIdGenerator(redisTemplate, 1L);
        String code = generator.generateCode();

        assertNotNull(code);
        assertTrue(code.matches("^[0-9A-Za-z]+$"), "短码应只包含 Base62 字符: " + code);
        assertTrue(generator.isSnowflakeMode());
    }

    /**
     * 简单模式的标识
     */
    @Test
    void simpleModeShouldNotBeSnowflakeMode() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        RedisIdGenerator generator = new RedisIdGenerator(redisTemplate);
        assertFalse(generator.isSnowflakeMode());
    }

    /**
     * 多线程并发：Redis INCR 原子自增，100 个并发调用应全部成功
     *
     * 教学点：
     * - 真实 Redis 是单线程串行执行命令，INCR 天然原子
     * - Mock 模式下我们只验证调用次数正确（每次都拿到不同的返回值）
     */
    @Test
    void shouldHandleConcurrentCalls() throws InterruptedException {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> ops = mock(ValueOperations.class);

        when(redisTemplate.opsForValue()).thenReturn(ops);
        // 模拟 Redis 真实行为：每次自增 +1
        java.util.concurrent.atomic.AtomicLong counter = new java.util.concurrent.atomic.AtomicLong(0);
        when(ops.increment("shortlink:id:counter")).thenAnswer(invocation -> counter.incrementAndGet());

        RedisIdGenerator generator = new RedisIdGenerator(redisTemplate);

        int threadCount = 10;
        int perThread = 100;
        java.util.Set<Long>[] sets = new java.util.Set[threadCount];
        Thread[] threads = new Thread[threadCount];

        for (int i = 0; i < threadCount; i++) {
            sets[i] = java.util.Collections.synchronizedSet(new java.util.HashSet<>());
            final int idx = i;
            threads[i] = new Thread(() -> {
                for (int j = 0; j < perThread; j++) {
                    sets[idx].add(generator.nextId());
                }
            });
        }
        for (Thread t : threads) t.start();
        for (Thread t : threads) t.join();

        // 1000 个调用应该产生 1000 个不同的 ID（Redis 模拟返回递增）
        java.util.Set<Long> all = new java.util.HashSet<>();
        for (java.util.Set<Long> s : sets) all.addAll(s);
        assertEquals(threadCount * perThread, all.size());
        verify(ops, times(threadCount * perThread)).increment("shortlink:id:counter");
    }
}
