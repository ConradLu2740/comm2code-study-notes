# 10 - Redis

## 概述

**Redis**（Remote Dictionary Server）是一个基于内存的 Key-Value 存储系统，支持多种数据结构（String、List、Hash、Set、ZSet、Stream 等），凭借亚毫秒级的响应速度和丰富的数据模型，成为后端高并发场景下的核心组件。它既是缓存、计数器、分布式锁的事实标准，也能充当轻量级消息队列。

**关键点速览**

| 维度 | 要点 |
|------|------|
| 定位 | 内存 KV 数据库，可持久化、支持复制与集群 |
| 数据结构 | 5+1 基础类型（String/List/Hash/Set/ZSet/Stream）+ 特殊类型（Bitmap/HyperLogLog/GEO） |
| 持久化 | RDB 快照 + AOF 日志，4.0 后支持混合持久化 |
| 高可用 | 主从复制 + Sentinel（哨兵）+ Cluster（集群，16384 槽位） |
| 单机性能 | 命令执行单线程（6.0 后 IO 多线程可开启），QPS 10w+ |
| 内存管理 | 8 种淘汰策略，含 allkeys-lfu（4.0+） |
| 典型用途 | 缓存、分布式锁、计数器、排行榜、消息队列、限流 |

## 核心概念

### Redis 是什么

Redis 把数据放在内存中，通过单线程命令循环（避免锁竞争）+ IO 多路复用（epoll/kqueue）拿到极致吞吐。和 MySQL 相比：

- **MySQL**：数据落盘，B+ 树索引，适合复杂查询和事务
- **Redis**：数据在内存，单 key 操作亚毫秒，适合简单访问模型的高频读写

二者常搭配使用：**MySQL 做"权威存储"，Redis 做"热点缓存"**。这是后端面试几乎必问的"缓存架构"原型。

### 数据结构（5+1 基础）

#### String（SDS）

Redis 没用 C 原生字符串（`char*`），而是自研 **SDS（Simple Dynamic String）**：

```c
struct sdshdr {
    int len;        // 已用长度，O(1) 取
    int free;       // 剩余空间
    char buf[];     // 字节数组
};
```

SDS 的优势：
1. **O(1) 取长度**：直接读 `len`，不用遍历（这正是 `STRLEN` 命令快的原因）
2. **二进制安全**：用 `len` 判断结束，可以存 `\0`，C 字符串不行
3. **预分配 + 惰性释放**：扩容时多给一倍空间，避免频繁 realloc
4. **杜绝缓冲区溢出**：拼接时先检查 `free` 是否够

字符串最大 512MB。常见命令：`SET/GET/MSET/MGET/INCR/SETNX/SETEX`。

#### List（quicklist，3.2+）

早期是 `linkedlist` 或 `ziplist`，元素多了会转 `linkedlist`。3.2 引入 **quicklist**：

```
quicklist = linkedlist of ziplist
```

每个节点是一个小 `ziplist`，节点间用双向指针串起来。这样既保留 `ziplist` 的内存紧凑，又避免单个 `ziplist` 过大。常见用途：消息队列（`LPUSH + BRPOP`）、最新列表（`LTRIM` 配合）。

#### Hash

存储 field-value 映射，底层两种编码：

- **listpack（7.0 后替代 ziplist）**：field 和 value 都用 listpack entry 紧凑存储，元素少、value 小时用
- **hashtable**：field 多或 value 大时升级

阈值由 `hash-max-listpack-entries`（默认 128）和 `hash-max-listpack-value`（默认 64 字节）控制。常用作**对象缓存**（`HSET user:1 name "xx" age 18`），相比把整个对象 JSON 序列化进 String，能局部更新。

#### Set

底层是 **intset（整数集合）** 或 **hashtable**：

- 元素全是整数 → `intset`，按升序紧凑存储
- 否则 → `hashtable`，value 全部为 NULL

