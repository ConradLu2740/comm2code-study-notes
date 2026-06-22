package com.conrad.shortlink.id;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * IdGenerator 配置 - 通过属性切换实现
 *
 * 教学点：
 * 1. Spring 条件装配（Conditional Configuration）的标准用法：
 *    - @Configuration 声明这是一个配置类（相当于 XML 配置的 <beans>）
 *    - @ConditionalOnProperty 根据 application.yml/properties 里的 key 决定是否加载
 *    - @ConditionalOnMissingBean 保证 Spring 容器里只有一个 IdGenerator Bean
 * 2. 三种互斥的实现：
 *    - Snowflake（默认）：matchIfMissing=true，无 Redis 依赖也能跑
 *    - Redis 简单模式：shortlink.short-code.id-generator=redis-simple
 *    - Redis + Snowflake 模式：shortlink.short-code.id-generator=redis-snowflake
 * 3. application.yml 切换示例：
 *    <pre>
 *    shortlink:
 *      short-code:
 *        id-generator: snowflake   # 默认
 *        # id-generator: redis-simple   # 启用 Redis 简单模式
 *        # id-generator: redis-snowflake # 启用 Redis 分配 workerId 的 Snowflake
 *    </pre>
 * 4. 这种"接口 + 多实现 + 条件装配"是 Spring Boot Starter 的标准扩展模式
 *    - 比如 spring-boot-starter-data-redis 内部就是用 ConditionalOnClass 决定加载 Jedis 还是 Lettuce
 *
 * 对应学习模块：notes/java/06-spring（条件装配、自动配置）
 */
@Configuration
public class IdGeneratorConfig {

    /**
     * 默认实现：Snowflake
     *
     * 教学点：matchIfMissing = true 表示配置文件里没写 id-generator 时也加载这个
     * - 这样老业务不配配置也能跑，符合"约定优于配置"
     * - @ConditionalOnMissingBean 保证如果用户自己定义了 IdGenerator，这里就不加载
     */
    @Bean
    @ConditionalOnMissingBean(IdGenerator.class)
    @ConditionalOnProperty(
        name = "shortlink.short-code.id-generator",
        havingValue = "snowflake",
        matchIfMissing = true
    )
    public IdGenerator snowflakeIdGenerator() {
        return new SnowflakeIdGenerator();
    }

    /**
     * Redis 简单 INCR 模式
     *
     * 教学点：
     * - @ConditionalOnBean(StringRedisTemplate.class) 保证只有当 Spring 容器里存在 StringRedisTemplate 时才加载
     *   - 默认 profile（短链项目）没有 Redis，这个 Bean 不会被创建
     *   - prod profile（短链项目）启用了 Redis 配置后才会创建
     * - 这样默认启动不会因为缺 Redis 而报错
     */
    @Bean
    @ConditionalOnMissingBean(IdGenerator.class)
    @ConditionalOnBean(StringRedisTemplate.class)
    @ConditionalOnProperty(
        name = "shortlink.short-code.id-generator",
        havingValue = "redis-simple"
    )
    public IdGenerator redisSimpleIdGenerator(StringRedisTemplate stringRedisTemplate) {
        return new RedisIdGenerator(stringRedisTemplate);
    }

    /**
     * Redis + Snowflake 模式：workerId 从 Redis 动态分配
     *
     * 教学点：
     * - 这是最接近生产级的方案：本地 Snowflake 生成（高性能）+ Redis 分配 workerId（分布式协调）
     * - 比纯 Redis INCR 模式快 100x（无 RTT），比纯 Snowflake 模式 workerId 不冲突
     * - 真实生产还要给 workerId 加续约机制（每 30s 续约一次，60s 没续约视为下线回收）
     */
    @Bean
    @ConditionalOnMissingBean(IdGenerator.class)
    @ConditionalOnBean(StringRedisTemplate.class)
    @ConditionalOnProperty(
        name = "shortlink.short-code.id-generator",
        havingValue = "redis-snowflake"
    )
    public IdGenerator redisSnowflakeIdGenerator(StringRedisTemplate stringRedisTemplate) {
        // datacenterId 固定为 1（教学项目简化）
        // 真实生产应该从 application.yml 读取
        return new RedisIdGenerator(stringRedisTemplate, 1L);
    }
}
