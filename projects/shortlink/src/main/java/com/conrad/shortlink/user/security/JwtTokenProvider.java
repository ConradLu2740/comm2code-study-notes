package com.conrad.shortlink.user.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/**
 * JWT 工具类：签发 + 校验 Token
 *
 * 教学点（重点讲清楚 JWT 三段式）：
 * ┌─────────────────────────────────────────────────────────┐
 * │ JWT = Header.Payload.Signature                         │
 * │   Header    : {"alg":"HS256","typ":"JWT"}  (Base64URL) │
 * │   Payload   : {"sub":"alice","iat":...,"exp":...}      │
 * │   Signature : HMACSHA256(header.payload, secret)        │
 * └─────────────────────────────────────────────────────────┘
 *
 * 1. HS256 签名原理：
 *    - HMAC = Hash-based Message Authentication Code
 *    - 同一份 header+payload + 同一把密钥 → 永远生成相同签名
 *    - 服务端收到 token 时用同一把密钥重新算签名，对比
 *    - 只要密钥不泄露，攻击者无法伪造
 *
 * 2. jjwt 0.12.x API（与 0.11.x 不兼容）：
 *    - 旧：Jwts.builder().setSubject(...).setIssuedAt(...)
 *    - 新：Jwts.builder().subject(...).issuedAt(...)
 *    - 旧：Jwts.parserBuilder().setSigningKey(...)
 *    - 新：Jwts.parser().verifyWith(secretKey).build()
 *
 * 3. secret 必须 ≥ 32 字节（256 bit），否则 HMAC-SHA256 抛 WeakKeyException
 *
 * 对应学习模块：notes/java/13-security
 */
@Component
public class JwtTokenProvider {

    /**
     * 密钥，从 application.yml 注入
     * 教学点：生产环境必须用环境变量注入，不能写在代码或 yml 里
     */
    @Value("${shortlink.jwt.secret}")
    private String secret;

    /**
     * 过期时间（秒）
     */
    @Value("${shortlink.jwt.expiration-seconds:604800}")
    private long expirationSeconds;

    /**
     * 密钥对象（启动时由 secret 字符串生成）
     * 教学点：SecretKey 不可变，线程安全，可作为单例
     */
    private SecretKey signingKey;

    /**
     * 初始化：把字符串密钥转成 SecretKey
     * 教学点：@PostConstruct 在依赖注入完成后、Bean 使用前调用一次
     */
    @PostConstruct
    void init() {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalStateException(
                "shortlink.jwt.secret 必须 ≥ 32 字节（当前 " + keyBytes.length + "）");
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * 生成 JWT token
     *
     * @param username 用户名（写入 sub 字段）
     * @return 形如 "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhbGljZSJ9.xxx" 的字符串
     */
    public String generateToken(String username) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(expirationSeconds);

        return Jwts.builder()
            .subject(username)           // jjwt 0.12.x 新 API（旧版是 setSubject）
            .issuedAt(Date.from(now))    // 签发时间
            .expiration(Date.from(expiry)) // 过期时间
            .signWith(signingKey)        // 用 HS256 + 密钥签名
            .compact();                  // 拼成三段式字符串
    }

    /**
     * 校验 token 并返回用户名
     *
     * @param token JWT 字符串
     * @return 用户名（sub 字段）
     * @throws JwtException 签名错误、过期、格式错误等
     */
    public String validateToken(String token) {
        try {
            // 0.12.x 新 API：parser().verifyWith(key).build().parseSignedClaims(token)
            Jws<Claims> parsed = Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token);

            Claims claims = parsed.getPayload();
            String username = claims.getSubject();

            if (username == null || username.isBlank()) {
                throw new JwtException("token 中 subject 为空");
            }
            return username;
        } catch (ExpiredJwtException e) {
            // 单独捕获，方便上层返回"token 过期"而不是笼统的"无效 token"
            throw new JwtException("token 已过期", e);
        }
    }

    /**
     * 暴露过期时间，方便 LoginResponse 填 expiresIn
     */
    public long getExpirationSeconds() {
        return expirationSeconds;
    }
}
