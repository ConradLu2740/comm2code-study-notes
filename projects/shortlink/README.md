# 短链接服务 (ShortLink)

> 转码第一个实战项目 - 类似 Bit.ly / TinyURL
> 覆盖 Spring Boot + Spring Data JPA + Redis + REST API 全栈后端技能

## 🚀 3 分钟跑起来

### 前置环境

| 工具 | 版本 | 检查命令 |
|---|---|---|
| JDK | 17+ | `java -version` |
| Maven | 3.8+ | `mvn -version` |

> 默认 profile 用 H2 内存数据库 + 内存缓存，**无需任何额外依赖**（不用装 MySQL/Redis）。

### 启动

```bash
cd projects/shortlink
mvn spring-boot:run
```

看到 `短链接服务已启动 → http://localhost:8080` 即可。

### 立即试用

打开另一个终端：

```bash
# 1. 创建短链接
curl -X POST http://localhost:8080/api/v1/shortlinks \
  -H "Content-Type: application/json" \
  -d '{"longUrl":"https://www.baidu.com"}'

# 响应: {"shortCode":"...","shortUrl":"http://localhost:8080/...","longUrl":"https://www.baidu.com",...}

# 2. 访问短链接（302 跳转到长链接）
curl -i http://localhost:8080/你的短码

# 3. 查看链接详情
curl http://localhost:8080/api/v1/shortlinks/你的短码

# 4. 测试数据（已经在 H2 里初始化）
curl -i http://localhost:8080/demo01  # → https://www.baidu.com
curl -i http://localhost:8080/demo02  # → https://github.com
curl -i http://localhost:8080/demo03  # → https://www.spring.io
```

### 看数据库

打开浏览器访问 http://localhost:8080/h2-console

- JDBC URL: `jdbc:h2:mem:shortlink`
- 用户名: `sa`
- 密码: (空)

## 🏗 架构一览

```
┌─────────────┐      ┌──────────────────┐      ┌──────────┐
│  客户端     │ ───> │  Controller      │ ───> │ Service  │
└─────────────┘      │  (REST + 重定向) │      │  Layer   │
                     └──────────────────┘      └─────┬────┘
                                                     │
                          ┌──────────────────────────┼─────────────────┐
                          │                          │                 │
                          v                          v                 v
                    ┌──────────┐              ┌──────────┐      ┌──────────┐
                    │ Repository│              │  Cache   │      │  Code    │
                    │ (JPA/H2)  │              │ (Mem/Redis)│     │Generator │
                    └─────┬────┘              └──────────┘      │(Snowflake)│
                          │                                      └──────────┘
                          v
                    ┌──────────┐
                    │   MySQL  │
                    └──────────┘
```

## 📁 项目结构

```
shortlink/
├── pom.xml                              # Maven 配置
├── README.md                            # ← 你正在看
├── docs/
│   ├── architecture.md                  # 详细架构 + 设计决策
│   ├── api.md                           # REST API 完整文档
│   └── learning-guide.md                # 每个文件对应哪个学习模块
└── src/
    ├── main/
    │   ├── java/com/conrad/shortlink/
    │   │   ├── ShortLinkApplication.java      # 启动类
    │   │   ├── config/                        # 配置
    │   │   │   ├── RedisConfig.java
    │   │   │   └── WebConfig.java
    │   │   ├── controller/                    # API 层
    │   │   │   ├── ShortLinkController.java   # REST CRUD
    │   │   │   └── RedirectController.java    # 短链重定向
    │   │   ├── service/                       # 业务层
    │   │   │   ├── ShortLinkService.java      # 业务核心
    │   │   │   ├── ShortCodeGenerator.java    # Snowflake + Base62
    │   │   │   ├── CacheService.java          # 缓存接口
    │   │   │   ├── InMemoryCacheService.java
    │   │   │   ├── RedisCacheService.java
    │   │   │   ├── RateLimiter.java           # 限流接口
    │   │   │   ├── InMemoryRateLimiter.java
    │   │   │   └── RedisRateLimiter.java
    │   │   ├── entity/ShortLink.java          # 数据库实体
    │   │   ├── repository/ShortLinkRepository.java  # JPA
    │   │   ├── dto/                           # 数据传输对象
    │   │   │   ├── CreateShortLinkRequest.java
    │   │   │   ├── ShortLinkResponse.java
    │   │   │   └── ErrorResponse.java
    │   │   └── exception/                     # 异常 + 全局处理
    │   │       ├── ShortLinkNotFoundException.java
    │   │       ├── RateLimitExceededException.java
    │   │       └── GlobalExceptionHandler.java
    │   └── resources/
    │       ├── application.yml                # 配置（含 dev/prod profile）
    │       └── data.sql                       # 测试数据
    └── test/
        └── java/com/conrad/shortlink/service/  # 单元测试
            ├── ShortCodeGeneratorTest.java
            ├── ShortLinkServiceTest.java
            └── InMemoryRateLimiterTest.java
```

## 🧪 跑测试

```bash
mvn test
```

应该看到所有测试通过。

## 🔧 切到生产模式（MySQL + Redis）

```bash
# 1. 准备 MySQL
mysql -u root -p -e "CREATE DATABASE shortlink;"

# 2. 准备 Redis
redis-server

# 3. 启动
SPRING_PROFILES_ACTIVE=prod \
DB_USER=root DB_PASSWORD=yourpass \
mvn spring-boot:run
```

## 🛠 扩展点（练手用）

1. **加用户系统**：JWT 登录、用户管理自己的短链
2. **加统计功能**：访问地域（IP 库）、设备（UA 解析）、Referer
3. **加自定义域名**：支持用户绑定自己的短链域名
4. **加分布式 ID**：Snowflake 改成基于 Redis 的分布式发号器
5. **加后台管理**：Vue + Element Plus 写个管理界面
6. **加 Docker**：写 Dockerfile + docker-compose.yml
7. **加 CI/CD**：GitHub Actions 自动化测试 + 部署

## 📚 学习指引

每个代码文件都有 `// 教学点：` 注释，解释关键概念。
想要系统性学习，参考 `docs/learning-guide.md`，把代码和 `notes/java/` 里的笔记对照看。

---

最后更新：2026-06-21