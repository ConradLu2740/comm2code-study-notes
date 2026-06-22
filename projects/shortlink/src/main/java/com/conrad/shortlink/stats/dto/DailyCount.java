package com.conrad.shortlink.stats.dto;

/**
 * 按日期聚合的访问量（每日趋势）。
 *
 * <p>教学点：
 * <ul>
 *   <li>date 用 String 而非 {@link java.time.LocalDate}，避免 Jackson 默认序列化
 *       还要配 JavaTimeModule（也避免 Hibernate cast 类型的版本差异）</li>
 *   <li>date 格式固定 yyyy-MM-dd，来自 native query 的 DATE_FORMAT</li>
 * </ul>
 */
public interface DailyCount {
    String getDate();
    long getCount();
}
