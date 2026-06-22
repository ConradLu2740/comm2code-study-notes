package com.conrad.shortlink.domain.service;

import com.conrad.shortlink.domain.dto.BindDomainRequest;
import com.conrad.shortlink.domain.dto.DomainVerifyResponse;
import com.conrad.shortlink.domain.entity.CustomDomain;
import com.conrad.shortlink.domain.repository.CustomDomainRepository;
import com.conrad.shortlink.repository.ShortLinkRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 自定义域名业务核心
 *
 * 教学点：
 * 1. 多租户权限校验：unbind/findByUser 必须校验 userId，防止越权访问
 * 2. 状态机转移：bind→PENDING，verify→ACTIVE，unbind→REVOKED
 *    - 状态变更走 entity 内部方法（markVerified/markRevoked），不暴露 setter
 *    - 这是"封装状态机"的标准做法（rich domain model）
 * 3. DNS TXT 验证原理：用 java.net.InetAddress 模拟（标准库无 TXT 查询 API）
 *    - 真实生产用 dnsjava / Vertx DNS 查 TXT 记录值匹配 token
 *    - 这里用 devMode 开关简化：dev=true 时跳过实际 DNS 查询，直接通过验证
 *    - 这样本地开发不用真的去 DNS 服务商配置 TXT 记录
 * 4. 软外键校验：shortCode 必须存在于 short_link 表，否则拒绝绑定
 *
 * 对应学习模块：notes/java/08-spring + 09-multi-tenant + 14-dns
 */
@Service
public class DomainService {

    private static final Logger log = LoggerFactory.getLogger(DomainService.class);

    /** DNS TXT 主机记录前缀（业界惯例：_前缀表示特殊用途的子域名） */
    private static final String TXT_HOST_PREFIX = "_shortlink-verify.";

    /** 默认 DNS 重试冷却时间（DNS 缓存通常 30s-5min，保守取 60s） */
    private static final long RETRY_COOLDOWN_SECONDS = 60;

    private final CustomDomainRepository domainRepository;
    private final ShortLinkRepository shortLinkRepository;

    /** 开发模式：跳过实际 DNS 查询，直接通过验证（默认 true） */
    @Value("${shortlink.domain.dev-mode:true}")
    private boolean devMode;

    public DomainService(CustomDomainRepository domainRepository,
                          ShortLinkRepository shortLinkRepository) {
        this.domainRepository = domainRepository;
        this.shortLinkRepository = shortLinkRepository;
    }

    // ===== 业务方法 =====

    /**
     * 绑定域名到短码
     * 流程：校验 shortCode 存在 → 校验域名未占用 → 生成 token → 保存 PENDING 状态
     */
    @Transactional
    public CustomDomain bindDomain(BindDomainRequest request, Long userId) {
        // 1. 校验 shortCode 存在（软外键校验）
        if (!shortLinkRepository.existsByShortCode(request.getShortCode())) {
            throw new IllegalArgumentException(
                "短码不存在，请先创建短链接: " + request.getShortCode());
        }

        // 2. 校验域名未被占用
        String normalizedDomain = normalizeDomain(request.getDomain());
        if (domainRepository.existsByDomain(normalizedDomain)) {
            throw new IllegalStateException("域名已被其他用户绑定: " + normalizedDomain);
        }

        // 3. 生成验证 token（UUID 去掉横线，32 位字符）
        String token = generateVerifyToken();

        // 4. 创建实体（PENDING 状态）
        CustomDomain entity = new CustomDomain(userId, normalizedDomain,
                                               request.getShortCode(), token);
        CustomDomain saved = domainRepository.save(entity);
        log.info("域名绑定成功: user={}, domain={}, shortCode={}, status=PENDING",
                 userId, normalizedDomain, request.getShortCode());
        return saved;
    }

