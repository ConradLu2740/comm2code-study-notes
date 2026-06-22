package com.conrad.shortlink.user.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 简易安全配置
 *
 * 教学点：
 * 1. 不引入 spring-security 全家桶，只用 spring-security-crypto 的 BCrypt
 * 2. BCryptPasswordEncoder 默认 strength=10（2^10 = 1024 轮哈希）
 * 3. 强度越高越慢越安全：12 适合后台，14+ 适合密码管理器
 *
 * 对应学习模块：notes/java/08-spring
 */
@Configuration
public class SecurityConfig {

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }
}
