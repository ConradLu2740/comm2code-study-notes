package com.conrad.shortlink.stats.controller;

import com.conrad.shortlink.stats.dto.StatsResponse;
import com.conrad.shortlink.stats.entity.VisitLog;
import com.conrad.shortlink.stats.service.StatsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 访问统计 REST API。
 *
 * <p>教学点：
 * <ul>
 *   <li>GET 查询用 @PathVariable 接收短码，REST 风格清晰</li>
 *   <li>不返回 list 大小写敏感的 DTO，直接返回 VisitLog 实体，
 *       由 Jackson 自动序列化（含 Hibernate 懒加载字段时需小心，本场景 VisitLog 是普通 Entity，无关联）</li>
 *   <li>实际生产建议用 DTO 而不是直接返回 Entity，避免数据库字段意外暴露</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/shortlinks")
public class StatsController {

    private final StatsService statsService;

    public StatsController(StatsService statsService) {
        this.statsService = statsService;
    }

    /**
     * GET /api/v1/shortlinks/{shortCode}/stats
     * 返回某短码的聚合统计。
     */
    @GetMapping("/{shortCode}/stats")
    public StatsResponse stats(@PathVariable String shortCode) {
        return statsService.getStats(shortCode);
    }

    /**
     * GET /api/v1/shortlinks/{shortCode}/visits
     * 返回最近 100 条访问日志。
     */
    @GetMapping("/{shortCode}/visits")
    public List<VisitLog> visits(@PathVariable String shortCode) {
        return statsService.getRecentVisits(shortCode);
    }
}
