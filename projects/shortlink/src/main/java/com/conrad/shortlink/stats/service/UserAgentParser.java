package com.conrad.shortlink.stats.service;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * 简化的 User-Agent 解析器。
 *
 * <p>教学点：
 * <ul>
 *   <li>完整 UA 解析非常复杂（Yauzl / Woothee / ua-parser 都是几百 KB 的库），
 *       这里只做正则匹配，足够给短链接后台看大盘</li>
 *   <li>浏览器识别顺序很关键：Edge 内核是 Chromium，必须先于 Chrome 匹配，
 *       否则 Edg 字符串里的 Chrome 关键字会先命中</li>
 *   <li>iPad 同时含 "Safari" 和 "Mobile" 关键词，需要单独判断为 Tablet</li>
 *   <li>Pattern 是线程安全且不可变，定义为 static final 复用，避免每次调用都编译正则</li>
 * </ul>
 *
 * <p>已知局限（用 ponytail 标记）：
 * <ul>
 *   <li>无法识别国产浏览器（QQ、UC、夸克）的内核，只能归类为 Chrome</li>
 *   <li>无法识别 Bot 流量</li>
 *   <li>无法识别具体设备型号（iPhone 14 vs 15）</li>
 * </ul>
 */
@Component
public class UserAgentParser {

    // 浏览器正则：捕获版本号（虽然我们只用类型）
    private static final Pattern EDGE = Pattern.compile("Edg/([\\d.]+)");
    private static final Pattern CHROME = Pattern.compile("Chrome/([\\d.]+)");
    private static final Pattern FIREFOX = Pattern.compile("Firefox/([\\d.]+)");
    private static final Pattern SAFARI = Pattern.compile("Version/([\\d.]+).*Safari");

    // 操作系统正则
    private static final Pattern WINDOWS = Pattern.compile("Windows NT ([\\d.]+)");
    private static final Pattern MACOS = Pattern.compile("Mac OS X ([\\d._]+)");
    private static final Pattern LINUX = Pattern.compile("Linux(?!.*Android)");
    private static final Pattern ANDROID = Pattern.compile("Android ([\\d.]+)");
    private static final Pattern IOS = Pattern.compile("(iPhone|iPad|iPod).*OS ([\\d_]+)");

    /**
     * 解析 UA，返回 (浏览器, 操作系统, 设备类型)。
     */
    public UaInfo parse(String userAgent) {
        if (userAgent == null || userAgent.isEmpty()) {
            return new UaInfo("Unknown", "Unknown", "Desktop");
        }

        String browser = detectBrowser(userAgent);
        String os = detectOs(userAgent);
        String deviceType = detectDevice(userAgent);
        return new UaInfo(browser, os, deviceType);
    }

    private String detectBrowser(String ua) {
        // 教学点：顺序敏感，Edge 必须先于 Chrome
        if (EDGE.matcher(ua).find()) return "Edge";
        if (CHROME.matcher(ua).find()) return "Chrome";
        if (FIREFOX.matcher(ua).find()) return "Firefox";
        if (SAFARI.matcher(ua).find()) return "Safari";
        return "Unknown";
    }

    private String detectOs(String ua) {
        // Windows NT 10.0 / 6.1 等
        if (WINDOWS.matcher(ua).find()) return "Windows";
        if (IOS.matcher(ua).find()) return "iOS";
        if (ANDROID.matcher(ua).find()) return "Android";
        if (MACOS.matcher(ua).find()) return "macOS";
        if (LINUX.matcher(ua).find()) return "Linux";
        return "Unknown";
    }

    private String detectDevice(String ua) {
        // 教学点：iPad 在 iOS 13+ 后 UA 改成和 Mac 一样，需要特殊处理；
        // 这里用关键词 "iPad" / "Tablet" 简单判断
        if (ua.contains("iPad") || ua.contains("Tablet")) {
            return "Tablet";
        }
        if (ua.contains("Mobile") || ua.contains("Android")) {
            return "Mobile";
        }
        return "Desktop";
    }

    /**
     * UA 解析结果。
     */
    public record UaInfo(String browser, String os, String deviceType) {
    }
}
