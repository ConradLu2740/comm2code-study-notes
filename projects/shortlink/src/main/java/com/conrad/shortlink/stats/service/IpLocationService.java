package com.conrad.shortlink.stats.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.lionsoul.ip2region.xdb.Searcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;

/**
 * IP 地域解析服务，基于 ip2region xdb 全内存加载。
 *
 * <p>教学点：
 * <ul>
 *   <li>xdb 文件是预生成的二进制库，加载一次即可 O(1) 查询</li>
 *   <li>{@code Searcher.newWithBuffer} 返回的实例线程安全，整个应用共用一个即可</li>
 *   <li>文件可能不存在（教学环境未下载），init 失败要吞掉异常，
 *       运行时 {@link #resolve} 返回 Unknown 兜底，不影响主流程</li>
 *   <li>ip2region 原始格式 {@code 国家|区域|省份|城市|运营商}，按 {@code |} 切分</li>
 * </ul>
 */
@Service
public class IpLocationService {

    private static final Logger log = LoggerFactory.getLogger(IpLocationService.class);

    /**
     * classpath 下的 xdb 路径，对应 src/main/resources/ip2region/ip2region.xdb。
     * 教学环境如果没下载真实文件，ClassPathResource.exists() 会返回 false。
     */
    private static final String XDB_PATH = "ip2region/ip2region.xdb";

    private Searcher searcher;

    @PostConstruct
    public void init() {
        try {
            ClassPathResource resource = new ClassPathResource(XDB_PATH);
            if (!resource.exists()) {
                log.warn("ip2region xdb 文件不存在：{}，IP 地域解析将返回 Unknown", XDB_PATH);
                return;
            }
            // 教学点：newWithBuffer 内部把整个 xdb 读到内存（约 11MB），
            // 比 newWithFile 快 10x+，但占堆内存
            try (InputStream is = resource.getInputStream()) {
                byte[] dbBytes = is.readAllBytes();
                this.searcher = Searcher.newWithBuffer(dbBytes);
                log.info("ip2region xdb 加载完成，大小 {} bytes", dbBytes.length);
            }
        } catch (Exception e) {
            // 教学点：init 阶段绝不能抛异常炸掉应用，降级为返回 Unknown
            log.error("加载 ip2region xdb 失败", e);
            this.searcher = null;
        }
    }

    @PreDestroy
    public void destroy() {
        if (searcher != null) {
            try {
                searcher.close();
            } catch (Exception e) {
                log.warn("关闭 Searcher 失败", e);
            }
        }
    }

    /**
     * 解析 IP，返回 (国家, 区域, 城市, 运营商)。失败/未知都返回 Unknown/空串。
     */
    public Location resolve(String ip) {
        if (ip == null || ip.isEmpty()) {
            return new Location("Unknown", "", "", "");
        }
        if (searcher == null) {
            return new Location("Unknown", "", "", "");
        }
        try {
            String region = searcher.search(ip);
            if (region == null || region.isEmpty()) {
                return new Location("Unknown", "", "", "");
            }
            // 教学点：ip2region v2 格式 国家|区域|省份|城市|运营商
            // 例如 "中国|0|浙江省|杭州市|电信"
            String[] parts = region.split("\\|");
            String country = parts.length > 0 ? parts[0] : "Unknown";
            String regionName = parts.length > 2 ? parts[2] : "";
            String city = parts.length > 3 ? parts[3] : "";
            String isp = parts.length > 4 ? parts[4] : "";
            return new Location(country, regionName, city, isp);
        } catch (Exception e) {
            log.debug("IP 解析异常: {}", ip, e);
            return new Location("Unknown", "", "", "");
        }
    }

    /**
     * IP 解析结果。
     * 教学点：record 适合做不可变值对象，省去手写 getter 的样板。
     */
    public record Location(String country, String region, String city, String isp) {
    }
}