支持集合运算：`SINTER`（交集）、`SUNION`（并集）、`SDIFF`（差集），时间复杂度 O(N)，可指定 `STORE` 直接写到目标 key。常用：标签、共同好友、抽奖（`SRANDMEMBER`）。

#### ZSet（Sorted Set）

最复杂也最常用，**跳表（SkipList）+ dict + listpack**：

```
zset {
    dict   dict;       // member -> score，O(1) 查分
    zskiplist *zsl;    // 按 score 排序的跳表
}
```

- **dict** 解决"按 member 查 score"是 O(1)
- **跳表** 解决"按 score 查 topN / 查排名"是 O(logN)
- 元素少时用 **listpack** 编码（节省内存）

跳表是有序集合的高频考点：**为什么不用红黑树？** 因为跳表实现简单、范围查询天然友好、并发友好（局部锁）。常见用途：**排行榜**（`ZADD`、`ZREVRANGE WITHSCORES`、`ZINCRBY`）、延迟队列（score 是执行时间戳）。

#### Stream（5.0+）

弥补 List 做消息队列的不足（无 ACK、无消费组），类似 Kafka 但更轻：

- 每条消息有唯一 ID（毫秒时间戳 + 序列号）
- 支持消费组（XREADGROUP）、ACK（XACK）
- 阻塞读（XREAD BLOCK）

适用场景：轻量级消息队列、事件溯源。重型场景还是建议 Kafka/RocketMQ。

### 特殊类型

| 类型 | 本质 | 用途 | 示例 |
|------|------|------|------|
| **Bitmap** | 字符串按 bit 操作 | 用户签到、日活统计 | `SETBIT user:sign:202606 100 1`、`BITCOUNT` |
| **HyperLogLog** | 概率数据结构，标准误差 0.81% | UV（独立访客）统计 | `PFADD`、`PFCOUNT`（12KB 存亿级数据） |
| **GEO** | ZSet + GeoHash 编码 | 附近的人、门店搜索 | `GEOADD`、`GEOSEARCH`（6.2+） |

注意 Bitmap 在用户量大（亿级）时节省内存效果显著，但只适合二值场景。

### 持久化

#### RDB（Redis Database）

内存快照，存为二进制 dump 文件（`dump.rdb`）。

- **触发方式**：`save`（阻塞，主线程）、`bgsave`（fork 子进程，主线程继续服务，**推荐**）、`save` 配置（`save 900 1` 表示 900 秒内至少 1 次修改就触发）
- **原理**：fork 用 **写时复制（COW）**，子进程拿一份一样的内存快照，几乎不耗额外内存
- **优点**：文件紧凑、恢复快、适合备份
- **缺点**：可能丢失最后一次快照之后的数据

#### AOF（Append Only File）

每条写命令追加到文件，恢复时重放命令。

- **fsync 策略**：`always`（每条都 fsync，慢但最安全）、`everysec`（每秒一次，**默认，折中**）、`no`（交给 OS）
- **AOF 重写（bgrewriteaof）**：AOF 文件会越来越大，重写用 fork 子进程遍历内存生成"等价的最短命令序列"
- **优点**：数据更安全（最多丢 1 秒）
- **缺点**：文件大、恢复慢

#### 4.0 混合持久化

RDB 恢复快但丢数据，AOF 安全但慢。混合模式：

- AOF 文件前半段是 RDB 格式（某次重写时的全量快照）
- 后半段是增量 AOF 命令

恢复时先加载 RDB 部分，再重放 AOF 增量。**既快又安全，是生产推荐配置**：

```conf
aof-use-rdb-preamble yes
```

### 高可用

#### 主从复制

一主多从，主写从读，支撑读扩展和数据冗余。

- **全量复制**：从节点首次连接或断线重连 offset 不在 `repl_backlog` 时发生。主 fork 子进程生成 RDB 发给从，从清空旧数据后加载
- **增量复制**：从节点断线重连后，主把 `repl_backlog` 缓冲区里的写命令发给从，继续同步（Redis 2.8+）

主从复制是异步的，**主挂了可能丢少量数据**，需要 Sentinel 或 Cluster 兜底。

