package com.conrad.shortlink.service;

/**
 * 缓存抽象接口
 *
 * 教学点：
 * 1. 用接口隔离实现，配置切换不影响业务代码
 * 2. 默认实现是内存版（ConcurrentHashMap），启动即用
 * 3. 生产环境切换 Redis 版，零业务代码改动
 *
 * 对应学习模块：notes/java/10-redis (缓存模式)
 */
public interface CacheService {

    /**
     * 获取缓存
     * @param key 键
     * @return 值，不存在返回 null
     */
    String get(String key);

    /**
     * 设置缓存（带过期时间）
     * @param key 键
     * @param value 值
     * @param ttlSeconds 过期秒数
     */
    void set(String key, String value, long ttlSeconds);

    /**
     * 删除缓存
     */
    void delete(String key);

    /**
     * 检查 key 是否存在
     */
    boolean hasKey(String key);
}