    /**
     * 验证域名所有权（DNS TXT 检查）
     *
     * 教学点：返回 DomainVerifyResponse 而不是 boolean，
     * 让前端能直接展示"还需添加的 DNS 记录"给用户。
     */
    @Transactional
    public DomainVerifyResponse verifyDomain(String domain) {
        String normalizedDomain = normalizeDomain(domain);
        CustomDomain entity = domainRepository.findByDomain(normalizedDomain)
            .orElseThrow(() -> new IllegalArgumentException("域名未绑定: " + normalizedDomain));

        String txtHost = TXT_HOST_PREFIX + normalizedDomain;
        boolean verified;

        if (devMode) {
            // 教学点：dev 模式直接通过，避免每次本地测试都要去改 DNS
            verified = true;
            log.info("[devMode] 跳过 DNS 查询，直接验证通过: {}", normalizedDomain);
        } else {
            verified = performDnsLookup(txtHost, entity.getVerifyToken());
        }

        if (verified) {
            entity.markVerified();
            domainRepository.save(entity);
            log.info("域名验证通过: {}", normalizedDomain);
            return new DomainVerifyResponse(
                normalizedDomain,
                entity.getStatus().name(),
                "验证成功！现在可以用 " + normalizedDomain + " 访问你的短链。",
                txtHost,
                entity.getVerifyToken(),
                entity.getVerifiedAt(),
                null
            );
        }

        return new DomainVerifyResponse(
            normalizedDomain,
            entity.getStatus().name(),
            "验证失败：请确认 DNS TXT 记录已正确添加（DNS 缓存最多需要 5 分钟生效）。",
            txtHost,
            entity.getVerifyToken(),
            null,
            Instant.now().plus(RETRY_COOLDOWN_SECONDS, ChronoUnit.SECONDS)
        );
    }

    /**
     * 短链重定向时按域名查短码
     * 只返回 ACTIVE 状态的记录（PENDING/REVOKED 不应被实际服务使用）
     */
    public Optional<CustomDomain> findActiveByDomain(String domain) {
        String normalizedDomain = normalizeDomain(domain);
        return domainRepository.findByDomain(normalizedDomain)
            .filter(CustomDomain::isUsable);
    }

    /**
     * 查某用户的所有域名（"我的域名"页面）
     */
    public List<CustomDomain> findByUserId(Long userId) {
        return domainRepository.findByUserId(userId);
    }

    /**
     * 解绑（软删除：状态置为 REVOKED 而非物理删除）
     *
     * 教学点：权限校验防止越权——任何 API 都不能假设"前端传过来的 ID 就是当前用户的"。
     * 即使用户绕过前端直接调 API，也必须在校验通过后才能操作。
     */
    @Transactional
    public void unbind(Long id, Long userId) {
        CustomDomain entity = domainRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("域名记录不存在: id=" + id));

        // 越权保护
        if (!entity.getUserId().equals(userId)) {
            log.warn("越权解绑尝试: id={}, 实际owner={}, 尝试user={}",
                     id, entity.getUserId(), userId);
            throw new SecurityException("无权操作该域名记录");
        }

        entity.markRevoked();
        domainRepository.save(entity);
        log.info("域名解绑成功: id={}, domain={}", id, entity.getDomain());
    }

    // ===== 私有辅助方法 =====

    /**
     * 域名规范化：小写 + 去前后空格
     * 教学点：域名大小写不敏感，存储前必须统一，否则 s.example.com 和 S.EXAMPLE.COM
     * 会被当成两条记录。
     */
    private String normalizeDomain(String domain) {
        return domain.trim().toLowerCase();
    }

    /**
     * 生成验证 token：UUID 去横线后取前 32 位
     */
    private String generateVerifyToken() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 32);
    }

    /**
     * 实际 DNS 查询（简化版）
     *
     * 教学点：JDK 标准库 InetAddress 只支持 A/AAAA 记录查询，不支持 TXT。
     * 这里用"主机名能否解析"作为简化判断；生产环境应该引入 dnsjava 库查 TXT。
     *
     * @param txtHost 期望的 TXT 主机名（如 _shortlink-verify.example.com）
     * @param expectedToken 期望的 TXT 记录值（生产用 dnsjava 比对）
     * @return true=验证通过
     */
    private boolean performDnsLookup(String txtHost, String expectedToken) {
        try {
            // 真实生产应该用 dnsjava：
            //   new Lookup(txtHost, Type.TXT).run() → 拿到 TXT 字符串列表 → contains(expectedToken)
            // JDK InetAddress 只能查 A 记录，所以这里只能判断"能否解析"
            InetAddress addr = InetAddress.getByName(txtHost);
            log.debug("DNS 查询成功: {} -> {}", txtHost, addr.getHostAddress());
            return addr.getHostAddress() != null;
        } catch (UnknownHostException e) {
            log.info("DNS 查询失败（用户尚未添加 TXT 记录）: {}", txtHost);
            return false;
        }
    }
}