#### 哨兵 Sentinel

`哨兵`是独立进程，至少部署 3 个节点组成集群，做四件事：

1. **监控（Monitoring）**：心跳检测主从节点
2. **选主（Leader Election）**：Raft 思路，多数派同意的哨兵成为 Leader
3. **通知（Notification）**：通过 pub/sub 通知客户端主从切换
4. **自动故障转移（Automatic Failover）**：从健康的从节点里选新主，调整其他从指向新主

客户端通过 `sentinel get-master-addr-by-name` 拿到当前主节点地址，连接断开时主动重连。

#### Cluster（集群）

解决"写扩展"和"海量数据"问题。**6.0+ 推荐**生产方案。

- **数据分片**：16384 个哈希槽（**不是 16384，而是 2^14**，因为心跳包大小权衡），`slot = CRC16(key) % 16384`
- **虚拟哈希槽**：节点增减时只需迁移槽，不用重新哈希所有 key（对比一致性哈希的优势）
- **节点通信**：每个节点每秒 ping 其他节点，用 **Gossip 协议** 传播集群状态
- **故障检测**：半数以上主节点认为某主节点下线（`cluster-node-timeout`），触发故障转移，从节点里选新主

**生产最佳实践**：3 主 3 从起步，节点数尽量不超过 100（避免 Gossip 开销过大）。

### 缓存三大问题

#### 1. 缓存穿透（Cache Penetration）

**查一个根本不存在的数据**，缓存和 DB 都没有，每次请求都打到 DB。

解决：
- **布隆过滤器（Bloom Filter）**：在缓存前加一层，拦截一定不存在的 key（误判率可接受）
- **空值缓存**：把 `null` 也缓存起来（设短 TTL，比如 60s）

#### 2. 缓存击穿（Cache Breakdown）

**热点 key 过期瞬间**，大量请求同时打到 DB。

解决：
- **互斥锁（分布式锁）**：只让一个线程去 DB 查并回填，其他线程等待
- **逻辑过期**：value 里存过期时间字段，业务判断过期后才去 DB 查并异步更新缓存（不阻塞读）

#### 3. 缓存雪崩（Cache Avalanche）

**大量 key 同时过期** 或 Redis 整体宕机，请求洪峰打到 DB。

解决：
- **随机过期时间**：基础 TTL ± 随机偏移（±10%）
- **熔断降级**：DB 压力大时返回兜底数据
- **高可用**：Redis Cluster + 多副本

### 数据一致性

三种经典模式：

| 模式 | 读流程 | 写流程 | 一致性 |
|------|--------|--------|--------|
| **Cache Aside**（最常用） | 读 cache，miss 则读 DB 并回填 | 先更 DB，再**删缓存** | 最终一致 |
| **Read/Write Through** | 读 cache，cache 自己负责同步 DB | 写 cache，cache 自己同步 DB | 强一致（实现复杂） |
| **Write Behind** | 读 cache | 异步批量写 DB | 弱一致，性能最高 |

**Cache Aside 是工业界事实标准**，核心是"先更 DB 再删缓存"，而不是反过来。原因：如果先删缓存再更 DB，中间有请求进来会把旧值回填到缓存。

但 Cache Aside 也有边界场景下的一致性问题（比如"先更 DB，再删缓存"中间发生删除失败），生产环境常配合：
- 延迟双删（删 → 等 500ms → 再删）
- 监听 binlog 异步删除（Canal 等）
- 设置较短 TTL 兜底

### Redis 6 多线程

Redis 4.0 后已经支持惰性删除和异步删除等多线程，但**命令执行一直是单线程**。6.0 引入真正的 **IO 多线程**：

- 默认**关闭**（`io-threads-do-reads no`）
- 启用方式：`io-threads 4`（线程数，建议不超过 CPU 核数）
- **只负责网络读写**（解析请求、写回响应），命令执行仍由主线程串行

