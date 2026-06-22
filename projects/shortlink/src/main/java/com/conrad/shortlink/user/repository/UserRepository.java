package com.conrad.shortlink.user.repository;

import com.conrad.shortlink.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

/**
 * 用户数据访问层
 *
 * 教学点：
 * 1. 继承 JpaRepository<User, Long> 自动获得 CRUD
 * 2. 方法命名规则查询：
 *    - findByUsername → SELECT * FROM users WHERE username = ?
 *    - existsByUsername → SELECT EXISTS(SELECT 1 FROM users WHERE username = ?)
 * 3. exists* 比 find* 更高效：只判断存在性，不加载实体
 * 4. 用 Optional<T> 包装防空值，调用方必须显式处理"找不到"的情况
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * 按用户名查询
     */
    Optional<User> findByUsername(String username);

    /**
     * 按邮箱查询（备用：未来支持"邮箱登录"时用）
     */
    Optional<User> findByEmail(String email);

    /**
     * 用户名是否已存在（注册时校验）
     */
    boolean existsByUsername(String username);

    /**
     * 邮箱是否已存在（注册时校验）
     */
    boolean existsByEmail(String email);
}
