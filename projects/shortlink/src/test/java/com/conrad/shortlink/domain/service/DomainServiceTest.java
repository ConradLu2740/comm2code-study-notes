package com.conrad.shortlink.domain.service;

import com.conrad.shortlink.domain.dto.BindDomainRequest;
import com.conrad.shortlink.domain.dto.DomainVerifyResponse;
import com.conrad.shortlink.domain.entity.CustomDomain;
import com.conrad.shortlink.domain.repository.CustomDomainRepository;
import com.conrad.shortlink.repository.ShortLinkRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * DomainService 单元测试
 *
 * 教学点：
 * 1. Mockito 模拟 Repository 依赖，不连真实数据库
 * 2. ReflectionTestUtils.setField() 注入 @Value 字段（devMode）
 *    - 因为 devMode 是 private 且没有 setter，测试需要"破封装"
 *    - 这正是 ReflectionTestUtils 的设计目的：测试专用，不破坏生产代码
 * 3. 每个测试覆盖一个分支：成功路径 / 失败路径 / 越权保护
 * 4. 教学点：单测只测"本类逻辑"，不重复测 Mockito 框架本身
 */
class DomainServiceTest {

    private CustomDomainRepository domainRepository;
    private ShortLinkRepository shortLinkRepository;
    private DomainService service;

    @BeforeEach
    void setUp() {
        domainRepository = mock(CustomDomainRepository.class);
        shortLinkRepository = mock(ShortLinkRepository.class);
        service = new DomainService(domainRepository, shortLinkRepository);
        // 默认开启 devMode，让 verify 测试不需要真实 DNS
        ReflectionTestUtils.setField(service, "devMode", true);
    }

    // ===== bindDomain 测试 =====

    @Test
    void shouldBindDomainSuccessfully() {
        BindDomainRequest request = new BindDomainRequest();
        request.setDomain("S.Example.COM");  // 测试大小写规范化
        request.setShortCode("abc123");

        when(shortLinkRepository.existsByShortCode("abc123")).thenReturn(true);
        when(domainRepository.existsByDomain("s.example.com")).thenReturn(false);
        when(domainRepository.save(any(CustomDomain.class))).thenAnswer(inv -> {
            CustomDomain arg = inv.getArgument(0);
            arg.setId(1L);
            return arg;
        });

        CustomDomain result = service.bindDomain(request, 100L);

        // 教学点：域名被规范化为小写
        assertEquals("s.example.com", result.getDomain());
        assertEquals(100L, result.getUserId());
        assertEquals("abc123", result.getShortCode());
        assertEquals(CustomDomain.Status.PENDING, result.getStatus());
        // token 长度 = 32（UUID 去横线后前 32 位）
        assertEquals(32, result.getVerifyToken().length());
    }

    @Test
    void shouldRejectBindWhenShortCodeNotExists() {
        BindDomainRequest request = new BindDomainRequest();
        request.setDomain("s.example.com");
        request.setShortCode("missing");

        when(shortLinkRepository.existsByShortCode("missing")).thenReturn(false);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> service.bindDomain(request, 100L));
        assertTrue(ex.getMessage().contains("短码不存在"));
        // 教学点：根本没走到 domainRepository.existsByDomain
        verify(domainRepository, never()).existsByDomain(any());
        verify(domainRepository, never()).save(any());
    }

    @Test
    void shouldRejectBindWhenDomainAlreadyTaken() {
        BindDomainRequest request = new BindDomainRequest();
        request.setDomain("s.example.com");
        request.setShortCode("abc123");

        when(shortLinkRepository.existsByShortCode("abc123")).thenReturn(true);
        when(domainRepository.existsByDomain("s.example.com")).thenReturn(true);

        assertThrows(IllegalStateException.class,
            () -> service.bindDomain(request, 100L));
        verify(domainRepository, never()).save(any());
    }

    // ===== findActiveByDomain 测试 =====

    @Test
    void shouldReturnActiveDomainOnly() {
        CustomDomain pending = new CustomDomain(100L, "s.example.com", "abc", "token");
        // 默认就是 PENDING，应该被过滤
        when(domainRepository.findByDomain("s.example.com")).thenReturn(Optional.of(pending));

        Optional<CustomDomain> result = service.findActiveByDomain("s.example.com");
        assertTrue(result.isEmpty(), "PENDING 状态的域名不应被 findActiveByDomain 返回");

        // 改成 ACTIVE 后应该返回
        pending.markVerified();
        when(domainRepository.findByDomain("s.example.com")).thenReturn(Optional.of(pending));
        result = service.findActiveByDomain("s.example.com");
        assertTrue(result.isPresent());
        assertTrue(result.get().isUsable());
    }

    @Test
    void shouldReturnEmptyWhenDomainNotBound() {
        when(domainRepository.findByDomain("nope.com")).thenReturn(Optional.empty());
        assertTrue(service.findActiveByDomain("nope.com").isEmpty());
    }

    // ===== unbind 测试 =====

    @Test
    void shouldUnbindWhenOwnerMatches() {
        CustomDomain entity = new CustomDomain(100L, "s.example.com", "abc", "token");
        entity.setId(7L);
        when(domainRepository.findById(7L)).thenReturn(Optional.of(entity));

        service.unbind(7L, 100L);

        assertEquals(CustomDomain.Status.REVOKED, entity.getStatus());
        verify(domainRepository).save(entity);
    }

    @Test
    void shouldRejectUnbindFromOtherUser() {
        CustomDomain entity = new CustomDomain(100L, "s.example.com", "abc", "token");
        entity.setId(7L);
        when(domainRepository.findById(7L)).thenReturn(Optional.of(entity));

        // 用户 200 试图解绑用户 100 的域名
        SecurityException ex = assertThrows(SecurityException.class,
            () -> service.unbind(7L, 200L));
        assertTrue(ex.getMessage().contains("无权"));

        // 教学点：越权时不能保存（不能误删别人的数据）
        verify(domainRepository, never()).save(any());
    }

    @Test
    void shouldThrowWhenUnbindingNonExistentId() {
        when(domainRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class,
            () -> service.unbind(99L, 100L));
    }

    // ===== verifyDomain 测试 =====

    @Test
    void shouldVerifyDomainInDevMode() {
        CustomDomain entity = new CustomDomain(100L, "s.example.com", "abc", "token");
        entity.setId(1L);
        when(domainRepository.findByDomain("s.example.com")).thenReturn(Optional.of(entity));

        DomainVerifyResponse response = service.verifyDomain("S.Example.COM");

        assertEquals("ACTIVE", response.getStatus());
        assertEquals("s.example.com", response.getDomain());
        // 教学点：响应必须告诉用户应该添加什么 DNS 记录
        assertEquals("_shortlink-verify.s.example.com", response.getTxtHost());
        assertEquals("token", response.getTxtValue());
        assertNotNull(response.getVerifiedAt());
    }

    @Test
    void shouldThrowWhenVerifyingUnboundDomain() {
        when(domainRepository.findByDomain("nope.com")).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class,
            () -> service.verifyDomain("nope.com"));
    }

    // ===== findByUserId 测试 =====

    @Test
    void shouldListUserDomains() {
        CustomDomain d1 = new CustomDomain(100L, "a.com", "x", "t1");
        CustomDomain d2 = new CustomDomain(100L, "b.com", "y", "t2");
        when(domainRepository.findByUserId(100L)).thenReturn(List.of(d1, d2));

        List<CustomDomain> result = service.findByUserId(100L);
        assertEquals(2, result.size());
    }
}