为什么命令执行不改成多线程？因为单线程 + 内存访问已经把延迟压到极限，多线程反而引入锁竞争和上下文切换。**Redis 6 多线程主要是为了解决高并发下网络 IO 瓶颈**（比如单机 10w+ QPS 时网络帧解析拖慢主线程）。

### 内存淘汰策略

当 `maxmemory` 满时，Redis 按策略淘汰 key：

| 策略 | 作用范围 | 淘汰规则 |
|------|----------|----------|
| `noeviction` | - | 不淘汰，写命令返回错误（默认） |
| `allkeys-lru` | 所有 key | LRU（最近最少使用） |
| `allkeys-lfu` | 所有 key | **LFU（最不经常使用）**，4.0+ |
| `allkeys-random` | 所有 key | 随机淘汰 |
| `volatile-lru` | 设置了 TTL 的 key | LRU |
| `volatile-lfu` | 设置了 TTL 的 key | LFU |
| `volatile-random` | 设置了 TTL 的 key | 随机 |
| `volatile-ttl` | 设置了 TTL 的 key | 优先淘汰 TTL 短的 |

**生产推荐**：
- 缓存场景（不丢数据重要）：`allkeys-lru` 或 `allkeys-lfu`
- 业务数据存 Redis（不能丢）：`volatile-lru` + 业务保证 TTL
- LFU 比 LRU 更能抗突发流量（热点 key 不容易被冷数据挤掉），**4.0+ 推荐 allkeys-lfu**

## 代码示例

### 示例 1：String 计数器（限流 / 点赞）

```java
@Service
public class LikeService {
    @Autowired
    private StringRedisTemplate redis;

    public boolean like(long userId, long articleId) {
        String key = "article:like:" + articleId;
        // 点赞数 +1，返回最新点赞数（INCR 是原子操作）
        Long count = redis.opsForValue().increment(key);
        // 记录用户点赞关系（SetNX 保证幂等）
        redis.opsForSet().add("article:liked:" + articleId, String.valueOf(userId));
        return count != null && count > 0;
    }
}
```

`INCR` 是 O(1) 原子操作，比 `GET/SET` 少一次往返，常用来做分布式计数器、限流（`INCR` + 过期时间 = 滑动窗口简易版）。

### 示例 2：Hash 缓存用户信息

```java
@Service
public class UserCacheService {
    @Autowired
    private StringRedisTemplate redis;

    public UserVO getUser(long id) {
        String key = "user:" + id;
        // 1. 查 Hash
        Map<Object, Object> entries = redis.opsForHash().entries(key);
        if (!entries.isEmpty()) {
            return toVO(entries);
        }
        // 2. 缓存 miss，查 DB
        UserVO user = userMapper.selectById(id);
        if (user != null) {
            Map<String, String> map = toMap(user);
            redis.opsForHash().putAll(key, map);
            redis.expire(key, Duration.ofHours(1)); // 1 小时过期
        }
        return user;
    }

    public void updateUser(UserVO user) {
        // 先更 DB
        userMapper.updateById(user);
        // 再删缓存（Cache Aside）
        redis.delete("user:" + user.getId());
    }
}
```

Hash 比 String(JSON) 灵活：可以局部更新字段（如只更新昵称），不用序列化整个对象。

### 示例 3：ZSet 实现排行榜

```java
@Service
public class RankService {
    @Autowired
    private StringRedisTemplate redis;

    private static final String RANK_KEY = "rank:game:weekly";

    public void addScore(long userId, double score) {
        // 加分
        redis.opsForZSet().incrementScore(RANK_KEY, String.valueOf(userId), score);
        redis.expire(RANK_KEY, Duration.ofDays(7)); // 一周清一次
    }

    public List<RankVO> topN(int n) {
        // 取分数最高的 N 个（倒序）
        Set<ZSetOperations.TypedTuple<String>> tuples =
                redis.opsForZSet().reverseRangeWithScores(RANK_KEY, 0, n - 1);
        return tuples.stream()
                .map(t -> new RankVO(Long.parseLong(t.getValue()), t.getScore()))
                .collect(Collectors.toList());
    }

    public Long getRank(long userId) {
        // 排名（0 开始，0 是第一名）
        return redis.opsForZSet().reverseRank(RANK_KEY, String.valueOf(userId));
    }
}
```

