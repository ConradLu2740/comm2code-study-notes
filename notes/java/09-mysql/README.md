# 09 - MySQL

## 概述

MySQL 是最流行的开源关系型数据库之一,后端开发岗面试必考。本模块从架构、索引、事务、锁、调优、复制六个维度系统梳理,重点理解 InnoDB 引擎底层原理,能够用 Explain 排查慢 SQL 并具备主从架构设计能力。

**关键点速览**

| 模块 | 核心考点 | 难度 |
| --- | --- | --- |
| 架构 | Server 层 + 存储引擎层,InnoDB 为默认引擎 | ★★ |
| 索引 | B+ 树、聚簇/二级索引、最左前缀、覆盖索引 | ★★★★★ |
| 事务 | ACID、隔离级别、MVCC、undo log | ★★★★★ |
| 锁 | Record/Gap/Next-Key Lock、死锁分析 | ★★★★ |
| 调优 | Explain 解读、慢查询、Profile、分库分表 | ★★★★ |
| 复制 | binlog/relay log、GTID、读写分离 | ★★★ |

---

## 核心概念

### 1. MySQL 架构

MySQL 采用「**连接层 + Server 层 + 存储引擎层**」的分层架构,存储引擎采用插件式设计,这是它区别于其他数据库的核心特性。

**连接层**:负责客户端连接管理、认证(用户名/密码/权限)、连接池。`max_connections` 控制最大连接数,生产环境通常配 500~2000。

**Server 层**:涵盖 MySQL 的大多数核心服务功能,所有存储引擎共用。
- **解析器**:词法分析 + 语法分析,生成解析树
- **预处理器**:检查表/列是否存在、权限校验
- **优化器**:基于成本的优化器(CBO),决定使用哪个索引、多表 JOIN 顺序
- **执行器**:调用存储引擎 API 获取数据,处理 `WHERE`/`ORDER BY` 等
- **查询缓存**(MySQL 8.0 已移除):缓存完整 SELECT 结果

**存储引擎层**:负责数据的存储和读取。常见引擎:

| 引擎 | 事务 | 锁粒度 | 关键特性 | 适用场景 |
| --- | --- | --- | --- | --- |
| **InnoDB** | 支持 | 行锁 | MVCC、外键、聚簇索引 | 默认引擎,绝大多数场景 |
| MyISAM | 不支持 | 表锁 | 读快、全文索引、压缩表 | 只读/读多写少、统计报表 |
| MEMORY | 不支持 | 表锁 | 数据存内存、Hash 索引 | 临时表、缓存 |

面试常问:「InnoDB 和 MyISAM 的本质区别?」——InnoDB 支持事务和行锁,采用聚簇索引;MyISAM 不支持事务,只有表锁,索引叶子节点存数据指针。

---

### 2. 索引结构:B+ 树

InnoDB 索引底层使用 **B+ 树**(多路平衡搜索树),而不是二叉平衡树、红黑树或 B 树。

**为什么不用二叉平衡树/红黑树?** 树高 = 磁盘 IO 次数。数据量大时二叉树高度可达 20+,每次 IO 一次 4~16KB 页极浪费。

**为什么不用 B 树?** B 树每个节点都存数据,导致单个节点能容纳的 key 减少,树变高。B+ 树只在叶子节点存数据,非叶子节点只存索引项,**单页能容纳更多 key,树更矮,IO 更少**。同时所有叶子节点形成有序链表,**范围查询性能极佳**(`范围查询时只需要定位到起点然后顺序遍历叶子节点`)。

**B+ 树核心特征**
- 非叶子节点只存主键 + 指针,叶子节点存完整数据(聚簇索引)或主键(二级索引)
- 叶子节点之间用双向链表连接
- 高度通常 2~4 层,千万级数据只需 3~4 次 IO

---

### 3. 聚簇索引 vs 二级索引

**聚簇索引(Clustered Index)**:又叫主键索引,叶子节点存储**完整行数据**。每张表只能有一个聚簇索引(InnoDB 通常是主键;若未定义主键则选第一个非空唯一索引;都没有则生成隐藏的 `row_id`)。

**二级索引(Secondary Index)**:又叫非主键索引,叶子节点存储**主键值**而非完整数据。查询时如果需要其他字段,需要**回表**(先通过二级索引找到主键,再用主键到聚簇索引找完整数据)。

```sql
-- 表结构
CREATE TABLE user (
    id BIGINT PRIMARY KEY,        -- 聚簇索引,叶子存完整行
    name VARCHAR(32),
    age INT,
    INDEX idx_name (name)         -- 二级索引,叶子存 id
);

-- 查询 age
SELECT age FROM user WHERE name = 'tom';
-- 流程:idx_name 找到 id -> 回表 -> 聚簇索引找 age
-- 优化:建立 (name, age) 联合索引,实现覆盖索引,避免回表
```

