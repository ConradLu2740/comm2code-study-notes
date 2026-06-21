package com.conrad.shortlink.controller;

import com.conrad.shortlink.dto.CreateShortLinkRequest;
import com.conrad.shortlink.dto.ShortLinkResponse;
import com.conrad.shortlink.entity.ShortLink;
import com.conrad.shortlink.exception.RateLimitExceededException;
import com.conrad.shortlink.service.RateLimiter;
import com.conrad.shortlink.service.ShortLinkService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 短链接 REST API
 *
 * 教学点：
 * 1. @RestController = @Controller + @ResponseBody，返回 JSON
 * 2. @RequestMapping("/api/v1/shortlinks") 统一前缀
 * 3. @Valid 触发参数校验
 * 4. ResponseEntity 包装响应（状态码 + 头 + 体）
 * 5. 限流在 Controller 层做，按 IP 限流
 *
 * 对应学习模块：notes/java/08-spring
 */
@RestController
@RequestMapping("/api/v1/shortlinks")
public class ShortLinkController {

    private final ShortLinkService service;
    private final RateLimiter rateLimiter;

    @Value("${shortlink.base-url:http://localhost:8080}")
    private String baseUrl;

    public ShortLinkController(ShortLinkService service, RateLimiter rateLimiter) {
        this.service = service;
        this.rateLimiter = rateLimiter;
    }

    /**
     * 创建短链接
     * POST /api/v1/shortlinks
     * Body: { "longUrl": "https://...", "alias": "optional" }
     */
    @PostMapping
    public ResponseEntity<ShortLinkResponse> create(
            @Valid @RequestBody CreateShortLinkRequest request,
            HttpServletRequest httpRequest) {
        // 限流：按 IP 限，每 IP 每秒最多 rate 个请求
        String clientIp = getClientIp(httpRequest);
        if (!rateLimiter.tryAcquire("create:" + clientIp)) {
            throw new RateLimitExceededException();
        }

        ShortLink entity = service.createShortLink(request.getLongUrl(), request.getAlias());
        ShortLinkResponse response = ShortLinkResponse.fromEntity(entity, baseUrl);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * 获取短链接详情
     * GET /api/v1/shortlinks/{shortCode}
     */
    @GetMapping("/{shortCode}")
    public ResponseEntity<ShortLinkResponse> getInfo(@PathVariable String shortCode) {
        ShortLink entity = service.getInfo(shortCode);
        return ResponseEntity.ok(ShortLinkResponse.fromEntity(entity, baseUrl));
    }

    /**
     * 获取客户端 IP（兼容反向代理场景）
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isBlank()) {
            ip = request.getRemoteAddr();
        }
        // 多个代理时取第一个
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip == null ? "unknown" : ip;
    }
}
