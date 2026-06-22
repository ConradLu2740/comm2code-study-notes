package com.conrad.shortlink.domain.repository;

import com.conrad.shortlink.domain.entity.CustomDomain;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

/**
 * 自定义域名数据访问层
 *
 * 教学点：
 * 1. Spring Data JPA 方法名自动派生查询（Derived Query）
 *    - findByDomain → WHERE domain = ?
 *    - findByUserId → WHERE user_id = ?
 *    - existsByDomain → SELECT 1 WHERE domain = ? LIMIT 1
 * 2. 返回 Optional<T> 强制调用方处理"找不到"的场景，比返回 null 安全
 * 3. findByUserId 用于"我的域名列表"接口；findByDomain 用于短链跳转时反查
 *
 * 对应学习模块：notes/java/08-spring (Spring Data JPA)
 */
@Repository
public interface CustomDomainRepository extends JpaRepository<CustomDomain, Long> {

    /**
     * 按域名精确查询（短链重定向时用：用户访问 https://s.example.com/abc
     * 需要先查到 s.example.com 对应的短码，再去 short_link 表解析）
     */
    Optional<CustomDomain> findByDomain(String domain);

    /**
     * 查某用户的所有自定义域名（"我的域名"页面用）
     */
    List<CustomDomain> findByUserId(Long userId);

    /**
     * 绑定前查重，避免 race condition（虽然 unique index 已经兜底）
     */
    boolean existsByDomain(String domain);
}
