# Java 学习笔记

> 后端开发主线语言。按 12 个模块拆，每个目录放一类笔记。

## 模块路线（建议顺序）

| # | 模块 | 关键内容 | 优先级 |
|---|------|---------|--------|
| 01 | [基础语法](./01-basics) | 数据类型、控制流、数组、字符串、异常 | ⭐⭐⭐⭐⭐ |
| 02 | [面向对象](./02-oop) | 类/对象、继承/多态、封装、抽象类/接口 | ⭐⭐⭐⭐⭐ |
| 03 | [集合框架](./03-collections) | List/Set/Map、ArrayList/HashMap 源码、ConcurrentHashMap | ⭐⭐⭐⭐⭐ |
| 04 | [并发编程](./04-concurrency) | Thread、synchronized、volatile、AQS、JUC | ⭐⭐⭐⭐⭐ |
| 05 | [JVM](./05-jvm) | 内存模型、GC、类加载、字节码、调优 | ⭐⭐⭐⭐⭐ |
| 06 | [IO/NIO](./06-io-nio) | BIO、NIO、Netty 基础、AIO | ⭐⭐⭐⭐ |
| 07 | [Netty](./07-netty) | Reactor 模型、Pipeline、ChannelHandler | ⭐⭐⭐⭐ |
| 08 | [Spring 全家桶](./08-spring) | IoC、AOP、Spring Boot、Spring Cloud | ⭐⭐⭐⭐⭐ |
| 09 | [MySQL](./09-mysql) | 索引、事务、锁、MVCC、调优 | ⭐⭐⭐⭐⭐ |
| 10 | [Redis](./10-redis) | 数据结构、持久化、集群、缓存设计 | ⭐⭐⭐⭐⭐ |
| 11 | [消息队列](./11-mq) | RabbitMQ / Kafka、消息可靠性、幂等 | ⭐⭐⭐⭐ |
| 12 | [设计模式](./12-patterns) | 23 种模式、Spring / JDK 源码中的应用 | ⭐⭐⭐ |

## 学习节奏

- 基础（01-03）：2-3 周，每天 2h
- 进阶（04-07）：4-6 周，每天 2h
- 框架（08-11）：与项目同步，边做边学
- 模式（12）：贯穿全程，遇到就回头补

## 推荐资源

- 视频：黑马 / 尚硅谷 Java 基础、并发、JVM
- 书：《Java 核心技术》《Effective Java》《深入理解 Java 虚拟机》
- 刷题：LeetCode（Java 题解）
- 源码：JDK 1.8 集合、Spring 源码

## 命名约定

每个目录笔记文件名格式：`YYYY-MM-DD-主题.md`
例如：`2026-06-21-hashmap-put-flow.md`

---

最后更新：2026-06-21