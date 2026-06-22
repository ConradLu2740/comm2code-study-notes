package com.conrad.shortlink.user.service;

import com.conrad.shortlink.user.dto.LoginRequest;
import com.conrad.shortlink.user.dto.LoginResponse;
import com.conrad.shortlink.user.dto.RegisterRequest;
import com.conrad.shortlink.user.dto.UserResponse;
import com.conrad.shortlink.user.entity.User;
import com.conrad.shortlink.user.repository.UserRepository;
import com.conrad.shortlink.user.security.JwtTokenProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用户业务核心：注册 + 登录
 *
 * 教学点（重点讲 BCrypt）：
 * ┌─────────────────────────────────────────────────────────┐
 * │ BCrypt 密码加密原理                                     │
 * │                                                         │
 * │ 1. 不存明文：DB 只存哈希，泄露 DB ≠ 泄露密码            │
 * │ 2. 自带 salt：每次加密结果不同（防 rainbow table 攻击） │
 * │    哈希格式：$2a$10$<22-char-salt><31-char-hash>        │
 * │    ^   ^   ^                                           │
 * │    |   |   └── salt（22字符）                          │
 * │    |   └────── cost factor（2^10 轮迭代）              │
 * │    └────────── 算法版本（2a = BCrypt）                 │
 * │ 3. 慢哈希：故意设计成 ~100ms/次，防暴力破解            │
 * │ 4. 校验：BCryptPasswordEncoder.matches(raw, hash)       │
 * │    内部从 hash 里提取 salt，重算后比对                  │
 * └─────────────────────────────────────────────────────────┘
 *
 * 对应学习模块：notes/java/13-security (密码哈希)
 */
@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository repository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;

    public UserService(UserRepository repository,
                       BCryptPasswordEncoder passwordEncoder,
                       JwtTokenProvider tokenProvider) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
    }

    /**
     * 注册新用户
     *
     * 流程：查重 → BCrypt 加密 → 存 DB → 返回 DTO
     *
     * @throws IllegalArgumentException 用户名或邮箱已存在
     */
    @Transactional
    public UserResponse register(RegisterRequest request) {
        // 1. 校验唯一性（数据库唯一索引兜底，这里只是给用户友好提示）
        if (repository.existsByUsername(request.getUsername())) {
            throw new IllegalArgumentException("用户名已存在: " + request.getUsername());
        }
        if (repository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("邮箱已存在: " + request.getEmail());
        }

        // 2. BCrypt 加密
        // 教学点：BCryptPasswordEncoder.encode() 内部生成随机 salt 并拼到 hash 里
        String hash = passwordEncoder.encode(request.getPassword());

        // 3. 存 DB（@PrePersist 钩子会自动设 createdAt / updatedAt）
        User entity = new User(request.getUsername(), request.getEmail(), hash);
        User saved = repository.save(entity);

        log.info("用户注册成功: id={}, username={}", saved.getId(), saved.getUsername());
        return UserResponse.fromEntity(saved);
    }

    /**
     * 用户登录
     *
     * 流程：按用户名查 → BCrypt 验密码 → 签发 JWT → 返回 LoginResponse
     *
     * @throws IllegalArgumentException 用户名不存在 / 密码错误（统一提示，防止用户名枚举）
     */
    public LoginResponse login(LoginRequest request) {
        // 1. 查用户
        User user = repository.findByUsername(request.getUsername())
            .orElseThrow(() -> new IllegalArgumentException("用户名或密码错误"));

        // 2. 验密码（BCrypt 自动从 hash 里取 salt 重新计算再比对）
        // 教学点：永远不要自己写 passwordEncoder.matches 的等价逻辑
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            log.warn("登录失败（密码错误）: {}", request.getUsername());
            throw new IllegalArgumentException("用户名或密码错误");
        }

        // 3. 签发 JWT
        String token = tokenProvider.generateToken(user.getUsername());

        log.info("用户登录成功: id={}, username={}", user.getId(), user.getUsername());
        return new LoginResponse(
            token,
            tokenProvider.getExpirationSeconds(),
            UserResponse.fromEntity(user)
        );
    }
}
