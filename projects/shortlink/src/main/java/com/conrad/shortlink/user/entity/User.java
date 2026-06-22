package com.conrad.shortlink.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 用户实体
 *
 * 教学点：
 * 1. @Entity 标记 JPA 实体，对应数据库表 user
 * 2. @Table 用 indexes 同时声明 username 和 email 的唯一索引
 *    - 唯一索引确保数据库层面不允许重复（应用层校验只是体验优化）
 *    - 登录查询走索引，O(log n)
 * 3. @PrePersist / @PreUpdate 钩子自动维护 createdAt / updatedAt
 *    - 比在 Service 里手动 set 更可靠（任何路径 save 都会触发）
 * 4. passwordHash：永远不存明文密码
 *    - BCrypt 每次加密结果都不一样（自带 salt），所以无需单独存 salt 字段
 * 5. 用 Instant 而不是 Date：Java 8+ 推荐的时间类型（不可变、线程安全）
 *
 * 对应学习模块：notes/java/08-spring (JPA)
 */
@Entity
@Table(name = "users", indexes = {
    @Index(name = "idx_user_username", columnList = "username", unique = true),
    @Index(name = "idx_user_email", columnList = "email", unique = true)
})
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "username", length = 64, nullable = false, unique = true)
    private String username;

    @Column(name = "email", length = 128, nullable = false, unique = true)
    private String email;

    /**
     * BCrypt 加密后的密码哈希（60 字节固定长度）
     * 教学点：BCrypt 格式：$2a$10$<22-char-salt><31-char-hash>
     */
    @Column(name = "password_hash", length = 60, nullable = false)
    private String passwordHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public User() {}

    public User(String username, String email, String passwordHash) {
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
    }

    /**
     * 持久化前自动设 createdAt 和 updatedAt
     * 教学点：JPA 生命周期回调，@PrePersist 在 INSERT 之前调用
     */
    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    /**
     * 更新前自动刷新 updatedAt
     */
    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    // ===== Getters / Setters =====

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
