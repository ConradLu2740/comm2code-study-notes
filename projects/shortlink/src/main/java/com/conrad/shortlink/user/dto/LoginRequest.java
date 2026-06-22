package com.conrad.shortlink.user.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 登录请求 DTO
 *
 * 教学点：
 * 1. 登录接口只收 username + password 两个字段（最小化攻击面）
 * 2. 不做密码复杂度校验（复杂度在注册时已校验）
 * 3. 不查数据库就判断用户名是否存在 → 统一用"用户名或密码错误"返回
 *    - 防用户名枚举攻击：攻击者无法通过错误信息判断"用户名是否存在"
 */
public class LoginRequest {

    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;

    public LoginRequest() {}

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
