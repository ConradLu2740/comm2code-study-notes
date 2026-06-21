package com.conrad.shortlink.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存版缓存服务 - 默认实现
 *
 * 教学点：
 * 1. 用 ConcurrentHashMap 保证线程安全
 * 2. @ConditionalOnProperty：只在 cache.type != redis 时加载
 * 3. 简单 TTL 实现：get 时检查过期，懒清理
 *    - 生产应该用定期清理（@Scheduled）或 Caffeine 库
 *
 * 对应学习模块：notes/java/03-collections (ConcurrentHashMap)
 */
@Service
@ConditionalOnProperty(name = "shortlink.cache.type", havingValue = "memory", matchIfMissing = true)
public class InMemoryCacheService implements CacheService {

    private record CacheEntry(String value, Instant expireAt) {}

    private final Map<String, CacheEntry> store = new ConcurrentHashMap<>();

    @Override
    public String get(String key) {
        CacheEntry entry = store.get(key);
        if (entry == null) return null;
        if (Instant.now().isAfter(entry.expireAt)) {
            store.remove(key);
            return null;
        }
        return entry.value;
    }

    @Override
    public void set(String key, String value, long ttlSeconds) {
        store.put(key, new CacheEntry(value, Instant.now().plusSeconds(ttlSeconds)));
    }

    @Override
    public void delete(String key) {
        store.remove(key);
    }

    @Override
    public boolean hasKey(String key) {
        return get(key) != null;
    }

    /**
     * 当前缓存大小（用于监控 / 测试）
     */
    public int size() {
        return store.size();
    }
}