ZSet 是排行榜的事实标准，`ZREVRANGE WITHSCORES` 直接拿 topN，`ZSCORE` 查分，`ZREVRANK` 查排名，都是 O(logN)。

### 示例 4：Redis 分布式锁（SETNX + Lua 释放）

```java
public boolean tryLock(String key, String value, long expireSeconds) {
    // SET key value NX EX expireSeconds 原子操作
    Boolean ok = redis.opsForValue()
            .setIfAbsent(key, value, Duration.ofSeconds(expireSeconds));
    return Boolean.TRUE.equals(ok);
}

public void unlock(String key) {
    // 用 Lua 脚本保证"判断 value + 删除"的原子性，避免误删别人的锁
    String script = "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                    "return redis.call('del', KEYS[1]) else return 0 end";
    redis.execute(new DefaultRedisScript<>(script, Long.class),
                  Collections.singletonList(key),
                  "client-uuid-" + Thread.currentThread().getId());
}
```

注意：`SETNX + EXPIRE` 是两条命令，非原子，**生产必须用 `SET NX EX` 一条命令**。释放锁必须用 Lua，否则可能释放别人的锁。

## 易错点 / 最佳实践

1. **慎用 Keys / O(N) 命令**：`KEYS *`、`HGETALL`、`SMEMBERS` 等是大数据量下的"性能杀手"，能阻塞主线程。生产环境用 `SCAN` 系列游标命令替代 `KEYS`，用 `HSCAN` 替代 `HGETALL`。

2. **避免大 Key（BigKey）**：单个 String > 10KB，或 Hash/List/Set/ZSet 元素超过 5000。BigKey 会导致：
   - 持久化时主线程阻塞（`SAVE`、AOF 重写）
   - 网络传输慢
   - 删除阻塞（用 `UNLINK` 异步删除代替 `DEL`）
   - **删除用 `UNLINK key`**（6.0+），主线程立刻返回，后台线程回收内存。

3. **必须设置 maxmemory 和淘汰策略**：默认 `noeviction` 满后写命令报错，生产环境务必改成 `allkeys-lru` 或 `allkeys-lfu`。同时建议设置 `maxmemory` 为机器内存的 60%~70%，留给 fork 子进程（持久化时 COW 需要）。

4. **缓存三大问题的兜底**：随机过期时间（雪崩）、布隆过滤器（穿透）、互斥锁/逻辑过期（击穿）。生产环境最好配上**熔断降级**（Sentinel/Hystrix），DB 兜底返回。

5. **生产禁用 `KEYS`**：用 `SCAN 0 MATCH user:* COUNT 100` 游标分批遍历。**禁用 `FLUSHDB` / `FLUSHALL`**（可以重命名为 `flushdb-rename-command`）。

## 面试常见问题

**Q1: Redis 为什么快？**

A: 三点：(1) 内存访问（纳秒级，比磁盘快 10w 倍）；(2) 单线程命令执行（避免锁竞争和上下文切换）；(3) IO 多路复用（epoll/kqueue，单线程同时监听多个连接）。Redis 6.0 后 IO 多线程可启用，但命令执行仍单线程。

**Q2: Redis 是单线程还是多线程？**

A: **命令执行是单线程**（这是核心设计，避免锁竞争），但 6.0 后**网络 IO 是多线程**（`io-threads`）。此外还有后台线程负责**惰性删除、异步删除、AOF flush**。所以严格说是"多线程整体框架 + 单线程命令执行"。

**Q3: 缓存穿透、击穿、雪崩的区别和解决方案？**

A: 
- **穿透**：查不存在的数据。解决：布隆过滤器、空值缓存。
- **击穿**：单个热点 key 过期。解决：互斥锁（只让一个线程查 DB）、逻辑过期（不阻塞读）。
- **雪崩**：大量 key 同时过期。解决：随机过期时间、熔断降级、高可用。

