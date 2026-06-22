package com.conrad.shortlink.stats.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * UserAgentParser 单元测试。
 *
 * <p>教学点：纯函数用 new + 方法调用即可测，无需 Spring 上下文，启动比 @SpringBootTest 快 100x+。
 */
class UserAgentParserTest {

    private final UserAgentParser parser = new UserAgentParser();

    @Test
    void parseChromeOnWindowsDesktop() {
        String ua = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
        UserAgentParser.UaInfo info = parser.parse(ua);
        assertEquals("Chrome", info.browser());
        assertEquals("Windows", info.os());
        assertEquals("Desktop", info.deviceType());
    }

    @Test
    void parseFirefoxOnMacDesktop() {
        String ua = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.15; rv:121.0) " +
                    "Gecko/20100101 Firefox/121.0";
        UserAgentParser.UaInfo info = parser.parse(ua);
        assertEquals("Firefox", info.browser());
        assertEquals("macOS", info.os());
        assertEquals("Desktop", info.deviceType());
    }

    @Test
    void parseSafariOniPhoneMobile() {
        String ua = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_2 like Mac OS X) " +
                    "AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.2 " +
                    "Mobile/15E148 Safari/604.1";
        UserAgentParser.UaInfo info = parser.parse(ua);
        assertEquals("Safari", info.browser());
        assertEquals("iOS", info.os());
        // 教学点：iPhone UA 含 "Mobile" 关键词，识别为 Mobile
        assertEquals("Mobile", info.deviceType());
    }

    @Test
    void parseChromeOnAndroidMobile() {
        String ua = "Mozilla/5.0 (Linux; Android 14; SM-S908B) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36";
        UserAgentParser.UaInfo info = parser.parse(ua);
        assertEquals("Chrome", info.browser());
        assertEquals("Android", info.os());
        assertEquals("Mobile", info.deviceType());
    }

    @Test
    void parseEdgeOnWindowsDesktop() {
        // 教学点：Edge UA 同时含 Chrome 和 Edg 关键词，
        // 必须 Edg 模式先匹配，否则会被 Chrome 截胡
        String ua = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36 Edg/120.0.0.0";
        UserAgentParser.UaInfo info = parser.parse(ua);
        assertEquals("Edge", info.browser());
        assertEquals("Windows", info.os());
        assertEquals("Desktop", info.deviceType());
    }

    @Test
    void parseNullReturnsUnknownDesktop() {
        UserAgentParser.UaInfo info = parser.parse(null);
        assertEquals("Unknown", info.browser());
        assertEquals("Unknown", info.os());
        assertEquals("Desktop", info.deviceType());
    }

    @Test
    void parseEmptyStringReturnsUnknownDesktop() {
        UserAgentParser.UaInfo info = parser.parse("");
        assertEquals("Unknown", info.browser());
        assertEquals("Unknown", info.os());
        assertEquals("Desktop", info.deviceType());
    }
}