**最佳实践**:InnoDB 表**强烈建议显式声明自增主键**。理由:(1) 二级索引叶子存主键,主键越小索引越省空间;(2) 自增避免页分裂,插入性能最佳。

---

### 4. 索引类型

| 类型 | 特点 | 示例 |
| --- | --- | --- |
| **主键索引** | 唯一非空,聚簇索引 | `PRIMARY KEY (id)` |
| **唯一索引** | 值唯一,允许 NULL(多个) | `UNIQUE KEY uk_email (email)` |
| **普通索引** | 无约束,加速查询 | `KEY idx_name (name)` |
| **复合索引** | 多列组合,遵循最左前缀 | `KEY idx_a_b_c (a, b, c)` |
| **前缀索引** | 长字符串前缀,节省空间 | `KEY idx_email (email(10))` |

---

### 5. Explain 执行计划

`EXPLAIN` 是 SQL 调优的瑞士军刀,面试必问。关键字段解读:

| 字段 | 含义 | 重点关注 |
| --- | --- | --- |
| **id** | SELECT 序号,id 越大越先执行;相同则从上到下 | 子查询/UNION 调优 |
| **select_type** | 查询类型 | SIMPLE/PRIMARY/SUBQUERY/DERIVED/UNION |
| **table** | 访问的表 |  |
| **type** | 访问类型,**性能从优到劣** | system > const > eq_ref > ref > range > index > ALL |
| **possible_keys** | 可能用到的索引 |  |
| **key** | 实际用到的索引 | **NULL 表示没走索引** |
| **key_len** | 索引使用字节数 | 联合索引命中几列看这里 |
| **rows** | 预估扫描行数 | 越小越好 |
| **Extra** | 额外信息 | 见下 |

**type 性能递减**:system(系统表) > const(主键/唯一索引等值) > eq_ref(JOIN 主键/唯一索引) > ref(非唯一索引) > range(范围) > index(全索引扫描) > **ALL(全表扫描,需优化)**

**Extra 关键值**
- `Using index`:覆盖索引,**不需要回表**,性能优秀
- `Using where`:Server 层过滤数据
- `Using filesort`:无法利用索引排序,**需优化**
- `Using temporary`:使用了临时表,GROUP BY/DISTINCT 没走索引时出现
- `Using join buffer`:JOIN 走 Block Nested Loop

```sql
EXPLAIN SELECT * FROM user WHERE name = 'tom' AND age = 20;
-- 观察 type、key、Extra,目标是 ref/const + Using index
```

---

### 6. 索引优化

**最左前缀原则**:联合索引 `(a, b, c)` 相当于建立了 a、(a,b)、(a,b,c) 三组索引。**跳过最左列则索引失效**。

**覆盖索引**:查询列全部在索引中,无需回表。`(name, age)` 索引 + `SELECT name, age FROM user WHERE name = ?` 即覆盖。

**索引下推 ICP(Index Condition Pushdown)**:MySQL 5.6+ 特性,Server 层下推部分 WHERE 条件到存储引擎层,减少回表次数。

**索引失效场景**(面试必背):
1. 对索引列使用**函数**:`WHERE SUBSTR(name, 1, 3) = 'tom'`
2. **隐式类型转换**:`WHERE phone = 13800000000`(phone 是 VARCHAR,数字走全表)
3. **OR 两侧非都走索引**:`WHERE id = 1 OR name = 'tom'`,name 没索引则全表
4. **IN 通常走索引**,但值过多时优化器可能放弃(>30% 走全表)
5. **联合索引违反最左前缀**
6. **LIKE 以 % 开头**:`LIKE '%tom%'` 失效;`LIKE 'tom%'` 有效
7. **!= / NOT IN / IS NULL**:某些场景下索引失效,看数据分布

---

### 7. 事务与 ACID

**ACID**
- **A (Atomicity) 原子性**:undo log 实现,事务要么全成功要么全回滚
- **C (Consistency) 一致性**:由 AID 共同保证
- **I (Isolation) 隔离性**:锁 + MVCC 实现
- **D (Durability) 持久性**:redo log 实现,事务提交后即使宕机数据不丢

**四种隔离级别**(由低到高)

