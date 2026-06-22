package com.conrad.shortlink.id;

import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Redis 版 ID 生成器
 *
 * 教学点：
 * 1. 两种工作模式（通过构造器区分）：
 *    a. 简单 INCR 模式：Redis 原子自增 key，天然全局唯一，部署多实例安全
 *       - 优点：实现最简、分布式友好、不依赖本地时钟
 *       - 缺点：强依赖 Redis 可用性；ID 不带时间信息，无法反推生成时间
 *       - ID 形态：单调递增的整数（短码形态不好控制长度）
 *    b. Snowflake + Redis workerId 分配模式（进阶）：
 *       - 用 Redis 给每个实例动态分配 workerId，避免多实例 workerId 冲突
 *       - 本地 Snowflake 生成，QPS 比纯 Redis 高一两个数量级
 *       - 更接近生产级方案（美团 Leaf-snowflake 同款思路）
 * 2. Redis INCR 是原子命令（Redis 单线程执行），天然全局唯一
 *    - 即使多台机器同时调用，Redis 也会串行化处理
 *    - 但要付出网络 RTT 代价（通常 0.1-1ms），比 Snowflake 慢 100x
 * 3. key 前缀 "shortlink:id:counter" 区分业务，避免和 cache/rate-limit 的 key 撞车
 * 4. 生产环境更推荐：
 *    - 美团 Leaf：支持号段 + Snowflake 双模式
 *    - 百度 UID-Generator：基于 Snowflake 改进
 *    - Tinyid：滴滴的开源方案，轻量
 *
 * 对应学习模块：notes/java/10-redis（INCR 原子命令）+ 04-concurrency（CAS 与分布式锁）
 */
public class RedisIdGenerator implements IdGenerator {

    /** Redis 计数器 key 前缀 */
    private static final String COUNTER_KEY = "shortlink:id:counter";

    /** Snowflake 模式下分配 workerId 的 key */
    private static final String WORKER_ID_KEY = "shortlink:id:worker:assigned";

    /** 工作模式：true = Snowflake + Redis 分配 workerId，false = 简单 INCR */
    private final boolean snowflakeMode;

    /** 简单 INCR 模式的 Redis 模板（snowflakeMode=false 时使用） */
    private final StringRedisTemplate redisTemplate;

    /** Snowflake 模式下的本地生成器（snowflakeMode=true 时使用） */
    private final SnowflakeIdGenerator snowflakeGenerator;

    /**
     * 构造器 a：简单 INCR 模式
     *
     * @param redisTemplate Redis 客户端（Spring 自动注入）
     */
    public RedisIdGenerator(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.snowflakeMode = false;
        this.snowflakeGenerator = null;
    }

    /**
     * 构造器 b：Snowflake + Redis 分配 workerId 模式
     *
     * 教学点：
     * - datacenterId 固定为 1（教学项目简化）
     * - workerId 用 Redis INCR 分配（0-1023 共 1024 个槽位，足够千台机器）
     * - 真实生产还要考虑 workerId 续约、心跳、回收（实例下线后归还）
     *
     * @param redisTemplate  Redis 客户端
     * @param datacenterId   数据中心 ID（0-31）
     */
    public RedisIdGenerator(StringRedisTemplate redisTemplate, long datacenterId) {
        this.redisTemplate = redisTemplate;
        this.snowflakeMode = true;
        long workerId = allocateWorkerId();
        this.snowflakeGenerator = new SnowflakeIdGenerator(datacenterId, workerId);
    }

    /**
     * 从 Redis 分配一个 workerId
     * 用 INCR 自增，超过 1024（10 位）就回卷重新分配（理论上应告警）
     */
    private long allocateWorkerId() {
        Long assigned = redisTemplate.opsForValue().increment(WORKER_ID_KEY);
        if (assigned == null) {
            throw new IllegalStateException("从 Redis 分配 workerId 失败");
        }
        // workerId 范围 0-31（5 位），超过回卷到 0
        return (assigned - 1) % 32;
    }

    // ===== Getter（手写） =====

    public boolean isSnowflakeMode() {
        return snowflakeMode;
    }

    /**
     * 生成下一个 ID
     * - 简单模式：直接 Redis INCR 返回 long
     * - Snowflake 模式：委托本地 SnowflakeIdGenerator
     */
    @Override
    public long nextId() {
        if (snowflakeMode) {
            return snowflakeGenerator.nextId();
        }
        Long id = redisTemplate.opsForValue().increment(COUNTER_KEY);
        if (id == null) {
            throw new IllegalStateException("Redis INCR 返回 null，请检查 Redis 连接");
        }
        return id;
    }

    /**
     * 生成 short code 字符串
     * 简单模式：Redis INCR → Base62 编码
     * Snowflake 模式：本地 Snowflake → Base62 编码
     */
    @Override
    public String generateCode() {
        return SnowflakeIdGenerator.encodeBase62(nextId());
    }
}
