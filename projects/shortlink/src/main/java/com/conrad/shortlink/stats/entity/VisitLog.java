package com.conrad.shortlink.stats.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * 访问日志实体。
 *
 * <p>短链接每次被访问都会写一条记录，用于后续地域/设备/Referer 统计。
 *
 * <p>教学点：
 * <ul>
 *   <li>短码 + 访问时间是高频查询条件，单独建索引避免全表扫描</li>
 *   <li>userAgent 可能很长（移动端 UA 经常超 256），单独放宽到 1024</li>
 *   <li>Lombok 不可用，所有 getter/setter 手写</li>
 * </ul>
 */
@Entity
@Table(
    name = "visit_log",
    indexes = {
        // 教学点：组合索引比单列索引更省空间，但本场景按短码过滤 + 按时间排序更常见，
        // 这里简单建两个单列索引，MySQL 会自动选择
        @Index(name = "idx_visit_short_code", columnList = "shortCode"),
        @Index(name = "idx_visit_accessed_at", columnList = "accessedAt")
    }
)
public class VisitLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32)
    private String shortCode;

    // IPv6 最大长度 45 (xxx:xxx:xxx:xxx:xxx:xxx:xxx:xxx)
    @Column(length = 45)
    private String ip;

    @Column(length = 64)
    private String country;

    @Column(length = 64)
    private String region;

    @Column(length = 64)
    private String city;

    @Column(length = 64)
    private String isp;

    @Column(length = 32)
    private String browser;

    @Column(length = 32)
    private String os;

    // 教学点：deviceType 故意用 String 而不是枚举，避免引入新的 enum 依赖；
    // 写死三个值：Mobile / Desktop / Tablet，统计时直接 group by
    @Column(length = 16)
    private String deviceType;

    @Column(length = 512)
    private String referer;

    @Column(length = 1024)
    private String userAgent;

    @Column(nullable = false)
    private LocalDateTime accessedAt;

    // ========== 业务构造器 ==========
    // 教学点：保留一个无参构造器给 JPA 用，再提供一个业务构造器给 Service 写日志用
    public VisitLog() {
    }

    public VisitLog(String shortCode, String ip, String country, String region,
                    String city, String isp, String browser, String os,
                    String deviceType, String referer, String userAgent,
                    LocalDateTime accessedAt) {
        this.shortCode = shortCode;
        this.ip = ip;
        this.country = country;
        this.region = region;
        this.city = city;
        this.isp = isp;
        this.browser = browser;
        this.os = os;
        this.deviceType = deviceType;
        this.referer = referer;
        this.userAgent = userAgent;
        this.accessedAt = accessedAt;
    }

    // ========== getter / setter ==========

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getShortCode() {
        return shortCode;
    }

    public void setShortCode(String shortCode) {
        this.shortCode = shortCode;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getIsp() {
        return isp;
    }

    public void setIsp(String isp) {
        this.isp = isp;
    }

    public String getBrowser() {
        return browser;
    }

    public void setBrowser(String browser) {
        this.browser = browser;
    }

    public String getOs() {
        return os;
    }

    public void setOs(String os) {
        this.os = os;
    }

    public String getDeviceType() {
        return deviceType;
    }

    public void setDeviceType(String deviceType) {
        this.deviceType = deviceType;
    }

    public String getReferer() {
        return referer;
    }

    public void setReferer(String referer) {
        this.referer = referer;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public LocalDateTime getAccessedAt() {
        return accessedAt;
    }

    public void setAccessedAt(LocalDateTime accessedAt) {
        this.accessedAt = accessedAt;
    }
}
