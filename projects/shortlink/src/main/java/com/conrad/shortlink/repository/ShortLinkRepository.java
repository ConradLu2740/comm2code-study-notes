package com.conrad.shortlink.repository;

import com.conrad.shortlink.entity.ShortLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.Optional;

/**
 * 短链接数据访问层
 *
 * 教学点：
 * 1. 继承 JpaRepository 自动获得 CRUD 方法（save / findById / findAll / deleteById 等）
 * 2. 方法命名规则查询：findByShortCode → SELECT * FROM short_link WHERE short_code = ?
 * 3. @Modifying + @Query 用于自定义 UPDATE 语句（避免先查后改的性能损失）
 * 4. Optional<T> 是 Java 8+ 推荐的防空值包装
 *
 * 对应学习模块：notes/java/08-spring (Spring Data JPA)
 */
@Repository
public interface ShortLinkRepository extends JpaRepository<ShortLink, Long> {

    /**
     * 根据短码查询
     */
    Optional<ShortLink> findByShortCode(String shortCode);

    /**
     * 判断短码是否存在
     */
    boolean existsByShortCode(String shortCode);

    /**
     * 原子自增点击次数（关键！用 SQL 直接 +1 避免并发问题）
     */
    @Modifying
    @Query("UPDATE ShortLink s SET s.clickCount = s.clickCount + 1 WHERE s.shortCode = :code")
    int incrementClickCount(@Param("code") String shortCode);
}
