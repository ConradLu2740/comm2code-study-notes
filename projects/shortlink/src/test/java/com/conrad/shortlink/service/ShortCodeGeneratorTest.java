package com.conrad.shortlink.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.RepeatedTest;
import static org.junit.jupiter.api.Assertions.*;

/**
 * ShortCodeGenerator 单元测试
 *
 * 教学点：
 * 1. JUnit 5 用 @Test 标记测试方法
 * 2. @RepeatedTest 重复执行（Snowflake ID 不能重复）
 * 3. assertNotNull / assertEquals / assertTrue 是基本断言
 */
class ShortCodeGeneratorTest {

    @Test
    void shouldGenerateNonEmptyCode() {
        ShortCodeGenerator generator = new ShortCodeGenerator();
        String code = generator.generate();
        assertNotNull(code);
        assertFalse(code.isEmpty());
    }

    @Test
    void shouldGenerateBase62Code() {
        ShortCodeGenerator generator = new ShortCodeGenerator();
        String code = generator.generate();
        assertTrue(code.matches("^[0-9A-Za-z]+$"), "短码应只包含 Base62 字符: " + code);
    }

    @RepeatedTest(1000)
    void shouldGenerateUniqueCodes() {
        ShortCodeGenerator gen = new ShortCodeGenerator();
        String c1 = gen.generate();
        String c2 = gen.generate();
        assertNotEquals(c1, c2, "Snowflake ID 不应重复");
    }

    @Test
    void base62EncodeAndDecodeShouldRoundTrip() {
        long[] testValues = {0L, 1L, 61L, 62L, 1234567890L, Long.MAX_VALUE / 2};
        for (long val : testValues) {
            String encoded = ShortCodeGenerator.encodeBase62(val);
            long decoded = ShortCodeGenerator.decodeBase62(encoded);
            assertEquals(val, decoded, "Base62 编解码应该可逆: " + val + " → " + encoded);
        }
    }

    @Test
    void base62ShouldProduceExpectedEncoding() {
        assertEquals("0", ShortCodeGenerator.encodeBase62(0));
        assertEquals("1", ShortCodeGenerator.encodeBase62(1));
        assertEquals("Z", ShortCodeGenerator.encodeBase62(61));
        assertEquals("10", ShortCodeGenerator.encodeBase62(62));
    }

    @Test
    void shouldRejectInvalidWorkerId() {
        assertThrows(IllegalArgumentException.class, () -> new ShortCodeGenerator(0, 32));
        assertThrows(IllegalArgumentException.class, () -> new ShortCodeGenerator(0, -1));
    }
}
