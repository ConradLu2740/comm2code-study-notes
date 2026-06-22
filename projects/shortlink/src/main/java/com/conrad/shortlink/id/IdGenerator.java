package com.conrad.shortlink.id;

/**
 * ID 生成器抽象接口
 *
 * 教学点：
 * 1. 把"生成短码"这个动作抽象成接口，是策略模式（Strategy Pattern）的最小应用
 *    - 调用方（ShortLinkService）只依赖 IdGenerator 接口，不知道底层是 Snowflake 还是 Redis
 *    - 切换实现不用改业务代码，符合"对扩展开放、对修改封闭"的开闭原则
 * 2. 接口暴露两个方法：
 *    - nextId(): 暴露原始 long ID（外部如果需要自增序列、时间戳反推等可用）
 *    - generateCode(): 返回 short code 字符串（业务调用方最常用）
 * 3. 一个接口多种实现 + 条件装配（@ConditionalOnProperty）= Spring 推荐的扩展模式
 *    - 默认 Snowflake（无依赖、本地可用、单机性能高）
 *    - 可选 Redis（多实例共享计数器，但要 Redis 可用）
 *
 * 对应学习模块：notes/java/02-oop（接口与多态）+ 06-spring（条件装配）
 */
public interface IdGenerator {

    /**
     * 生成下一个 long 类型 ID
     *
     * @return 全局唯一的 long ID
     */
    long nextId();

    /**
     * 生成 short code 字符串（业务层直接调用这个）
     *
     * @return Base62 编码后的短码，例如 "aB3xY9"
     */
    String generateCode();
}
