package com.conrad.shortlink.stats.repository;

import com.conrad.shortlink.stats.dto.CountryCount;
import com.conrad.shortlink.stats.dto.DailyCount;
import com.conrad.shortlink.stats.dto.DeviceCount;
import com.conrad.shortlink.stats.entity.VisitLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * 访问日志 Repository。
 *
 * <p>教学点：
 * <ul>
 *   <li>简单 count/findBy 用 Spring Data 派生方法，零 SQL</li>
 *   <li>聚合统计用 @Query + interface projection，避免手写 DTO 转换代码</li>
 *   <li>interface projection 由 Spring Data 在运行时生成代理类，方法名匹配字段别名</li>
 *   <li>跨数据库的日期格式化用 native query，并注释 H2 需要 MySQL 兼容模式</li>
 * </ul>
 */
public interface VisitLogRepository extends JpaRepository<VisitLog, Long> {

    /**
     * 取某短码最近的 N 条访问日志（按时间倒序）。
     * Pageable 传 PageRequest.of(0, 100) 即 top 100。
     */
    List<VisitLog> findByShortCodeOrderByAccessedAtDesc(String shortCode, Pageable pageable);

    /**
     * 统计某短码的总访问次数。
     */
    long countByShortCode(String shortCode);

    /**
     * 按 country 分组统计。
     * 教学点：AS 别名必须和接口方法 getXxx 对应（如 country -> getCountry）。
     */
    @Query("SELECT v.country AS country, COUNT(v) AS count " +
           "FROM VisitLog v " +
           "WHERE v.shortCode = :shortCode AND v.country IS NOT NULL AND v.country <> '' " +
           "GROUP BY v.country " +
           "ORDER BY count DESC")
    List<CountryCount> countByCountry(@Param("shortCode") String shortCode);

    /**
     * 按 deviceType 分组统计。
     */
    @Query("SELECT v.deviceType AS deviceType, COUNT(v) AS count " +
           "FROM VisitLog v " +
           "WHERE v.shortCode = :shortCode AND v.deviceType IS NOT NULL AND v.deviceType <> '' " +
           "GROUP BY v.deviceType " +
           "ORDER BY count DESC")
    List<DeviceCount> countByDevice(@Param("shortCode") String shortCode);

    /**
     * 按日期分组统计访问量（每日趋势）。
     *
     * <p>教学点：跨数据库的日期聚合用 JPQL + EXTRACT，H2 和 MySQL 都支持。
     * 改用 Service 层拿到 visits 后聚合（避免跨 DB 兼容问题）。
     */
    @Query("SELECT v.accessedAt FROM VisitLog v WHERE v.shortCode = :shortCode")
    List<java.time.Instant> findAllAccessTimes(@Param("shortCode") String shortCode);
}
