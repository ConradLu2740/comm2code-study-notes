package com.conrad.shortlink.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 注册请求 DTO
 *
 * 教学点：
 * 1. DTO 隔离 API 层和 Entity：Entity 含 passwordHash 等内部字段，不应直接暴露
 * 2. @NotBlank：拒绝 null + 空字符串 + 全空格（比 @NotEmpty 严格）
 * 3. @Email：Jakarta Bean Validation 提供的邮箱格式校验
 * 4. @Size：长度限制，防止超长输入撑爆数据库（username 64, email 128）
 * 5. 配合 @Valid 在 Controller 触发校验，失败抛 MethodArgumentNotValidException
 *
 * 对应学习模块：notes/java/08-spring (Bean Validation)
 */
public class RegisterRequest {

    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 64, message = "用户名长度必须在 3-64 之间")
    private String username;

    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    @Size(max = 128, message = "邮箱长度不能超过 128")
    private String email;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 64, message = "密码长度必须在 6-64 之间")
    private String password;

    public RegisterRequest() {}

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
