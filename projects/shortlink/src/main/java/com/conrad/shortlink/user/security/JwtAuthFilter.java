package com.conrad.shortlink.user.security;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT 鉴权过滤器
 *
 * 教学点：
 * 1. OncePerRequestFilter 保证每次请求只走一次（避免重定向时重复执行）
 * 2. 流程：解析 Authorization 头 → 提取 Bearer token → 校验 → 写入 request attribute
 * 3. 这里只"识别"用户身份，不做权限控制（未来 SecurityConfig 里再决定哪些路径要鉴权）
 * 4. 校验失败不要直接返回 401，而是放行（让 Controller 自己决定怎么处理）
 *    - 因为 /api/v1/auth/login 不需要 token，Filter 应该对没带 token 的请求透明
 * 5. request attribute 约定 key = "currentUsername"
 *
 * 对应学习模块：notes/java/13-security (Servlet Filter)
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);

    /**
     * request attribute key，Controller 可通过 (String) request.getAttribute("currentUsername") 获取
     */
    public static final String CURRENT_USERNAME_ATTR = "currentUsername";

    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider tokenProvider;

    public JwtAuthFilter(JwtTokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String token = extractToken(request);

        if (token != null) {
            try {
                String username = tokenProvider.validateToken(token);
                // 校验成功，把 username 放进 request attribute
                request.setAttribute(CURRENT_USERNAME_ATTR, username);
                log.debug("JWT 鉴权通过: {}", username);
            } catch (JwtException e) {
                // token 无效：不写入 attribute，但也不阻断（交给 Controller 处理）
                log.debug("JWT 校验失败: {}", e.getMessage());
            }
        }

        chain.doFilter(request, response);
    }

    /**
     * 从请求头提取 Bearer token
     * 教学点：Authorization: Bearer <token> → 去掉 "Bearer " 前缀
     */
    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader(AUTH_HEADER);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return null;
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }
}
