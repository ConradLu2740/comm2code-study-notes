package com.conrad.shortlink.stats.service;

import com.conrad.shortlink.stats.dto.CountryCount;
import com.conrad.shortlink.stats.dto.DeviceCount;
import com.conrad.shortlink.stats.dto.StatsResponse;
import com.conrad.shortlink.stats.entity.VisitLog;
import com.conrad.shortlink.stats.repository.VisitLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * StatsService 单元测试，用 Mockito 隔离 Repository 和外部服务。
 *
 * <p>教学点：
 * <ul>
 *   <li>单元测试不启动 Spring，直接 new 服务 + mock 依赖，启动 ms 级</li>
 *   <li>{@code @Async} 在脱离 Spring 代理时退化为同步调用，测试里能正常 verify</li>
 *   <li>interface projection 在测试里用 {@code mock(CountryCount.class)} 当 stub，
 *       再 stub 方法返回值即可</li>
 * </ul>
 */
class StatsServiceTest {

    private VisitLogRepository repository;
    private IpLocationService ipLocationService;
    private UserAgentParser userAgentParser;
    private StatsService statsService;

    @BeforeEach
    void setUp() {
        repository = mock(VisitLogRepository.class);
        ipLocationService = mock(IpLocationService.class);
        userAgentParser = mock(UserAgentParser.class);
        statsService = new StatsService(repository, ipLocationService, userAgentParser);
    }

    @Test
    void getStatsAggregatesAllSources() {
        // given
        when(repository.countByShortCode("abc123")).thenReturn(120L);

        CountryCount cn = mock(CountryCount.class);
        when(cn.getCountry()).thenReturn("中国");
        when(cn.getCount()).thenReturn(80L);

        CountryCount us = mock(CountryCount.class);
        when(us.getCountry()).thenReturn("美国");
        when(us.getCount()).thenReturn(40L);

        DeviceCount mobile = mock(DeviceCount.class);
        when(mobile.getDeviceType()).thenReturn("Mobile");
        when(mobile.getCount()).thenReturn(70L);

        DeviceCount desktop = mock(DeviceCount.class);
        when(desktop.getDeviceType()).thenReturn("Desktop");
        when(desktop.getCount()).thenReturn(50L);

        // 2 个访问时间，分别在同一天（聚合后应该只有 1 条 dailyTrend）
        Instant t1 = Instant.parse("2026-06-21T10:00:00Z");
        Instant t2 = Instant.parse("2026-06-21T15:00:00Z");

        when(repository.countByCountry("abc123")).thenReturn(List.of(cn, us));
        when(repository.countByDevice("abc123")).thenReturn(List.of(mobile, desktop));
        when(repository.findAllAccessTimes("abc123")).thenReturn(List.of(t1, t2));

        // when
        StatsResponse stats = statsService.getStats("abc123");

        // then
        assertEquals(120L, stats.totalClicks());
        assertEquals(2L, stats.uniqueCountries());
        assertEquals(2, stats.topCountries().size());
        assertEquals("中国", stats.topCountries().get(0).getCountry());
        assertEquals(2, stats.topDevices().size());
        assertEquals("Mobile", stats.topDevices().get(0).getDeviceType());
        assertEquals(1, stats.dailyTrend().size());
        assertEquals(2L, stats.dailyTrend().get(0).getCount());
    }

    @Test
    void getStatsWithEmptyDataReturnsZeros() {
        // given
        when(repository.countByShortCode("empty")).thenReturn(0L);
        when(repository.countByCountry("empty")).thenReturn(List.of());
        when(repository.countByDevice("empty")).thenReturn(List.of());
        when(repository.findAllAccessTimes("empty")).thenReturn(List.of());

        // when
        StatsResponse stats = statsService.getStats("empty");

        // then
        assertEquals(0L, stats.totalClicks());
        assertEquals(0L, stats.uniqueCountries());
        assertEquals(0, stats.topCountries().size());
        assertEquals(0, stats.dailyTrend().size());
    }

    @Test
    void recordVisitPersistsLog() {
        // given
        when(ipLocationService.resolve("1.2.3.4"))
                .thenReturn(new IpLocationService.Location("中国", "0", "浙江省", "电信"));
        when(userAgentParser.parse("ua"))
                .thenReturn(new UserAgentParser.UaInfo("Chrome", "Windows", "Desktop"));

        // when
        statsService.recordVisit("abc123", "1.2.3.4", "ua", "https://example.com");

        // then
        verify(repository, times(1)).save(any(VisitLog.class));
    }

    @Test
    void recordVisitSwallowsException() {
        // given
        when(ipLocationService.resolve(any()))
                .thenThrow(new RuntimeException("ip db not loaded"));
        // when & then：不抛异常
        statsService.recordVisit("abc123", "1.2.3.4", "ua", "https://example.com");
        // repository.save 不会被调用
        verify(repository, times(0)).save(any(VisitLog.class));
    }

    @Test
    void getRecentVisitsReturnsList() {
        // given
        VisitLog entry = new VisitLog();
        when(repository.findByShortCodeOrderByAccessedAtDesc(
                ArgumentMatchers.eq("abc123"), any()))
                .thenReturn(List.of(entry));

        // when
        List<VisitLog> result = statsService.getRecentVisits("abc123");

        // then
        assertEquals(1, result.size());
    }
}
