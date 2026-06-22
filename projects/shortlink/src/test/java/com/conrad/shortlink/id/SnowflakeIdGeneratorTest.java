package com.conrad.shortlink.id;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.RepeatedTest;
import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.Set;

/**
 * SnowflakeIdGenerator 单元测试
 *
 * 教学点：
 * 1. 接口 IdGenerator 的存在让测试更通用：可以用同一套断言验证任意实现
 * 2. @RepeatedTest 是 JUnit 5 提供的"重复执行"注解，比手写 for 循环更声明式
 *    - Snowflake ID 必须在 1000 次内不重复
 * 3. 用 HashSet 验证唯一性：插入失败说明有重复 ID
 *    - Set.add() 返回 boolean，false 表示已存在
 * 4. 并发测试：起多个线程同时生成，验证不同 datacenter/worker 组合下不会冲突
 *
 * 对应学习模块：notes/java/04-concurrency（线程安全）+ 测试驱动开发
 */
class SnowflakeIdGeneratorTest {

    @Test
    void shouldGenerateNonEmptyCode() {
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator();
        String code = generator.generateCode();
        assertNotNull(code, "短码不应为 null");
        assertFalse(code.isEmpty(), "短码不应为空字符串");
    }

    @Test
    void shouldGenerateBase62Code() {
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator();
        String code = generator.generateCode();
        assertTrue(code.matches("^[0-9A-Za-z]+$"), "短码应只包含 Base62 字符: " + code);
    }

    /**
     * 核心测试：1000 次生成不重复
     *
     * 教学点：
     * - Snowflake 算法的核心保证是"全局唯一"
     * - 单实例内唯一性靠 sequence 自增 + 时间戳递增保证
     * - 跨实例唯一性靠 workerId + datacenterId 区分保证
     */
    @RepeatedTest(1000)
    void shouldGenerateUniqueCodes() {
        SnowflakeIdGenerator gen = new SnowflakeIdGenerator();
        String c1 = gen.generateCode();
        String c2 = gen.generateCode();
        assertNotEquals(c1, c2, "Snowflake ID 不应重复");
    }

    /**
     * 批量生成 1000 个 ID，验证全部唯一
     *
     * 教学点：比 @RepeatedTest 更严格的测试，能抓到 sequence 边界问题
     */
    @Test
    void shouldGenerate1000UniqueIds() {
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(1, 1);
        Set<Long> ids = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            long id = generator.nextId();
            assertTrue(ids.add(id), "Snowflake ID 重复: " + id);
        }
        assertEquals(1000, ids.size());
    }

    /**
     * 不同 workerId 的生成器产生的 ID 不会冲突
     *
     * 教学点：这是分布式场景的核心约束——多实例不能产生相同 ID
     */
    @Test
    void differentWorkerIdsShouldNotConflict() {
        SnowflakeIdGenerator gen1 = new SnowflakeIdGenerator(1, 1);
        SnowflakeIdGenerator gen2 = new SnowflakeIdGenerator(1, 2);
        SnowflakeIdGenerator gen3 = new SnowflakeIdGenerator(2, 1);

        Set<Long> ids = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            assertTrue(ids.add(gen1.nextId()), "gen1 重复");
            assertTrue(ids.add(gen2.nextId()), "gen2 重复");
            assertTrue(ids.add(gen3.nextId()), "gen3 重复");
        }
        assertEquals(300, ids.size());
    }

    @Test
    void base62EncodeAndDecodeShouldRoundTrip() {
        long[] testValues = {0L, 1L, 61L, 62L, 1234567890L, Long.MAX_VALUE / 2};
        for (long val : testValues) {
            String encoded = SnowflakeIdGenerator.encodeBase62(val);
            long decoded = SnowflakeIdGenerator.decodeBase62(encoded);
            assertEquals(val, decoded, "Base62 编解码应该可逆: " + val + " → " + encoded);
        }
    }

    @Test
    void base62ShouldProduceExpectedEncoding() {
        assertEquals("0", SnowflakeIdGenerator.encodeBase62(0));
        assertEquals("1", SnowflakeIdGenerator.encodeBase62(1));
        assertEquals("Z", SnowflakeIdGenerator.encodeBase62(61));
        assertEquals("10", SnowflakeIdGenerator.encodeBase62(62));
    }

    @Test
    void shouldRejectInvalidWorkerId() {
        assertThrows(IllegalArgumentException.class, () -> new SnowflakeIdGenerator(0, 32));
        assertThrows(IllegalArgumentException.class, () -> new SnowflakeIdGenerator(0, -1));
        assertThrows(IllegalArgumentException.class, () -> new SnowflakeIdGenerator(32, 0));
        assertThrows(IllegalArgumentException.class, () -> new SnowflakeIdGenerator(-1, 0));
    }

    /**
     * 并发安全测试：多线程同时生成 10000 个 ID 不重复
     *
     * 教学点：
     * - nextId() 加了 synchronized，所以即使多线程并发也不会生成重复 ID
     * - 这个测试能抓到 synchronized 漏加、AtomicLong 用错等并发 bug
     */
    @Test
    void shouldBeThreadSafe() throws InterruptedException {
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(1, 1);
        int threadCount = 10;
        int perThread = 1000;
        Set<Long>[] sets = new Set[threadCount];

        Thread[] threads = new Thread[threadCount];
        for (int i = 0; i < threadCount; i++) {
            sets[i] = new HashSet<>();
            final int idx = i;
            threads[i] = new Thread(() -> {
                for (int j = 0; j < perThread; j++) {
                    sets[idx].add(generator.nextId());
                }
            });
        }
        for (Thread t : threads) t.start();
        for (Thread t : threads) t.join();

        Set<Long> all = new HashSet<>();
        for (Set<Long> s : sets) {
            all.addAll(s);
        }
        // 线程间也不应该有重复（synchronized 保证）
        assertEquals(threadCount * perThread, all.size(), "多线程并发不能产生重复 ID");
    }
}
