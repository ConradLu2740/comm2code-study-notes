package com.conrad.shortlink.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 短链接实体
 *
 * 教学点：
 * 1. @Entity 标记这是 JPA 实体（对应数据库表）
 * 2. @Table(name = "short_link", indexes = {...}) 指定表名和索引
 *    - short_code 必须建唯一索引，查询 O(log n)
 * 3. @Id + @GeneratedValue：主键自增策略
 * 4. @Column：字段映射（length、nullable 等）
 * 5. 使用 Instant 而不是 Date：Java 8+ 推荐的不可变时间类型
 *
 * 对应学习模块：notes/java/08-spring (JPA)
 */
@Entity
@Table(name = "short_link", indexes = {
    @Index(name = "idx_short_code", columnList = "short_code", unique = true)
})
public class ShortLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "short_code", length = 16, nullable = false, unique = true)
    private String shortCode;

    @Column(name = "long_url", length = 2048, nullable = false)
    private String longUrl;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expire_at")
    private Instant expireAt;

    @Column(name = "click_count", nullable = false)
    private Long clickCount = 0L;

    public ShortLink() {}

    public ShortLink(String shortCode, String longUrl, Instant expireAt) {
        this.shortCode = shortCode;
        this.longUrl = longUrl;
        this.expireAt = expireAt;
        this.createdAt = Instant.now();
        this.clickCount = 0L;
    }

    // ===== 业务方法 =====

    public boolean isExpired() {
        return expireAt != null && Instant.now().isAfter(expireAt);
    }

    public void incrementClickCount() {
        this.clickCount++;
    }

    // ===== Getters / Setters =====

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getShortCode() { return shortCode; }
    public void setShortCode(String shortCode) { this.shortCode = shortCode; }

    public String getLongUrl() { return longUrl; }
    public void setLongUrl(String longUrl) { this.longUrl = longUrl; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getExpireAt() { return expireAt; }
    public void setExpireAt(Instant expireAt) { this.expireAt = expireAt; }

    public Long getClickCount() { return clickCount; }
    public void setClickCount(Long clickCount) { this.clickCount = clickCount; }
}
