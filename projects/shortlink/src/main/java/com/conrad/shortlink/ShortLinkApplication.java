package com.conrad.shortlink;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 短链接服务启动类
 *
 * 教学点：
 * 1. @SpringBootApplication = @Configuration + @EnableAutoConfiguration + @ComponentScan
 * 2. @EnableAsync 开启异步方法支持（让 @Async 注解生效）
 *    - StatsService.recordVisit() 用了 @Async 异步写日志
 * 3. 默认会扫描 com.conrad.shortlink 包及子包的所有 Bean
 * 4. ID 生成器在 id/IdGeneratorConfig 里有条件装配
 *
 * 对应学习模块：notes/java/08-spring
 */
@SpringBootApplication
@EnableAsync
public class ShortLinkApplication {

    public static void main(String[] args) {
        SpringApplication.run(ShortLinkApplication.class, args);
        System.out.println("""
                 _____ _     _      _     _
                |   __|_|___| |_   | |_  | |_  ___  _ _  ___
                |__   | |_ -|  _|  |  _||  _|| . || | || . |
                |_____|_|___|_|    |_|  |_|  |___||___||  _|
                                                     |_|
                短链接服务已启动 → http://localhost:8080
                用户模块: POST /api/v1/auth/{register,login}
                统计模块: GET  /api/v1/shortlinks/{code}/stats
                域名模块: POST /api/v1/domains
                """);
    }
}
