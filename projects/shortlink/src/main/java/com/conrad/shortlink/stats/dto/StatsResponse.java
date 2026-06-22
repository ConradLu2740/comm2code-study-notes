package com.conrad.shortlink.stats.dto;

import java.util.List;

/**
 * 统计接口返回的聚合结果。
 *
 * <p>教学点：响应 DTO 用 Java 17 record，天然不可变、字段自带 getter，
 * 比手写 getter/setter 简洁一个量级。Lombok 不可用时 record 是首选。
 *
 * <p>字段语义：
 * <ul>
 *   <li>totalClicks - 该短码总访问次数</li>
 *   <li>uniqueCountries - 出现过的独立 country 数量（不是独立访客数）</li>
 *   <li>topCountries - 按访问量倒序的 country 列表（接口投影）</li>
 *   <li>topDevices - 设备类型分布</li>
 *   <li>dailyTrend - 每日访问量（按日期倒序）</li>
 * </ul>
 */
public record StatsResponse(
        long totalClicks,
        long uniqueCountries,
        List<CountryCount> topCountries,
        List<DeviceCount> topDevices,
        List<DailyCount> dailyTrend
) {
}
