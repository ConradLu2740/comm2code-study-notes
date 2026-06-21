package com.conrad.shortlink.service;

import com.conrad.shortlink.entity.ShortLink;
import com.conrad.shortlink.exception.ShortLinkNotFoundException;
import com.conrad.shortlink.repository.ShortLinkRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * ShortLinkService 单元测试
 *
 * 教学点：
 * 1. Mockito 模拟依赖（Repository、Cache、Generator）
 * 2. @BeforeEach 在每个测试前初始化 mocks
 * 3. when().thenReturn() 模拟方法返回值
 * 4. verify() 验证方法被调用次数（推荐用 ArgumentCaptor 捕获参数）
 */
class ShortLinkServiceTest {

    private ShortLinkRepository repository;
    private ShortCodeGenerator codeGenerator;
    private CacheService cache;
    private ShortLinkService service;

    @BeforeEach
    void setUp() {
        repository = mock(ShortLinkRepository.class);
        codeGenerator = mock(ShortCodeGenerator.class);
        cache = mock(CacheService.class);
        service = new ShortLinkService(repository, codeGenerator, cache);
    }

    @Test
    void shouldCreateShortLinkWithGeneratedCode() {
        when(codeGenerator.generate()).thenReturn("abc123");
        when(repository.save(any(ShortLink.class))).thenAnswer(inv -> {
            ShortLink arg = inv.getArgument(0);
            arg.setId(1L);
            return arg;
        });

        ShortLink result = service.createShortLink("https://example.com", null);

        assertEquals("abc123", result.getShortCode());
        assertEquals("https://example.com", result.getLongUrl());
        verify(cache).set(eq("shortlink:code:abc123"), eq("https://example.com"), anyLong());
    }

    @Test
    void shouldUseAliasWhenProvided() {
        when(repository.existsByShortCode("myalias")).thenReturn(false);
        when(repository.save(any(ShortLink.class))).thenAnswer(inv -> {
            ShortLink arg = inv.getArgument(0);
            arg.setId(1L);
            return arg;
        });

        ShortLink result = service.createShortLink("https://example.com", "myalias");

        assertEquals("myalias", result.getShortCode());
        verify(codeGenerator, never()).generate();  // 不应该调生成器
    }

    @Test
    void shouldThrowWhenAliasExists() {
        when(repository.existsByShortCode("taken")).thenReturn(true);

        assertThrows(IllegalArgumentException.class,
            () -> service.createShortLink("https://example.com", "taken"));
    }

    @Test
    void shouldResolveFromCache() {
        when(cache.get("shortlink:code:abc")).thenReturn("https://example.com");

        String result = service.resolve("abc");

        assertEquals("https://example.com", result);
        verify(repository, never()).findByShortCode(any());
    }

    @Test
    void shouldResolveFromDatabaseOnCacheMiss() {
        ShortLink entity = new ShortLink("abc", "https://example.com", null);
        when(cache.get("shortlink:code:abc")).thenReturn(null);
        when(repository.findByShortCode("abc")).thenReturn(Optional.of(entity));

        String result = service.resolve("abc");

        assertEquals("https://example.com", result);
        verify(cache).set(eq("shortlink:code:abc"), eq("https://example.com"), anyLong());
    }

    @Test
    void shouldThrowOnNotFound() {
        when(cache.get(anyString())).thenReturn(null);
        when(repository.findByShortCode("missing")).thenReturn(Optional.empty());

        assertThrows(ShortLinkNotFoundException.class,
            () -> service.resolve("missing"));
    }

    @Test
    void shouldThrowOnExpired() {
        ShortLink expired = new ShortLink("abc", "https://example.com",
            java.time.Instant.now().minusSeconds(60));
        when(cache.get(anyString())).thenReturn(null);
        when(repository.findByShortCode("abc")).thenReturn(Optional.of(expired));

        assertThrows(ShortLinkNotFoundException.class,
            () -> service.resolve("abc"));
    }

    @Test
    void shouldCaptureIncrementClickCountArgs() {
        service.recordAccess("abc");
        verify(repository).incrementClickCount("abc");
    }
}
