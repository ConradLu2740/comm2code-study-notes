package com.conrad.shortlink.user.dto;

import com.conrad.shortlink.user.entity.User;
import java.time.Instant;

/**
 * 用户信息响应 DTO（不含密码）
 *
 * 教学点：
 * 1. DTO 与 Entity 分离：Entity 是数据库模型，DTO 是 API 模型
 *    - 即使 Entity 加字段，也不一定会暴露给前端
 *    - passwordHash 等敏感字段永远不进 DTO
 * 2. fromEntity 工厂方法封装转换逻辑
 * 3. @JsonInclude(NON_NULL) 可选：createdAt 等字段不会因为 null 而显示
 *
 * 对应学习模块：notes/java/12-patterns (工厂方法)
 */
public class UserResponse {

    private Long id;
    private String username;
    private String email;
    private Instant createdAt;

    public UserResponse() {}

    public UserResponse(Long id, String username, String email, Instant createdAt) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.createdAt = createdAt;
    }

    /**
     * 工厂方法：Entity → DTO
     * 教学点：永远不要把 Entity 直接序列化给前端
     */
    public static UserResponse fromEntity(User entity) {
        return new UserResponse(
            entity.getId(),
            entity.getUsername(),
            entity.getEmail(),
            entity.getCreatedAt()
        );
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