| 隔离级别 | 脏读 | 不可重复读 | 幻读 | 实现 |
| --- | --- | --- | --- | --- |
| Read Uncommitted (RU) | ✓ | ✓ | ✓ | 几乎不用 |
| Read Committed (RC) | × | ✓ | ✓ | 每次 SELECT 重建 Read View |
| **Repeatable Read (RR)** | × | × | ✓(InnoDB 解决) | 事务开始时建 Read View |
| Serializable | × | × | × | 全表锁,性能差 |

**MySQL InnoDB 默认 RR**,且通过 Next-Key Lock 在一定程度上解决了幻读。

---

### 8. MVCC 原理

**MVCC (Multi-Version Concurrency Control)**:多版本并发控制,通过「读快照」实现非锁定读,提升并发性能。

**三个隐藏字段**
- `trx_id`:事务 ID,记录最后修改该行的事务
- `roll_pointer`:回滚指针,指向 undo log 中的旧版本
- `row_id`:无主键时隐式生成(6 字节)

**undo log 版本链**:每次 UPDATE 生成一条 undo log,通过 roll_pointer 串联,形成版本链。

**Read View**(在 RC 级别每次 SELECT 重建,RR 级别事务开始时建一次)
- `m_ids`:当前活跃事务 ID 集合
- `min_trx_id`:最小活跃事务 ID
- `max_trx_id`:下一个事务 ID
- `creator_trx_id`:创建该 Read View 的事务 ID

**可见性算法**:对于某行 trx_id,
- trx_id == creator_trx_id → 自己改的,可见
- trx_id < min_trx_id → 已提交,可见
- trx_id > max_trx_id → 未开始,不可见
- min_trx_id ≤ trx_id ≤ max_trx_id 且 trx_id 在 m_ids 中 → 未提交,不可见;否则可见

不可见则顺着 roll_pointer 找上一版本,直到找到可见版本。

---

### 9. 锁机制

**行级锁分类**
- **Record Lock(记录锁)**:锁定索引上的某条记录
- **Gap Lock(间隙锁)**:锁定索引记录间的间隙,防止幻读
- **Next-Key Lock(临键锁)= Record Lock + Gap Lock**,InnoDB RR 级别下默认使用

**表级锁**
- **意向锁(Intention Lock)**:事务加行锁前先加意向锁,加快表锁判定。意向共享锁(IS)/意向排他锁(IX)。
- **插入意向锁(Insert Intention Lock)**:多个事务插入不同位置到同一间隙不冲突,提高并发插入性能。

**死锁**:两个事务互相持有对方需要的锁。InnoDB 自动检测,回滚代价小的事务。
- **避免死锁**:统一加锁顺序、减小事务、降低隔离级别、设置锁等待超时 `innodb_lock_wait_timeout`

```sql
-- 查看锁等待
SELECT * FROM information_schema.INNODB_TRX;
SHOW ENGINE INNODB STATUS;
```

---

### 10. SQL 调优

**慢查询日志**
```ini
# my.cnf
slow_query_log = 1
slow_query_log_file = /var/log/mysql/slow.log
long_query_time = 1            # 超过 1 秒记录
log_queries_not_using_indexes = 1
```

`mysqldumpslow -s t -t 10 slow.log` 找出最慢的 10 条 SQL。

**SHOW PROFILE**:MySQL 5.7 之前工具,显示 SQL 各阶段耗时(发送、解析、打开表、执行、关闭表等)。
```sql
SET profiling = 1;
SELECT * FROM user WHERE id = 1;
SHOW PROFILES;
SHOW PROFILE FOR QUERY 1;
```

**分库分表**(数据量 > 500 万行考虑)
- 垂直拆分:按列拆分(冷热数据分离)
- 水平拆分:按行拆分(按 user_id hash、时间范围)
- 中间件:**Sharding-JDBC**(客户端)、MyCat(代理)

---

### 11. 主从复制与读写分离

**主从复制原理**
1. 主库写 binlog(`binlog_format: ROW`)
2. 从库 IO Thread 拉 binlog 写入 relay log
3. 从库 SQL Thread 重放 relay log

**GTID(Global Transaction Identifier)**:MySQL 5.6+ 引入,每个事务有唯一 ID,简化主从切换和故障恢复。`gtid_mode = ON`。

**读写分离**:主库写、从库读,通过 Sharding-JDBC / MyCat / 动态数据源路由。面试常问:「主从延迟如何处理?」——读强制走主库、延迟监控、半同步复制。

---

## 代码示例

### 示例 1:创建合适的索引

