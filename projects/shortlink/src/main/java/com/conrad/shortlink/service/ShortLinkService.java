package com.conrad.shortlink.service;

import com.conrad.shortlink.entity.ShortLink;
import com.conrad.shortlink.exception.ShortLinkNotFoundException;
import com.conrad.shortlink.id.IdGenerator;
import com.conrad.shortlink.repository.ShortLinkRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 短链接业务核心
 *
 * 教学点：
 * 1. 依赖 IdGenerator 接口（策略模式），不关心底层是 Snowflake 还是 Redis
 *    - 切换实现只改 application.yml 里的 shortlink.short-code.id-generator
 * 2. 构造器注入：final 字段 + 构造器，比 @Autowired 字段注入更安全
 * 3. @Transactional 事务管理
 * 4. Cache-Aside 模式：先查缓存，再查 DB，写回缓存
 *
 * 对应学习模块：notes/java/08-spring + 10-redis + id/IdGenerator
 */
@Service
public class ShortLinkService {

    private static final Logger log = LoggerFactory.getLogger(ShortLinkService.class);
    private static final String CACHE_KEY_PREFIX = "shortlink:code:";

    private final ShortLinkRepository repository;
    private final IdGenerator idGenerator;
    private final CacheService cache;

    @Value("${shortlink.cache.ttl-seconds:2592000}")
    private long cacheTtlSeconds;

    public ShortLinkService(ShortLinkRepository repository,
                             IdGenerator idGenerator,
                             CacheService cache) {
        this.repository = repository;
        this.idGenerator = idGenerator;
        this.cache = cache;
    }

    /**
     * 创建短链接
     */
    @Transactional
    public ShortLink createShortLink(String longUrl, String alias) {
        String shortCode;

        if (alias != null && !alias.isBlank()) {
            if (repository.existsByShortCode(alias)) {
                throw new IllegalArgumentException("自定义短码已被占用: " + alias);
            }
            shortCode = alias;
        } else {
            shortCode = idGenerator.generateCode();
        }

        ShortLink entity = new ShortLink(shortCode, longUrl, null);
        try {
            ShortLink saved = repository.save(entity);
            cache.set(CACHE_KEY_PREFIX + saved.getShortCode(), saved.getLongUrl(), cacheTtlSeconds);
            log.info("创建短链接: {} -> {}", saved.getShortCode(), saved.getLongUrl());
            return saved;
        } catch (DataIntegrityViolationException e) {
            log.warn("短码冲突，重试: {}", shortCode);
            throw new IllegalArgumentException("短码生成冲突，请重试");
        }
    }

    /**
     * 解析短码 → 长链接
     */
    public String resolve(String shortCode) {
        String cacheKey = CACHE_KEY_PREFIX + shortCode;

        String cached = cache.get(cacheKey);
        if (cached != null) {
            log.debug("缓存命中: {}", shortCode);
            return cached;
        }

        ShortLink entity = repository.findByShortCode(shortCode)
            .orElseThrow(() -> new ShortLinkNotFoundException(shortCode));

        if (entity.isExpired()) {
            throw new ShortLinkNotFoundException(shortCode);
        }

        cache.set(cacheKey, entity.getLongUrl(), cacheTtlSeconds);

        return entity.getLongUrl();
    }

    /**
     * 记录访问
     */
    @Transactional
    public void recordAccess(String shortCode) {
        try {
            repository.incrementClickCount(shortCode);
        } catch (Exception e) {
            log.warn("点击数自增失败: {}", shortCode, e);
        }
    }

    /**
     * 获取详情
     */
    public ShortLink getInfo(String shortCode) {
        return repository.findByShortCode(shortCode)
            .orElseThrow(() -> new ShortLinkNotFoundException(shortCode));
    }
}
