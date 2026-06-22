package com.conrad.shortlink.stats.dto;

/**
 * 按 deviceType 聚合的统计结果（Mobile / Desktop / Tablet）。
 *
 * <p>教学点：见 {@link CountryCount}，interface projection 用法一致。
 */
public interface DeviceCount {
    String getDeviceType();
    long getCount();
}
