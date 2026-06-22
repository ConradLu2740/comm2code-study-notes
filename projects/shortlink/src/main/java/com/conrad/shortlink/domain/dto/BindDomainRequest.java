package com.conrad.shortlink.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 绑定自定义域名请求 DTO
 *
 * 教学点：
 * 1. @NotBlank 校验非空（比 @NotNull 更严格，禁空格字符串）
 * 2. @Pattern 正则校验：域名格式合法性在 Controller 入口处就拦掉
 *    - 这里只允许子域名（必须含点），禁止裸域名 "localhost" 这种
 * 3. DTO 只承载"输入数据"，不承载业务规则（业务规则在 Service 里）
 */
public class BindDomainRequest {

    @NotBlank(message = "域名不能为空")
    @Size(max = 253, message = "域名长度不能超过 253")
    @Pattern(regexp = "^[a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(\\.[a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)+$",
             message = "域名格式不合法，例如 s.example.com")
    private String domain;

    @NotBlank(message = "短码不能为空")
    @Size(max = 16, message = "短码长度不能超过 16")
    private String shortCode;

    public BindDomainRequest() {}

    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }

    public String getShortCode() { return shortCode; }
    public void setShortCode(String shortCode) { this.shortCode = shortCode; }
}
