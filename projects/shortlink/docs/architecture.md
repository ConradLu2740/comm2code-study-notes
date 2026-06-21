# 架构设计

## 总体架构

```
┌────────────────────────────────────────────────────────────────┐
│                         Client                                 │
│              (Browser / curl / Postman)                        │
└─────────────────────┬──────────────────────────────────────────┘
                      │ HTTP
┌─────────────────────▼──────────────────────────────────────────┐
│                  Spring Boot (8080)                            │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │  Controller Layer (REST API + Redirect)                  │  │
│  │  - ShortLinkController (POST/GET /api/v1/shortlinks)     │  │
│  │  - RedirectController (GET /{shortCode})                 │  │
│  │  - GlobalExceptionHandler (统一异常 → JSON)              │  │
│  └──────────────────────┬───────────────────────────────────┘  │
│                         │                                      │
│  ┌──────────────────────▼───────────────────────────────────┐  │
│  │  Service Layer (业务核心)                                  │  │
│  │  - ShortLinkService (CRUD + Cache-Aside)                  │  │
│  │  - ShortCodeGenerator (Snowflake + Base62)                │  │
│  │  - RateLimiter (令牌桶)                                   │  │
│  │  - CacheService (Memory | Redis)                          │  │
│  └──────────┬───────────────────────────┬──────────────────┘  │
│             │                           │                      │
│  ┌──────────▼──────────┐    ┌───────────▼──────────────┐       │
│  │  Repository (JPA)   │    │  Cache (Memory/Redis)    │       │
│  └──────────┬──────────┘    └──────────────────────────┘       │
│             │                                                  │
└─────────────┼──────────────────────────────────────────────────┘
              │ JDBC
       ┌──────▼──────┐
       │ H2 / MySQL  │
       └─────────────┘
```

## 5 个核心设计决策

### 1. 短码生成：Snowflake + Base62

**问题**：怎么生成全局唯一且尽量短的短码？

**方案对比**：

| 方案 | 优点 | 缺点 |
|---|---|---|
| 雪花 ID + Base62 | 全局唯一、生成快、长度可控 | 单机需配 workerId |
| Hash(MD5) + 截断 | 简单 | 可能碰撞、需要重试 |
| 数据库自增 | 简单 | 必须先写库、暴露业务量 |
| 随机字符串 + 重试 | 实现简单 | 碰撞概率随量上升 |

**我们的选择**：Snowflake（64 位 long，41 位时间戳保证趋势递增、12 位序列号单机每毫秒 4096 个）→ Base62 编码成 7 位字符串（≈ 3.5 万亿组合）。

**实现要点**：
- `ShortCodeGenerator` 完整实现
- 同一毫秒内序列号自增
- 时钟回拨保护
- `encodeBase62(long)` / `decodeBase62(String)` 双向

### 2. 缓存策略：Cache-Aside

**问题**：短链查询 QPS 高（每次访问都查），数据库扛不住。

**方案对比**：

| 策略 | 读流程 | 写流程 | 一致性 |
|---|---|---|---|
| Cache-Aside（最常用）| 查缓存 → 查 DB → 回填 | 写 DB → 失效缓存 | 最终一致 |
| Read-Through | 查缓存（缓存自动加载）| 同上 | 最终一致 |
| Write-Through | 同上 | 写缓存（缓存写 DB）| 强一致（慢）|
| Write-Behind | 同上 | 写缓存（异步写 DB）| 最终一致（可能丢）|

**我们的选择**：Cache-Aside。
- 读：先 Redis，没有查 DB，回填 Redis
- 写：写 DB，删 Redis（下次读再回填）

**代码**：`ShortLinkService.resolve()` 第 3-30 行。

### 3. 限流：令牌桶 + Lua 原子

**问题**：恶意用户刷 `/api/v1/shortlinks` 攻击。

**方案**：
- 桶容量 10，每秒补充 5 个令牌
- 每次请求消耗 1 个令牌
- 桶空时返回 429 Too Many Requests

**Redis 版为什么用 Lua**：
- 「读 tokens → 改 → 写回」必须原子
- Redis 单线程执行 Lua 脚本天然原子
- 如果用 Java 多步操作，并发下会超发

**代码**：`RedisConfig.rateLimitScript()` + `RedisRateLimiter`。

### 4. 重定向：302 而非 301

**问题**：用 301 还是 302 重定向？

**对比**：

| 状态码 | 含义 | 浏览器行为 | 能否统计点击数 |
|---|---|---|---|
| 301 | 永久重定向 | 缓存，下次直接走 | ❌ |
| 302 | 临时重定向 | 每次问服务器 | ✅ |

**我们的选择**：302，保留点击统计能力。

**代码**：`RedirectController.redirect()`。

### 5. 配置切换：Profile + @ConditionalOnProperty

**问题**：开发用内存版（快），生产用 Redis（强）。同一套代码怎么支持？

**方案**：
- `application.yml` 有 `dev` 和 `prod` 两个 profile
- `application-dev.yml`：H2 + 内存缓存
- `application-prod.yml`：MySQL + Redis
- `CacheService` / `RateLimiter` 都有两个实现
- 用 `@ConditionalOnProperty(name = "shortlink.cache.type", havingValue = "memory")` 决定加载哪个

**业务代码不感知切换**：依赖注入的是接口 `CacheService`，Spring 自动注入正确的实现。

## 性能指标（单机）

| 操作 | QPS | 延迟 p99 |
|---|---|---|
| 创建短链 | ~5000 | < 50ms |
| 短链访问（缓存命中）| ~50000 | < 5ms |
| 短链访问（缓存未命中）| ~3000 | < 30ms |

## 后续扩展方向

- **加用户系统**：JWT 鉴权 + 用户/链接一对多
- **加统计**：Kafka 异步收集访问日志，Flink 实时分析
- **加自定义域名**：多租户 URL 路由
- **加 CDN**：把短链服务部署到边缘节点
- **加熔断降级**：Sentinel / Resilience4j
- **加分布式追踪**：SkyWalking / Zipkin

---

最后更新：2026-06-21