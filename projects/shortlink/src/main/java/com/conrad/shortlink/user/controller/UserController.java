package com.conrad.shortlink.user.controller;

import com.conrad.shortlink.user.dto.UserResponse;
import com.conrad.shortlink.user.entity.User;
import com.conrad.shortlink.user.repository.UserRepository;
import com.conrad.shortlink.user.security.JwtAuthFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.UNAUTHORIZED;

/**
 * 用户管理 REST API（需要鉴权）
 *
 * 教学点（鉴权架构演进）：
 *
 * ┌────────────────────────────────────────────────────────────┐
 * │ 当前状态（基础版）：                                       │
 * │   - JwtAuthFilter 已识别用户身份（写入 request attribute）│
 * │   - Controller 自己从 request 取 username 并查 DB         │
 * │   - 没带 token → 直接抛 401                               │
 * │                                                            │
 * │ 未来升级（标准 Spring Security）：                          │
 * │   - 加 SecurityConfig，配置 /api/v1/users/** 需要鉴权     │
 * │   - 用 Spring Security 的 Authentication 机制             │
 * │   - Controller 注入 @AuthenticationPrincipal UserDetails  │
 * │                                                            │
 * │ 当前简化版的理由：                                         │
 * │   - 项目里没有用 spring-boot-starter-security 全家桶      │
 * │   - 只引入了 spring-security-crypto（仅 BCrypt 工具）      │
 * │   - 加全家族会引入大量配置，对学习负担太大                 │
 * │   - 鉴权逻辑保持在 Filter + Controller 两层可读性更高      │
 * └────────────────────────────────────────────────────────────┘
 *
 * 对应学习模块：notes/java/13-security
 */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserRepository repository;

    public UserController(UserRepository repository) {
        this.repository = repository;
    }

    /**
     * 获取当前登录用户的信息
     * GET /api/v1/users/me
     * Header: Authorization: Bearer <token>
     * 成功：200 OK + UserResponse
     * 失败：401 Unauthorized（没带 token / token 无效）
     */
    @GetMapping("/me")
    public ResponseEntity<UserResponse> me(HttpServletRequest request) {
        // 1. 从 Filter 写入的 attribute 取 username
        String username = (String) request.getAttribute(JwtAuthFilter.CURRENT_USERNAME_ATTR);
        if (username == null) {
            // 没带 token 或 token 无效（Filter 已经校验过）
            // 教学点：401 = Unauthorized（未认证），不是 403 Forbidden（无权限）
            throw new ResponseStatusException(UNAUTHORIZED, "请先登录");
        }

        // 2. 查 DB
        User user = repository.findByUsername(username)
            .orElseThrow(() -> new ResponseStatusException(UNAUTHORIZED, "用户不存在"));

        // 3. 返回 DTO（不暴露 passwordHash）
        return ResponseEntity.ok(UserResponse.fromEntity(user));
    }
}
