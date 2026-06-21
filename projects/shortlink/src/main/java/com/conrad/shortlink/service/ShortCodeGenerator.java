package com.conrad.shortlink.service;

import org.springframework.stereotype.Component;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 短码生成器：Snowflake ID + Base62 编码
 *
 * 教学点：
 * 1. Snowflake 是 Twitter 开源的分布式 ID 算法，64 位 long
 *    - 1 位符号（固定 0）
 *    - 41 位时间戳（毫秒级，可用 69 年）
 *    - 10 位机器 ID（5 位数据中心 + 5 位工作机器，部署多机时分配）
 *    - 12 位序列号（同一毫秒内自增，单机每毫秒 4096 个 ID）
 * 2. Base62 编码：0-9 + a-z + A-Z，6 位 ≈ 568 亿组合，7 位 ≈ 3.5 万亿
 * 3. 整个生成过程不查数据库，性能极高
 *
 * 对应学习模块：notes/java/05-jvm (long 类型) + 算法
 */
@Component
public class ShortCodeGenerator {

    // ===== Snowflake 参数 =====
    private static final long EPOCH = 1700000000000L;  // 2023-11-14，自定义起始时间

    private static final long WORKER_ID_BITS = 5L;
    private static final long DATACENTER_ID_BITS = 5L;
    private static final long SEQUENCE_BITS = 12L;

    private static final long MAX_WORKER_ID = ~(-1L << WORKER_ID_BITS);  // 31
    private static final long MAX_DATACENTER_ID = ~(-1L << DATACENTER_ID_BITS);  // 31
    private static final long SEQUENCE_MASK = ~(-1L << SEQUENCE_BITS);  // 4095

    private static final long WORKER_ID_SHIFT = SEQUENCE_BITS;
    private static final long DATACENTER_ID_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;
    private static final long TIMESTAMP_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS + DATACENTER_ID_BITS;

    // ===== 状态 =====
    private final long datacenterId;   // 0-31
    private final long workerId;       // 0-31
    private final AtomicLong sequence = new AtomicLong(0L);
    private volatile long lastTimestamp = -1L;

    public ShortCodeGenerator() {
        this(1, 1);  // 默认 datacenter=1, worker=1
    }

    public ShortCodeGenerator(long datacenterId, long workerId) {
        if (datacenterId > MAX_DATACENTER_ID || datacenterId < 0) {
            throw new IllegalArgumentException("datacenterId 必须在 0-31 之间");
        }
        if (workerId > MAX_WORKER_ID || workerId < 0) {
            throw new IllegalArgumentException("workerId 必须在 0-31 之间");
        }
        this.datacenterId = datacenterId;
        this.workerId = workerId;
    }

    /**
     * 生成下一个 Snowflake ID
     */
    public synchronized long nextId() {
        long timestamp = currentTimeMillis();

        // 时钟回拨处理
        if (timestamp < lastTimestamp) {
            throw new RuntimeException("系统时钟发生回拨，拒绝生成 ID: " +
                (lastTimestamp - timestamp) + "ms");
        }

        if (timestamp == lastTimestamp) {
            // 同一毫秒内，序列号自增
            long seq = sequence.incrementAndGet() & SEQUENCE_MASK;
            if (seq == 0) {
                // 序列号用完，等待下一毫秒
                timestamp = waitNextMillis(lastTimestamp);
            }
        } else {
            sequence.set(0L);
        }

        lastTimestamp = timestamp;

        return ((timestamp - EPOCH) << TIMESTAMP_SHIFT)
            | (datacenterId << DATACENTER_ID_SHIFT)
            | (workerId << WORKER_ID_SHIFT)
            | sequence.get();
    }

    /**
     * 生成短码（Snowflake ID → Base62 字符串）
     */
    public String generate() {
        long id = nextId();
        return encodeBase62(id);
    }

    private long waitNextMillis(long lastTimestamp) {
        long timestamp = currentTimeMillis();
        while (timestamp <= lastTimestamp) {
            timestamp = currentTimeMillis();
        }
        return timestamp;
    }

    private long currentTimeMillis() {
        return System.currentTimeMillis();
    }

    // ===== Base62 编码 =====

    private static final String BASE62_CHARS = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";

    /**
     * 把 long 转成 Base62 字符串
     * 例如 1234567890L → "1ly7v3"（取决于具体值）
     */
    public static String encodeBase62(long value) {
        if (value == 0) return "0";
        StringBuilder sb = new StringBuilder();
        boolean negative = value < 0;
        if (negative) value = -value;
        while (value > 0) {
            int remainder = (int) (value % 62);
            sb.append(BASE62_CHARS.charAt(remainder));
            value /= 62;
        }
        if (negative) sb.append('-');
        return sb.reverse().toString();
    }

    /**
     * Base62 字符串 → long（用于反查）
     */
    public static long decodeBase62(String s) {
        long result = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            int index = BASE62_CHARS.indexOf(c);
            if (index < 0) {
                throw new IllegalArgumentException("非法的 Base62 字符: " + c);
            }
            result = result * 62 + index;
        }
        return result;
    }
}
