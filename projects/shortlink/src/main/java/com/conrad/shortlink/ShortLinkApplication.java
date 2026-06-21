package com.conrad.shortlink;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 短链接服务启动类
 *
 * 教学点：
 * 1. @SpringBootApplication = @Configuration + @EnableAutoConfiguration + @ComponentScan
 *    - @Configuration：标记这是配置类
 *    - @EnableAutoConfiguration：开启自动配置（Spring Boot 的核心）
 *    - @ComponentScan：自动扫描本包及子包的 @Component / @Service / @Repository / @Controller
 * 2. main 方法是 Java 应用入口，SpringApplication.run 启动内嵌 Tomcat
 *
 * 对应学习模块：notes/java/08-spring
 */
@SpringBootApplication
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
                """);
    }
}
