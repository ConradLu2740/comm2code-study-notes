package com.conrad.shortlink.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/**
 * 创建短链接请求 DTO
 *
 * 教学点：
 * 1. DTO（Data Transfer Object）是 Controller 和 Service 之间的数据传输对象
 * 2. 区别于 Entity：DTO 是面向 API 的，Entity 是面向数据库的
 * 3. @NotBlank / @Size / @Pattern 是 Bean Validation 注解，配合 @Valid 使用
 *
 * 对应学习模块：notes/java/08-spring
 */
public class CreateShortLinkRequest {

    @NotBlank(message = "长链接不能为空")
    @Size(max = 2048, message = "长链接长度不能超过 2048")
    @Pattern(regexp = "^https?://.*", message = "长链接必须以 http:// 或 https:// 开头")
    private String longUrl;

    /**
     * 可选的自定义短码：3-16 位字母数字
     */
    @Pattern(regexp = "^[A-Za-z0-9_-]{3,16}$",
             message = "自定义短码只能包含字母、数字、下划线、连字符，长度 3-16")
    private String alias;

    /**
     * 可选的过期时间（ISO-8601 格式：2026-12-31T23:59:59Z）
     */
    private Instant expireAt;

    public CreateShortLinkRequest() {}

    public String getLongUrl() { return longUrl; }
    public void setLongUrl(String longUrl) { this.longUrl = longUrl; }

    public String getAlias() { return alias; }
    public void setAlias(String alias) { this.alias = alias; }

    public Instant getExpireAt() { return expireAt; }
    public void setExpireAt(Instant expireAt) { this.expireAt = expireAt; }
}
