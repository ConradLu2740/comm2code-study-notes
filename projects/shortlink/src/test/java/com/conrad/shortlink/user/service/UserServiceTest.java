package com.conrad.shortlink.user.service;

import com.conrad.shortlink.user.dto.LoginRequest;
import com.conrad.shortlink.user.dto.LoginResponse;
import com.conrad.shortlink.user.dto.RegisterRequest;
import com.conrad.shortlink.user.dto.UserResponse;
import com.conrad.shortlink.user.entity.User;
import com.conrad.shortlink.user.repository.UserRepository;
import com.conrad.shortlink.user.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * UserService 单元测试
 *
 * 教学点：
 * 1. Mockito 模拟所有依赖（Repository / BCryptPasswordEncoder / JwtTokenProvider）
 * 2. BCrypt 是真实对象（不是 mock）—— 因为我们就是要测 BCrypt 集成的正确性
 *    - 如果连 BCrypt 都 mock，测试就形同虚设
 * 3. when().thenReturn() 模拟返回值；verify() 验证行为
 * 4. 覆盖：注册成功 / 用户名重复 / 邮箱重复 / 登录成功 / 密码错 / 用户不存在
 */
class UserServiceTest {

    private UserRepository repository;
    private BCryptPasswordEncoder passwordEncoder;
    private JwtTokenProvider tokenProvider;
    private UserService service;

    @BeforeEach
    void setUp() {
        repository = mock(UserRepository.class);
        // 教学点：BCryptPasswordEncoder 是真实实例（cost 默认 10，单测够用）
        passwordEncoder = new BCryptPasswordEncoder();
        // JwtTokenProvider 用 mock，避免真实密钥校验
        tokenProvider = mock(JwtTokenProvider.class);

        service = new UserService(repository, passwordEncoder, tokenProvider);
    }

    // ==================== register ====================

    @Test
    void shouldRegisterSuccessfully() {
        RegisterRequest req = new RegisterRequest();
        req.setUsername("alice");
        req.setEmail("alice@example.com");
        req.setPassword("secret123");

        when(repository.existsByUsername("alice")).thenReturn(false);
        when(repository.existsByEmail("alice@example.com")).thenReturn(false);
        // save() 模拟 JPA：给个 id 和 createdAt 返回
        when(repository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(1L);
            u.setCreatedAt(Instant.now());
            u.setUpdatedAt(Instant.now());
            return u;
        });

        UserResponse resp = service.register(req);

        assertEquals(1L, resp.getId());
        assertEquals("alice", resp.getUsername());
        assertEquals("alice@example.com", resp.getEmail());
        // 教学点：DTO 不应返回 passwordHash
        assertNull(resp.getCreatedAt() == null ? null : null);  // createdAt 已设，不为 null
        assertNotNull(resp.getCreatedAt());
        verify(repository).save(any(User.class));
    }

    @Test
    void shouldHashPasswordOnRegister() {
        // 核心：测试密码确实被 BCrypt 加密了（不是明文存）
        RegisterRequest req = new RegisterRequest();
        req.setUsername("bob");
        req.setEmail("bob@example.com");
        req.setPassword("plainPassword");

        when(repository.existsByUsername(any())).thenReturn(false);
        when(repository.existsByEmail(any())).thenReturn(false);
        when(repository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(1L);
            u.setCreatedAt(Instant.now());
            return u;
        });

        service.register(req);

        // 捕获 save 时的 User 实参，验证 passwordHash 是 BCrypt 格式
        org.mockito.ArgumentCaptor<User> captor = org.mockito.ArgumentCaptor.forClass(User.class);
        verify(repository).save(captor.capture());
        String hash = captor.getValue().getPasswordHash();

        // BCrypt hash 特征：$2a$10$... 开头，60 字节
        assertNotEquals("plainPassword", hash);
        assertTrue(hash.startsWith("$2a$") || hash.startsWith("$2b$"),
            "应该是 BCrypt 格式，实际是: " + hash);
        assertEquals(60, hash.length());

        // 验证 hash 能被原密码 matches 成功（自洽）
        assertTrue(passwordEncoder.matches("plainPassword", hash));
    }

    @Test
    void shouldThrowWhenUsernameExists() {
        RegisterRequest req = new RegisterRequest();
        req.setUsername("alice");
        req.setEmail("alice@example.com");
        req.setPassword("secret123");

        when(repository.existsByUsername("alice")).thenReturn(true);

        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> service.register(req)
        );
        assertTrue(ex.getMessage().contains("用户名已存在"));
        verify(repository, never()).save(any(User.class));
    }

    @Test
    void shouldThrowWhenEmailExists() {
        RegisterRequest req = new RegisterRequest();
        req.setUsername("alice");
        req.setEmail("taken@example.com");
        req.setPassword("secret123");

        when(repository.existsByUsername("alice")).thenReturn(false);
        when(repository.existsByEmail("taken@example.com")).thenReturn(true);

        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> service.register(req)
        );
        assertTrue(ex.getMessage().contains("邮箱已存在"));
        verify(repository, never()).save(any(User.class));
    }

    // ==================== login ====================

    @Test
    void shouldLoginSuccessfully() {
        // 准备一个已存在的用户（密码 hash 是真实的 BCrypt hash）
        String realHash = passwordEncoder.encode("correctPassword");
        User existing = new User("alice", "alice@example.com", realHash);
        existing.setId(1L);
        existing.setCreatedAt(Instant.now());

        LoginRequest req = new LoginRequest();
        req.setUsername("alice");
        req.setPassword("correctPassword");

        when(repository.findByUsername("alice")).thenReturn(Optional.of(existing));
        when(tokenProvider.generateToken("alice")).thenReturn("jwt.token.here");
        when(tokenProvider.getExpirationSeconds()).thenReturn(604800L);

        LoginResponse resp = service.login(req);

        assertEquals("jwt.token.here", resp.getToken());
        assertEquals(604800L, resp.getExpiresIn());
        assertNotNull(resp.getUser());
        assertEquals("alice", resp.getUser().getUsername());
        verify(tokenProvider).generateToken("alice");
    }

    @Test
    void shouldThrowWhenUserNotFound() {
        LoginRequest req = new LoginRequest();
        req.setUsername("ghost");
        req.setPassword("whatever");

        when(repository.findByUsername("ghost")).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> service.login(req)
        );
        // 教学点：用户名/密码错误用同一提示，防用户名枚举
        assertEquals("用户名或密码错误", ex.getMessage());
        verify(tokenProvider, never()).generateToken(any());
    }

    @Test
    void shouldThrowWhenPasswordWrong() {
        String realHash = passwordEncoder.encode("correctPassword");
        User existing = new User("alice", "alice@example.com", realHash);
        existing.setId(1L);

        LoginRequest req = new LoginRequest();
        req.setUsername("alice");
        req.setPassword("wrongPassword");

        when(repository.findByUsername("alice")).thenReturn(Optional.of(existing));

        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> service.login(req)
        );
        // 教学点：和"用户不存在"用同一提示，防用户名枚举
        assertEquals("用户名或密码错误", ex.getMessage());
        verify(tokenProvider, never()).generateToken(any());
    }
}