```sql
-- 用户表
CREATE TABLE orders (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    status TINYINT NOT NULL DEFAULT 0,
    amount DECIMAL(10,2) NOT NULL,
    created_at DATETIME NOT NULL,
    remark VARCHAR(255),

    -- 联合索引:覆盖"查询某用户未支付订单"的常见业务
    KEY idx_user_status_created (user_id, status, created_at),
    -- 区分度高的列放最左
    KEY idx_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 索引覆盖良好,不需要回表
EXPLAIN SELECT user_id, status, created_at
  FROM orders
 WHERE user_id = 10086 AND status = 0
 ORDER BY created_at DESC
 LIMIT 10;
-- 期望:type=ref, Extra=Using index
```

### 示例 2:排查慢 SQL

```sql
-- 1. 开启慢查询日志(临时)
SET GLOBAL slow_query_log = 1;
SET GLOBAL long_query_time = 1;

-- 2. 用 EXPLAIN 分析可疑 SQL
EXPLAIN SELECT * FROM orders
 WHERE user_id = 10086
   AND DATE(created_at) = '2026-06-21';
-- type=ALL, key=NULL, Extra=Using where
-- 问题:DATE() 函数导致 created_at 索引失效
-- 改写:WHERE created_at >= '2026-06-21' AND created_at < '2026-06-22'
--      (此时 idx_created 生效)
```

### 示例 3:死锁排查与事务控制

```sql
-- 事务 A
START TRANSACTION;
UPDATE account SET balance = balance - 100 WHERE id = 1;
UPDATE account SET balance = balance + 100 WHERE id = 2;
COMMIT;

-- 事务 B(并发)
START TRANSACTION;
UPDATE account SET balance = balance - 50 WHERE id = 2;
UPDATE account SET balance = balance + 50 WHERE id = 1;
COMMIT;
-- 加锁顺序相反 -> 可能死锁
-- 解决:统一先 id 小后 id 大

-- 排查
SELECT * FROM information_schema.INNODB_TRX\G
SHOW ENGINE INNODB STATUS;  -- LATEST DETECTED DEADLOCK
```

### 示例 4:Sharding-JDBC 读写分离配置

```yaml
# application.yml (Spring Boot + ShardingSphere 5.x)
spring:
  shardingsphere:
    datasource:
      names: master,slave0
      master:
        type: com.zaxxer.hikari.HikariDataSource
        driver-class-name: com.mysql.cj.jdbc.Driver
        jdbc-url: jdbc:mysql://master:3306/order_db
        username: root
        password: root
      slave0:
        type: com.zaxxer.hikari.HikariDataSource
        jdbc-url: jdbc:mysql://slave:3306/order_db
        username: root
        password: root
    rules:
      readwrite-splitting:
        data-sources:
          pr_ds:
            write-data-source-name: master
            read-data-source-names: slave0
            load-balancer-name: round_robin
```

---

## 易错点 / 最佳实践

1. **小表也要有主键**。即使业务表只有几百行,InnoDB 数据按主键聚簇存储,UUID 主键会导致页分裂严重、空间膨胀。**主键用 `BIGINT UNSIGNED AUTO_INCREMENT`**,二级索引也省空间。

2. **不要在区分度低的列上建索引**。如 `gender` (男女)、`is_deleted` (布尔),选择性 < 5% 时索引可能比全表扫描更慢。组合索引时把**高区分度列放最左**。

3. **COUNT(*) 在 InnoDB 上没有快速方案**。MyISAM 存了精确行数,InnoDB 因为 MVCC 每行可见性不同只能全表扫描或扫最小索引。**优化**:用 `EXPLAIN` 的 rows 估算、Redis 计数器、额外计数表。

4. **避免长事务**。长事务持有锁、占用 undo log、导致主从延迟。**设置** `innodb_undo_log_truncate = ON` 自动清理 undo,**事务里只做必要操作,快进快出**。

5. **深分页(LIMIT 1000000, 10)优化**。MySQL 会扫前 1000010 行再丢弃。**改用主键游标**:`WHERE id > last_id ORDER BY id LIMIT 10`(延迟关联 + 子查询)。

---

## 面试常见问题

**Q: InnoDB 为什么用 B+ 树而不是 B 树或红黑树?**

A: B+ 树相对 B 树:(1) 叶子节点存数据,非叶子节点只存索引,单页 key 更多,树更矮,IO 更少;(2) 叶子节点形成有序链表,范围查询无需回溯上层。B+ 树相对红黑树/二叉平衡树:同样是 O(log N),但 B+ 树单节点容纳更多 key(多路),磁盘 IO 次数少(树高 2~4 层即可容纳千万级数据)。红黑树 IO 次数 = 树高 = log2N,数据量百万级就达到 20+。

**Q: 什么是聚簇索引和非聚簇索引?回表是什么?**

