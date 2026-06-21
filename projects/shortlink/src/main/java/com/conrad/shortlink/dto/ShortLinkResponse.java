package com.conrad.shortlink.dto;

import com.conrad.shortlink.entity.ShortLink;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

/**
 * 短链接响应 DTO
 *
 * 教学点：
 * 1. @JsonInclude(JsonInclude.Include.NON_NULL)：序列化时跳过 null 字段
 *    - 让 expireAt=null 时不返回该字段，API 更干净
 * 2. fromEntity 是工厂方法模式，封装 Entity → DTO 的转换逻辑
 *
 * 对应学习模块：notes/java/12-patterns (工厂方法)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ShortLinkResponse {

    private String shortCode;
    private String shortUrl;
    private String longUrl;
    private Instant createdAt;
    private Instant expireAt;
    private Long clickCount;

    public ShortLinkResponse() {}

    public static ShortLinkResponse fromEntity(ShortLink entity, String baseUrl) {
        ShortLinkResponse r = new ShortLinkResponse();
        r.shortCode = entity.getShortCode();
        r.shortUrl = baseUrl + "/" + entity.getShortCode();
        r.longUrl = entity.getLongUrl();
        r.createdAt = entity.getCreatedAt();
        r.expireAt = entity.getExpireAt();
        r.clickCount = entity.getClickCount();
        return r;
    }

    public String getShortCode() { return shortCode; }
    public void setShortCode(String shortCode) { this.shortCode = shortCode; }

    public String getShortUrl() { return shortUrl; }
    public void setShortUrl(String shortUrl) { this.shortUrl = shortUrl; }

    public String getLongUrl() { return longUrl; }
    public void setLongUrl(String longUrl) { this.longUrl = longUrl; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getExpireAt() { return expireAt; }
    public void setExpireAt(Instant expireAt) { this.expireAt = expireAt; }

    public Long getClickCount() { return clickCount; }
    public void setClickCount(Long clickCount) { this.clickCount = clickCount; }
}
