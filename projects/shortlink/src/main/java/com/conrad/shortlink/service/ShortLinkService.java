package com.conrad.shortlink.service;

import com.conrad.shortlink.entity.ShortLink;
import com.conrad.shortlink.exception.ShortLinkNotFoundException;
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
 * 1. @Service 标记这是业务组件，Spring 自动注册为 Bean
 * 2. 构造器注入：final 字段 + 构造器，比 @Autowired 字段注入更安全
 * 3. @Transactional 事务管理（增删改要加）
 * 4. Cache-Aside 模式：先查缓存，再查 DB，写回缓存
 *
 * 对应学习模块：notes/java/08-spring + 10-redis
 */
@Service
public class ShortLinkService {

    private static final Logger log = LoggerFactory.getLogger(ShortLinkService.class);
    private static final String CACHE_KEY_PREFIX = "shortlink:code:";

    private final ShortLinkRepository repository;
    private final ShortCodeGenerator codeGenerator;
    private final CacheService cache;

    @Value("${shortlink.cache.ttl-seconds:2592000}")
    private long cacheTtlSeconds;

    public ShortLinkService(ShortLinkRepository repository,
                             ShortCodeGenerator codeGenerator,
                             CacheService cache) {
        this.repository = repository;
        this.codeGenerator = codeGenerator;
        this.cache = cache;
    }

    /**
     * 创建短链接
     */
    @Transactional
    public ShortLink createShortLink(String longUrl, String alias) {
        String shortCode;

        if (alias != null && !alias.isBlank()) {
            // 自定义短码：检查是否已存在
            if (repository.existsByShortCode(alias)) {
                throw new IllegalArgumentException("自定义短码已被占用: " + alias);
            }
            shortCode = alias;
        } else {
            // 自动生成短码。冲突概率极低（Snowflake 唯一），但理论上还是要重试
            shortCode = generateUniqueCode();
        }

        ShortLink entity = new ShortLink(shortCode, longUrl, null);
        try {
            ShortLink saved = repository.save(entity);
            // 写入缓存
            cache.set(CACHE_KEY_PREFIX + saved.getShortCode(), saved.getLongUrl(), cacheTtlSeconds);
            log.info("创建短链接: {} -> {}", saved.getShortCode(), saved.getLongUrl());
            return saved;
        } catch (DataIntegrityViolationException e) {
            // 极端情况：唯一约束冲突
            log.warn("短码冲突，重试: {}", shortCode);
            throw new IllegalArgumentException("短码生成冲突，请重试");
        }
    }

    /**
     * 解析短码 → 长链接（核心查询接口）
     */
    public String resolve(String shortCode) {
        String cacheKey = CACHE_KEY_PREFIX + shortCode;

        // 1. 先查缓存
        String cached = cache.get(cacheKey);
        if (cached != null) {
            log.debug("缓存命中: {}", shortCode);
            return cached;
        }

        // 2. 缓存未命中，查数据库
        ShortLink entity = repository.findByShortCode(shortCode)
            .orElseThrow(() -> new ShortLinkNotFoundException(shortCode));

        // 3. 检查过期
        if (entity.isExpired()) {
            throw new ShortLinkNotFoundException(shortCode);
        }

        // 4. 写回缓存
        cache.set(cacheKey, entity.getLongUrl(), cacheTtlSeconds);

        return entity.getLongUrl();
    }

    /**
     * 记录访问（异步思想：这里用同步自增，简单清晰）
     * 教学点：生产环境应该用 MQ 异步 + 批量写库
     */
    @Transactional
    public void recordAccess(String shortCode) {
        try {
            repository.incrementClickCount(shortCode);
        } catch (Exception e) {
            // 统计失败不应该影响主流程
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

    private String generateUniqueCode() {
        // 极端情况下（时间回拨或测试）Snowflake 可能生成冲突
        // 这里简化为单次生成，生产应该带重试
        return codeGenerator.generate();
    }
}
