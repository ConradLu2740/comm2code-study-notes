package com.conrad.shortlink.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 自定义短链域名实体
 *
 * 教学点：
 * 1. 多租户（multi-tenant）设计：通过 userId 字段做"软外键"实现数据隔离
 *    - 没有用真正的数据库外键（@ManyToOne），是因为短链服务和用户服务可能部署在不同实例
 *    - 实际生产应该同步 userId 的有效性（消息队列 / 定期校验）
 * 2. 状态机（state machine）：PENDING → ACTIVE → REVOKED
 *    - PENDING：用户提交了绑定，等待 DNS 所有权验证
 *    - ACTIVE：验证通过，域名可用于短链跳转
 *    - REVOKED：用户主动解绑或管理员禁用（软删除，不物理删除以便审计）
 * 3. @Enumerated(EnumType.STRING)：枚举在数据库存字符串而不是序号
 *    - 避免新增枚举值时老数据错位
 * 4. unique index on domain：保证一个域名只能被一个用户绑定
 * 5. verifyToken：用于 DNS TXT 验证的随机令牌（防止他人抢注你的域名）
 *
 * 对应学习模块：notes/java/08-spring (JPA) + 09-multi-tenant
 */
@Entity
@Table(name = "custom_domain", indexes = {
    @Index(name = "idx_domain_unique", columnList = "domain", unique = true),
    @Index(name = "idx_user_id", columnList = "user_id")
})
public class CustomDomain {

    /**
     * 域名状态枚举
     *
     * 教学点：状态机模式的字段必须是 enum 或受控字典，
     * 禁止用 int 0/1/2（魔法数字）防止误读。
     */
    public enum Status {
        /** 已绑定，等待 DNS TXT 验证 */
        PENDING,
        /** 验证通过，可用于短链服务 */
        ACTIVE,
        /** 已解绑或被禁用（软删除） */
        REVOKED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 所属用户 ID（多租户隔离键） */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 完整域名，例如 s.example.com */
    @Column(name = "domain", length = 253, nullable = false, unique = true)
    private String domain;

    /** 关联到 short_link 表的 short_code（逻辑外键，不加数据库 FK 约束） */
    @Column(name = "short_code", length = 16, nullable = false)
    private String shortCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 16, nullable = false)
    private Status status;

    /** DNS TXT 验证令牌（用户需在域名解析中添加 _shortlink-verify.domain.com TXT=token） */
    @Column(name = "verify_token", length = 64, nullable = false)
    private String verifyToken;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    public CustomDomain() {}

    public CustomDomain(Long userId, String domain, String shortCode, String verifyToken) {
        this.userId = userId;
        this.domain = domain;
        this.shortCode = shortCode;
        this.verifyToken = verifyToken;
        this.status = Status.PENDING;
        this.createdAt = Instant.now();
    }

    // ===== 业务方法 =====

    /**
     * 标记为已验证
     */
    public void markVerified() {
        this.status = Status.ACTIVE;
        this.verifiedAt = Instant.now();
    }

    /**
     * 标记为已解绑
     */
    public void markRevoked() {
        this.status = Status.REVOKED;
    }

    /**
     * 是否处于可用状态
     */
    public boolean isUsable() {
        return this.status == Status.ACTIVE;
    }

    // ===== Getters / Setters =====

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }

    public String getShortCode() { return shortCode; }
    public void setShortCode(String shortCode) { this.shortCode = shortCode; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public String getVerifyToken() { return verifyToken; }
    public void setVerifyToken(String verifyToken) { this.verifyToken = verifyToken; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getVerifiedAt() { return verifiedAt; }
    public void setVerifiedAt(Instant verifiedAt) { this.verifiedAt = verifiedAt; }
}
