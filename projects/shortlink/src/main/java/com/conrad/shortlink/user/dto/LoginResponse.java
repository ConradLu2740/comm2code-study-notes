package com.conrad.shortlink.user.dto;

/**
 * 登录响应 DTO
 *
 * 教学点：
 * 1. token 是 JWT 字符串，客户端存到 localStorage / Cookie
 * 2. expiresIn 是秒数（不是绝对时间戳），方便客户端直接 setTimeout 续签
 * 3. user 字段嵌入了基本信息，避免客户端登录后还要再调 /me 接口
 * 4. 不返回 passwordHash：服务端任何响应都不应泄露密码字段
 *
 * 对应学习模块：notes/java/13-security (JWT 三段式)
 */
public class LoginResponse {

    private String token;

    /**
     * 过期时间（秒），例如 604800 = 7 天
     */
    private long expiresIn;

    private UserResponse user;

    public LoginResponse() {}

    public LoginResponse(String token, long expiresIn, UserResponse user) {
        this.token = token;
        this.expiresIn = expiresIn;
        this.user = user;
    }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    public long getExpiresIn() { return expiresIn; }
    public void setExpiresIn(long expiresIn) { this.expiresIn = expiresIn; }

    public UserResponse getUser() { return user; }
    public void setUser(UserResponse user) { this.user = user; }
}