A: 聚簇索引叶子节点存完整行数据,InnoDB 中即主键索引(每表唯一);非聚簇(二级)索引叶子节点存主键值。回表指二级索引查到主键后,需再用主键到聚簇索引取完整数据。**用覆盖索引可避免回表**。

**Q: 解释 MVCC 的实现原理?**

A: MVCC 通过「数据多版本 + Read View」实现非锁定读。每行有 trx_id 和 roll_pointer 隐藏字段,每次 UPDATE 生成 undo log 形成版本链。Read View 记录当前活跃事务集合,查询时按规则判断版本可见性:已提交版本对当前事务可见,未提交版本顺着 roll_pointer 找上一版本。RC 每次 SELECT 重建 Read View,RR 事务开始时建一次。

**Q: RR 级别下 InnoDB 如何解决幻读?**

A: 通过 Next-Key Lock(行锁 + 间隙锁)。`SELECT * FROM t WHERE id > 100 FOR UPDATE` 不仅锁住满足条件的行,还锁住 id=100 之后的间隙,阻止其他事务 INSERT 该区间的数据,从而避免幻读。注意:只有当前读(加锁读)才防幻读,普通 SELECT 走快照读仍可能读到新插入行(因此 RR 的「幻读」和 ANSI 标准定义有差异)。

**Q: 慢 SQL 怎么排查?**

A: 五步法:(1) 开启慢查询日志 `slow_query_log=1` + `long_query_time=1`,定位慢 SQL;(2) `EXPLAIN` 看执行计划,重点看 type(避免 ALL)、Extra(避免 filesort/temporary)、key(避免 NULL);(3) `SHOW PROFILE` 或 `performance_schema` 看各阶段耗时;(4) 索引优化(加索引、改写 SQL、利用覆盖索引);(5) 表结构优化、读写分离、分库分表。

**Q: 什么是索引下推 ICP?**

A: MySQL 5.6 引入。Server 层把部分 WHERE 条件下推到存储引擎层过滤,减少回表次数。例:`(a, b)` 索引 + `WHERE a LIKE 'x%' AND b = 1`,无 ICP 时先按 `a LIKE` 索引查出主键回表取 b 再过滤;有 ICP 时存储引擎直接用 b 过滤,只回表符合条件的主键。

**Q: 主从复制原理?主从延迟怎么处理?**

A: 原理:主库写 binlog → 从库 IO Thread 拉 binlog 写 relay log → 从库 SQL Thread 重放。延迟原因:从库单线程重放(5.6 之后多线程)、大事务、从库性能差、网络延迟。处理:(1) 强制读主(关键业务);(2) 半同步复制 `rpl_semi_sync_master_wait_for_slave_count`;(3) 缓存写后读;(4) 业务容忍最终一致。

**Q: binlog 有几种格式?生产用什么?**

A: 三种:STATEMENT(记 SQL,体积小但主从不一致风险)、ROW(记行变更,精确但体积大)、MIXED(混合)。**生产强烈推荐 ROW**,主从一致性强、数据可恢复性强(误删可闪回),MySQL 8.0 默认 ROW。

**Q: UUID 主键和自增主键怎么选?**

A: 自增 BIGINT 优势:紧凑(8 字节)、顺序写避免页分裂、二级索引省空间。UUID(36 字符串)优势:全局唯一适合分布式 ID、合并数据无冲突。**推荐:分布式场景用雪花算法(Snowflake,64 位 long 形如 `181563472341348352`)**——既有序又有时间信息,且是数字。

**Q: 为什么推荐 Sharding-JDBC 而不是 MyCat?**

A: Sharding-JDBC 客户端嵌入,无中间层,性能损耗小、运维简单、与应用同语言(JDBC 增强);MyCat 是代理,需要额外部署,所有 SQL 走代理有单点风险。**中小规模用 Sharding-JDBC**,超大集群或异构数据库(MySQL+PG)选 MyCat。

---

## 延伸阅读

- **书**:《高性能 MySQL》(Baron Schwartz 等)—— MySQL 调优圣经,前 5 章必读
- **书**:《MySQL 是怎么运行的:从根儿上理解 MySQL》(小孩子4919)—— 国内口碑极好,讲清 InnoDB 原理
- **官方**:[MySQL 8.0 Reference Manual - InnoDB](https://dev.mysql.com/doc/refman/8.0/en/innodb-storage-engine.html)—— 最权威的 InnoDB 文档
- **视频**:尚硅谷 MySQL 高级 + 小林 coding 「图解 MySQL」—— 面试考点覆盖全
- **实战**:执行 `EXPLAIN ANALYZE`(MySQL 8.0.18+)查看真实执行计划,而非估算
