package com.conrad.shortlink.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 配置：开启 CORS 跨域支持
 *
 * 教学点：
 * - CORS（Cross-Origin Resource Sharing）是浏览器的安全机制
 * - 当前后端分离时，前端（不同端口）调用后端 API 会被浏览器拦截
 * - 这里允许所有来源，仅用于开发
 *
 * 对应学习模块：notes/java/08-spring
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
