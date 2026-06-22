package com.conrad.shortlink.user.controller;

import com.conrad.shortlink.user.dto.LoginRequest;
import com.conrad.shortlink.user.dto.LoginResponse;
import com.conrad.shortlink.user.dto.RegisterRequest;
import com.conrad.shortlink.user.dto.UserResponse;
import com.conrad.shortlink.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证 REST API（注册 + 登录）
 *
 * 教学点：
 * 1. 路径 /api/v1/auth/* 不需要鉴权（否则新人无法注册）
 * 2. POST /register 返回 201 Created（资源创建成功）
 * 3. POST /login 返回 200 OK + JWT
 * 4. @Valid 触发参数校验，失败抛 MethodArgumentNotValidException
 *    - 由 GlobalExceptionHandler 统一处理为 400 JSON
 * 5. 业务异常（用户名已存在等）抛 IllegalArgumentException
 *    - 也由 GlobalExceptionHandler 统一处理为 400 JSON
 * 6. BCryptPasswordEncoder 是 Spring Security Crypto 提供的 Bean
 *    - 需要在配置类里声明 @Bean（这里假设 SecurityConfig 或类似类已提供）
 *    - 如果没有，应用启动会失败 → 提示需要补一个 Config 类
 *
 * 对应学习模块：notes/java/08-spring (REST API)
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    /**
     * 用户注册
     * POST /api/v1/auth/register
     * Body: { "username":"alice", "email":"a@x.com", "password":"secret123" }
     * 成功：201 Created + { "id":1, "username":"alice", ... }
     */
    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        UserResponse response = userService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * 用户登录
     * POST /api/v1/auth/login
     * Body: { "username":"alice", "password":"secret123" }
     * 成功：200 OK + { "token":"eyJ...", "expiresIn":604800, "user":{...} }
     */
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = userService.login(request);
        return ResponseEntity.ok(response);
    }
}
