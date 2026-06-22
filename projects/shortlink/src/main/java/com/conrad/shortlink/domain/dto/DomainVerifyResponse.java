package com.conrad.shortlink.domain.dto;

import java.time.Instant;

/**
 * 域名所有权验证响应 DTO
 *
 * 教学点：域名所有权验证（DNS TXT Record Verification）的标准流程
 *
 * 1. 用户在短链服务提交域名 s.example.com
 * 2. 服务生成一个随机 token，要求用户在 DNS 服务商添加一条 TXT 记录：
 *      主机记录：_shortlink-verify.s.example.com
 *      记录类型：TXT
 *      记录值：<随机 token>
 * 3. 服务通过 DNS 查询该 TXT 记录，能查到匹配 token 即视为用户拥有该域名
 *
 * 为什么是 TXT 而不是 A/CNAME？
 *   - A 记录只能查 IP，不便携带任意字符串
 *   - CNAME 是别名，会和实际服务解析冲突
 *   - TXT 是 DNS 专门为"任意文本信息"设计的记录类型，最适合做所有权验证
 *
 * 业界案例：Let's Encrypt 用同样的方式验证域名所有权（challenge）
 *           Google Search Console / GitHub Pages 自定义域名也是这套机制
 */
public class DomainVerifyResponse {

    /** 完整域名 */
    private String domain;

    /** 状态：PENDING / ACTIVE / FAILED */
    private String status;

    /** 当前验证状态描述（前端直接展示给用户） */
    private String message;

    /** 用户需要在 DNS 服务商添加的 TXT 主机记录 */
    private String txtHost;

    /** TXT 记录值（即 verifyToken） */
    private String txtValue;

    /** 验证时间（如果已验证） */
    private Instant verifiedAt;

    /** 下次可重试时间（DNS 缓存通常 30s-5min） */
    private Instant nextRetryAt;

    public DomainVerifyResponse() {}

    public DomainVerifyResponse(String domain, String status, String message,
                                 String txtHost, String txtValue,
                                 Instant verifiedAt, Instant nextRetryAt) {
        this.domain = domain;
        this.status = status;
        this.message = message;
        this.txtHost = txtHost;
        this.txtValue = txtValue;
        this.verifiedAt = verifiedAt;
        this.nextRetryAt = nextRetryAt;
    }

    // ===== Getters / Setters =====

    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getTxtHost() { return txtHost; }
    public void setTxtHost(String txtHost) { this.txtHost = txtHost; }

    public String getTxtValue() { return txtValue; }
    public void setTxtValue(String txtValue) { this.txtValue = txtValue; }

    public Instant getVerifiedAt() { return verifiedAt; }
    public void setVerifiedAt(Instant verifiedAt) { this.verifiedAt = verifiedAt; }

    public Instant getNextRetryAt() { return nextRetryAt; }
    public void setNextRetryAt(Instant nextRetryAt) { this.nextRetryAt = nextRetryAt; }
}
