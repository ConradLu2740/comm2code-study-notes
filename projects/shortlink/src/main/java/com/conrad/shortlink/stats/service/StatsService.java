package com.conrad.shortlink.stats.service;

import com.conrad.shortlink.stats.dto.CountryCount;
import com.conrad.shortlink.stats.dto.DailyCount;
import com.conrad.shortlink.stats.dto.DeviceCount;
import com.conrad.shortlink.stats.dto.StatsResponse;
import com.conrad.shortlink.stats.entity.VisitLog;
import com.conrad.shortlink.stats.repository.VisitLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 统计核心服务。
 *
 * <p>两个职责：
 * <ol>
 *   <li>{@link #recordVisit} - 异步写访问日志，不阻塞短链接跳转主流程</li>
 *   <li>{@link #getStats} / {@link #getRecentVisits} - 同步聚合查询</li>
 * </ol>
 *
 * <p>教学点：
 * <ul>
 *   <li>{@code @Async} 让写日志在独立线程池执行，跳转接口立即返回 302；
 *       <b>必须在主类加 {@code @EnableAsync} 才会生效</b>（否则会以同步方式调用，但代码不报错）</li>
 *   <li>异步方法最好在独立事务里写库，加 {@code REQUIRES_NEW} 避免外部事务回滚影响日志写入</li>
 *   <li>异步方法返回值必须 void 或 {@code CompletableFuture}，不能直接返回 DTO</li>
 *   <li>异常不能从异步方法里抛出，会被丢弃（这里 catch 后只记日志）</li>
 * </ul>
 */
@Service
public class StatsService {

    private static final Logger log = LoggerFactory.getLogger(StatsService.class);

    private final VisitLogRepository repository;
    private final IpLocationService ipLocationService;
    private final UserAgentParser userAgentParser;

    public StatsService(VisitLogRepository repository,
                        IpLocationService ipLocationService,
                        UserAgentParser userAgentParser) {
        this.repository = repository;
        this.ipLocationService = ipLocationService;
        this.userAgentParser = userAgentParser;
    }

    /**
     * 异步记录一次访问。
     *
     * <p>需要主类加 {@code @EnableAsync} 才会真正异步执行。
     * 短链接跳转 Controller 调完即可返回 302，写日志由线程池兜底。
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordVisit(String shortCode, String ip, String userAgent, String referer) {
        try {
            IpLocationService.Location location = ipLocationService.resolve(ip);
            UserAgentParser.UaInfo ua = userAgentParser.parse(userAgent);

            VisitLog entry = new VisitLog(
                    shortCode,
                    ip,
                    location.country(),
                    location.region(),
                    location.city(),
                    location.isp(),
                    ua.browser(),
                    ua.os(),
                    ua.deviceType(),
                    referer,
                    userAgent,
                    LocalDateTime.now()
            );
            repository.save(entry);
        } catch (Exception e) {
            // 教学点：异步线程的异常默认会被吞掉，这里显式 catch 后只记日志，
            // 绝不抛出，否则调用方拿不到反馈且线程池可能异常退出
            StatsService.log.error("记录访问日志失败: shortCode={}, ip={}", shortCode, ip, e);
        }
    }

    /**
     * 聚合查询某短码的统计信息。
     */
    public StatsResponse getStats(String shortCode) {
        long totalClicks = repository.countByShortCode(shortCode);
        List<CountryCount> countries = repository.countByCountry(shortCode);
        List<DeviceCount> devices = repository.countByDevice(shortCode);

        // 按日期聚合：在 Java 层做，避免跨 DB 的 DATE_FORMAT 兼容问题
        // 教学点：小数据量（万级）在内存 group by 没问题；大数据量应该用 SQL 函数或 OLAP
        List<Instant> allAccessTimes = repository.findAllAccessTimes(shortCode);
        List<DailyCount> daily = aggregateByDate(allAccessTimes);

        // 教学点：uniqueCountries 是 country 维度去重数，不是独立访客数
        // 严格意义的独立访客需要用 HyperLogLog 或 Redis Bitmap，成本更高
        long uniqueCountries = countries.size();

        return new StatsResponse(totalClicks, uniqueCountries, countries, devices, daily);
    }

    /**
     * 按日期分组计数（TreeMap 保持日期升序）
     */
    private List<DailyCount> aggregateByDate(List<Instant> instants) {
        Map<LocalDate, Long> grouped = new TreeMap<>();
        ZoneId zone = ZoneId.systemDefault();
        for (Instant instant : instants) {
            LocalDate date = instant.atZone(zone).toLocalDate();
            grouped.merge(date, 1L, Long::sum);
        }
        return grouped.entrySet().stream()
            .<DailyCount>map(e -> new DailyCount() {
                @Override public String getDate() { return e.getKey().toString(); }
                @Override public long getCount() { return e.getValue(); }
            })
            .toList();
    }

    /**
     * 取某短码最近 100 条访问日志。
     */
    public List<VisitLog> getRecentVisits(String shortCode) {
        Pageable pageable = PageRequest.of(0, 100);
        return repository.findByShortCodeOrderByAccessedAtDesc(shortCode, pageable);
    }
}
