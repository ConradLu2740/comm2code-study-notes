package com.conrad.shortlink.domain.dto;

import com.conrad.shortlink.domain.entity.CustomDomain;
import java.time.Instant;

/**
 * 自定义域名响应 DTO
 *
 * 教学点：
 * 1. DTO 与 Entity 解耦：Entity 不应该直接返回给前端
 *    - Entity 可能包含敏感字段（如 verifyToken 不应该暴露给前端）
 *    - DTO 可以只暴露需要的字段，并做格式化
 * 2. 静态工厂方法 fromEntity()：聚合 Entity → DTO 的转换逻辑
 *    - 比在 Controller 里手写转换更易测试
 * 3. 注意 verifyToken 字段没有暴露给前端（安全考虑，前端只需要知道"已绑定"或"待验证"）
 */
public class DomainResponse {

    private Long id;
    private String domain;
    private String shortCode;
    private String status;
    private Instant createdAt;
    private Instant verifiedAt;

    public DomainResponse() {}

    /**
     * Entity → DTO 转换
     *
     * 教学点：为什么不用 Lombok @RequiredArgsConstructor？
     * 因为本项目 Lombok 不可用，所有构造器/getter/setter 手写。
     */
    public static DomainResponse fromEntity(CustomDomain entity) {
        DomainResponse dto = new DomainResponse();
        dto.id = entity.getId();
        dto.domain = entity.getDomain();
        dto.shortCode = entity.getShortCode();
        dto.status = entity.getStatus().name();
        dto.createdAt = entity.getCreatedAt();
        dto.verifiedAt = entity.getVerifiedAt();
        return dto;
    }

    // ===== Getters / Setters =====

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }

    public String getShortCode() { return shortCode; }
    public void setShortCode(String shortCode) { this.shortCode = shortCode; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getVerifiedAt() { return verifiedAt; }
    public void setVerifiedAt(Instant verifiedAt) { this.verifiedAt = verifiedAt; }
}
