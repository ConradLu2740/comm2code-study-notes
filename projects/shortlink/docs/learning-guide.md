# 学习指南：代码 ↔ 笔记对照表

> 这个项目里**每个文件**都对应 `notes/java/` 里的某个学习模块。
> 看完代码再去看笔记，效率最高；看完笔记再看代码，理解最深。

## 速查表

| 项目文件 | 对应学习模块 | 学习重点 |
|---|---|---|
| `ShortLinkApplication.java` | 08-spring | `@SpringBootApplication` 三个注解含义 |
| `config/WebConfig.java` | 08-spring | CORS、`@Configuration`、`WebMvcConfigurer` |
| `config/RedisConfig.java` | 10-redis + 04-concurrency | `@ConditionalOnProperty`、Lua 脚本 |
| `entity/ShortLink.java` | 08-spring | JPA 注解、`@Entity`、`@Table`、`@Id` |
| `repository/ShortLinkRepository.java` | 08-spring | `JpaRepository`、方法命名规则、`@Query` |
| `dto/CreateShortLinkRequest.java` | 08-spring | Bean Validation 注解 |
| `dto/ShortLinkResponse.java` | 12-patterns | 工厂方法模式（`fromEntity`） |
| `dto/ErrorResponse.java` | 12-patterns | DTO 模式、统一响应格式 |
| `exception/...` | 08-spring | 自定义异常 |
| `exception/GlobalExceptionHandler.java` | 08-spring | `@RestControllerAdvice`、AOP 统一异常处理 |
| `service/ShortCodeGenerator.java` | 05-jvm + 算法 | 位运算（移位、与、或）、Snowflake 算法 |
| `service/CacheService.java` | 10-redis | 缓存抽象、接口设计 |
| `service/InMemoryCacheService.java` | 03-collections | `ConcurrentHashMap`、记录类型（record）|
| `service/RedisCacheService.java` | 10-redis | Spring Data Redis |
| `service/RateLimiter.java` | 04-concurrency | 令牌桶算法概念 |
| `service/InMemoryRateLimiter.java` | 04-concurrency | `AtomicReference`、CAS、synchronized |
| `service/RedisRateLimiter.java` | 10-redis + 04-concurrency | Redis Lua 原子脚本 |
| `service/ShortLinkService.java` | 08-spring + 10-redis | Cache-Aside、`@Transactional`、构造器注入 |
| `controller/ShortLinkController.java` | 08-spring | `@RestController`、`@RequestMapping`、HTTP 方法、限流 |
| `controller/RedirectController.java` | 08-spring | 302 重定向、`ResponseEntity` |
| `src/test/...` | JUnit 5 | 单元测试、Mockito |

## 推荐学习顺序

### 第一遍：跟着代码读笔记（3-5 天）

1. **Spring Boot 启动** → 看 `ShortLinkApplication.java` → 读 `08-spring` 的 `@SpringBootApplication` 部分
2. **配置文件** → 看 `application.yml` → 读 `08-spring` 的 `application.properties` 部分
3. **REST API** → 看 `ShortLinkController.java` → 读 `08-spring` 的 REST 部分
4. **数据库** → 看 `entity/ShortLink.java` + `repository/...` → 读 `08-spring` 的 JPA 部分
5. **业务逻辑** → 看 `ShortLinkService.java` → 理解 Cache-Aside 模式 → 读 `10-redis` 的 Cache Aside
6. **缓存** → 看 `CacheService` + 两个实现 → 读 `10-redis` 的整体
7. **限流** → 看 `RateLimiter` + Lua 脚本 → 读 `10-redis` 的 Lua 部分
8. **短码生成** → 看 `ShortCodeGenerator.java` → 读 `05-jvm` 的位运算部分

### 第二遍：抄代码（5-7 天）

不看我的代码，凭记忆把整个项目重写一遍。卡住的地方回来看注释。

### 第三遍：扩展（无限）

参考 README 的"扩展点"部分，每个加 1-2 个功能。

## 关键概念对应

### `@SpringBootApplication` = 三个注解

```java
@SpringBootApplication
// = @SpringBootConfiguration
//   = @Configuration（标记这是配置类）
// + @EnableAutoConfiguration（开启自动配置：Spring Boot 启动时自动配置 Spring MVC、JPA、Tomcat 等）
// + @ComponentScan（扫描本包及子包的所有 @Component / @Service / @Repository / @Controller / @RestController）
```

### Snowflake 位运算

```java
return ((timestamp - EPOCH) << TIMESTAMP_SHIFT)   // 时间戳占 41 位
    | (datacenterId << DATACENTER_ID_SHIFT)        // 数据中心占 5 位
    | (workerId << WORKER_ID_SHIFT)                // 工作机器占 5 位
    | sequence.get();                              // 序列号占 12 位
```

`<<` 左移 = 在二进制末尾补 0；`|` 按位或 = 把两段二进制拼起来。

### Cache-Aside 模式

```java
// 读
String cached = cache.get(key);     // 1. 查缓存
if (cached != null) return cached;  // 命中返回
String value = db.get(key);         // 2. 查 DB
cache.set(key, value, ttl);         // 3. 回填缓存
return value;

// 写
db.save(entity);                    // 1. 写 DB
cache.delete(key);                  // 2. 失效缓存（下一次读会回填新的）
```

### 令牌桶

```
tokens = min(capacity, tokens + (now - lastRefill) * rate)
if tokens >= permits:
    tokens -= permits
    return true  # 放行
else:
    return false  # 限流
```

## 常见疑问

### Q: `@Autowired` 字段注入 vs 构造器注入？

**A**: 构造器注入更安全（字段可标 `final`、测试时不用反射、容易发现循环依赖）。本项目全部用构造器。

### Q: 为什么用 `Optional<T>` 而不是返回 `null`？

**A**: 强制调用者处理"不存在"的情况，避免空指针。`orElseThrow()` / `orElse()` / `orElseGet()` 都是显式处理。

### Q: 什么是 DTO？为什么不直接返回 Entity？

**A**: DTO 是 API 层的传输对象。Entity 改了字段（比如加个 `password`），DTO 不改就不影响 API 契约。

### Q: `@Transactional` 加在 Controller 还是 Service？

**A**: Service。因为事务应该围绕业务操作，Controller 只负责参数接收和响应包装。

### Q: 为什么不直接用 `@Autowired` 注入？

**A**: 见上。`@Autowired` 字段注入是历史包袱，构造器注入是 Spring 5+ 推荐方式。

---

最后更新：2026-06-21