package com.conrad.shortlink.domain.controller;

import com.conrad.shortlink.domain.dto.BindDomainRequest;
import com.conrad.shortlink.domain.dto.DomainResponse;
import com.conrad.shortlink.domain.dto.DomainVerifyResponse;
import com.conrad.shortlink.domain.entity.CustomDomain;
import com.conrad.shortlink.domain.service.DomainService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import java.util.Map;

/**
 * 自定义域名 REST API
 *
 * 教学点：
 * 1. 路径前缀 /api/v1/domains 与项目里 ShortLinkController 的 /api/v1/shortlinks 保持一致
 * 2. userId 当前用 @RequestParam 模拟"已登录用户"——真实项目应该从 SecurityContext 取
 *    （项目里已有 spring-security-crypto 依赖，未来可以扩展）
 * 3. 状态码语义：
 *    - 201 Created：POST 成功创建
 *    - 200 OK：GET/POST 通用成功
 *    - 204 No Content：DELETE 成功（按 REST 惯例 DELETE 不返回 body）
 *
 * 对应学习模块：notes/java/08-spring (REST API)
 */
@RestController
@RequestMapping("/api/v1/domains")
public class DomainController {

    private final DomainService service;

    public DomainController(DomainService service) {
        this.service = service;
    }

    /**
     * 绑定域名
     * POST /api/v1/domains?userId=1
     * Body: { "domain": "s.example.com", "shortCode": "abc123" }
     */
    @PostMapping
    public ResponseEntity<DomainResponse> bind(@Valid @RequestBody BindDomainRequest request,
                                                @RequestParam Long userId) {
        CustomDomain entity = service.bindDomain(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED)
                             .body(DomainResponse.fromEntity(entity));
    }

    /**
     * 当前用户的所有域名
     * GET /api/v1/domains?userId=1
     */
    @GetMapping
    public ResponseEntity<List<DomainResponse>> list(@RequestParam Long userId) {
        List<DomainResponse> result = service.findByUserId(userId).stream()
            .map(DomainResponse::fromEntity)
            .toList();
        return ResponseEntity.ok(result);
    }

    /**
     * 触发域名所有权验证
     * POST /api/v1/domains/s.example.com/verify
     */
    @PostMapping("/{domain}/verify")
    public ResponseEntity<DomainVerifyResponse> verify(@PathVariable String domain) {
        DomainVerifyResponse response = service.verifyDomain(domain);
        return ResponseEntity.ok(response);
    }

    /**
     * 解绑
     * DELETE /api/v1/domains/{id}?userId=1
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> unbind(@PathVariable Long id, @RequestParam Long userId) {
        service.unbind(id, userId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 查域名对应的短码（短链重定向流程会用到）
     * GET /api/v1/domains/{domain}/short-code
     * 返回：{ "shortCode": "abc123" }
     */
    @GetMapping("/{domain}/short-code")
    public ResponseEntity<Map<String, String>> getShortCode(@PathVariable String domain) {
        return service.findActiveByDomain(domain)
            .map(d -> ResponseEntity.ok(Map.of("shortCode", d.getShortCode())))
            .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                                            .body(Map.of("error", "域名未绑定或未验证")));
    }
}