**Q4: Redis 和 Memcached 的区别？**

A: 
- **数据结构**：Redis 支持 5+1 基础类型和特殊类型；Memcached 只支持 String。
- **持久化**：Redis 支持 RDB/AOF；Memcached 不支持（重启丢数据）。
- **集群**：Redis Cluster 原生支持；Memcached 需要客户端分片。
- **线程**：Redis 单线程命令执行；Memcached 多线程。
- **淘汰策略**：Redis 有 8 种；Memcached 只有 LRU。

**Q5: Redis 的 ZSet 为什么用跳表而不是红黑树？**

A: (1) 跳表实现简单，代码量约为红黑树的 1/3；(2) 范围查询天然支持（顺序遍历链表即可），红黑树范围查询要中序遍历，效率低；(3) 并发友好，红黑树旋转操作难以并行，跳表局部加锁即可；(4) 内存占用相近（Redis 作者 Antirez 在博客里有详细对比）。

**Q6: Redis 持久化如何选型？**

A: 
- **纯缓存**：可关掉持久化（性能最好），重启从 DB 回填。
- **一般业务**：RDB 即可（性能 + 数据安全折中）。
- **数据敏感**：AOF + `everysec`（最多丢 1 秒）。
- **生产推荐**：**混合持久化**（RDB + AOF），RDB 快速恢复，AOF 兜底安全。

**Q7: Redis Cluster 是怎么分片的？**

A: 16384 个哈希槽（`slot = CRC16(key) % 16384`），每个 master 节点负责一部分槽。客户端访问任意节点，如果 key 的槽不在该节点上，会返回 `-MOVED` 错误指引客户端跳转。**不是一致性哈希**，而是**虚拟哈希槽**，节点增减时只需迁移槽，不用重新哈希所有 key。

**Q8: Redis 分布式锁怎么实现？Redisson 比手写好在哪？**

A: 手写：SET key value NX EX + Lua 释放。Redisson 在此基础上提供：
- **可重入**（基于 Lua 脚本计数 + Hash 结构）
- **看门狗自动续期**（默认 30s 锁，10s 续期一次）
- **RedLock 红锁**（多 Redis 实例多数派通过，解决主从切换锁失效问题，但实际工程上争议较大）
- 阻塞等待、`tryLock(timeout)` 等丰富 API

**Q9: 什么是缓存预热？怎么避免冷启动 DB 被打挂？**

A: 缓存预热指系统上线或重启后，提前把热点数据加载到 Redis。常用方式：(1) 启动时定时任务从 DB 拉热点；(2) 配合 Nginx/网关的灰度发布，让流量逐步进入；(3) 设置较短 TTL，DB 自动回填；(4) 重要接口加熔断保护（即使缓存空也能降级返回）。

**Q10: Redis 内存淘汰策略 LRU 和 LFU 怎么选？**

A: 
- **LRU（最近最少使用）**：基于访问时间，长尾流量友好，但易被突发流量误伤（突发访问的 key 刚加载就被淘汰）。
- **LFU（最不经常使用）**：基于访问频次（Redis 4.0+ 实现，改进版 LFU 带衰减因子），热点数据更稳定。
- **生产推荐**：4.0+ 用 `allkeys-lfu`，长期热点场景（电商详情、新闻列表）效果优于 LRU。

## 延伸阅读

1. **《Redis 设计与实现》**（黄健宏）：Redis 源码级讲解，5+1 数据结构、持久化、集群部分讲得非常透彻，面试前必读。
2. **Redis 官方文档**：https://redis.io/documentation —— 命令参考、配置项说明、最新特性。
3. **Antirez（Redis 作者）博客**：http://antirez.com —— 关于 LFU 实现、Cluster 设计、Stream 设计的第一手设计文档。
4. **B 站"黑马程序员 Redis 入门到精通"**：免费的入门 + 实战课程，覆盖 SpringBoot 整合 Redis、缓存三大问题、分布式锁等高频面试题。