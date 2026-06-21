# 08 - Spring 全家桶

## 概述

**Spring** 是 Java 后端开发事实上的事实标准，是一套以 **IoC（控制反转）** 和 **AOP（面向切面编程）** 为核心的轻量级应用框架。从单一 Bean 容器到 Spring Boot 的自动配置，再到 Spring Cloud 的微服务全家桶，Spring 生态几乎覆盖了后端开发的所有场景，是面试必考、生产必用的核心知识。

### 关键点速览

| 维度 | 核心点 |
|------|--------|
| 核心思想 | IoC（控制反转）+ AOP（面向切面） |
| 容器 | BeanFactory → ApplicationContext（功能增强） |
| 生命周期 | 实例化 → 属性注入 → 初始化前 → 初始化 → 初始化后 → 使用 → 销毁 |
| 循环依赖 | 三级缓存（singletonFactories / earlySingletonObjects / singletonObjects） |
| AOP | JDK 动态代理（接口）vs CGLIB（子类），Spring Boot 2.x 默认 CGLIB |
| 事务 | @Transactional 基于 AOP，自调用会失效 |
| Boot 启动 | @SpringBootApplication = @Configuration + @EnableAutoConfiguration + @ComponentScan |
| 自动配置 | spring.factories（3.x）/ META-INF/spring/AutoConfiguration.imports（3.x） |
| Cloud 组件 | 注册中心（Nacos/Eureka）、配置中心（Nacos/Config）、网关（Gateway）、熔断（Sentinel/Hystrix）、分布式事务（Seata） |

---

## 核心概念

### 1. IoC 与 DI

**IoC（Inversion of Control）** 是 Spring 最核心的思想：对象的创建、组装、生命周期不再由调用方 `new` 出来，而是交给容器管理。**DI（Dependency Injection）** 是 IoC 的一种实现方式——容器在运行时把依赖对象注入到目标对象中。

常见注入方式：

| 方式 | 说明 | 推荐度 |
|------|------|--------|
| 构造器注入 | 通过构造方法注入依赖，**官方推荐**，可保证依赖不可变且不为 null | ★★★★★ |
| Setter 注入 | 通过 `setXxx` 方法注入 | ★★★ |
| 字段注入（@Autowired 直接打在字段上） | 写法最简单但无法注入 final 字段，不利于单测 | ★★ |

### 2. IoC 容器：BeanFactory vs ApplicationContext

- **BeanFactory**：最底层的容器接口，只提供基本的 Bean 获取（`getBean`），**延迟加载**（用到才创建）。
- **ApplicationContext**：BeanFactory 的子接口，提供了企业级特性：国际化、事件机制、资源加载、AOP、**预加载所有单例 Bean**。日常开发使用的 `ClassPathXmlApplicationContext` / `AnnotationConfigApplicationContext` 都是它的实现。

### 3. BeanDefinition 与 Bean 注册流程

容器启动时，扫描到的每一个 Bean 都会被解析成一个 **`BeanDefinition`** 对象，它保存了 Bean 的 class 名、作用域、初始化方法、销毁方法、依赖关系等元数据。

注册流程（基于 `@Component` 注解）：
1. 容器创建 `AnnotatedBeanDefinitionReader`，加载内置的 `ConfigurationClassPostProcessor` 等后置处理器。
2. `ComponentScan` 扫描指定包，把所有 `@Component`（含 `@Service`/`@Repository`/`@Controller`）类解析为 `ScannedGenericBeanDefinition`。
3. BeanDefinition 注册到 `DefaultListableBeanFactory.beanDefinitionMap`。
4. 容器刷新时（`refresh()`），按 BeanDefinition **实例化** Bean 并填充属性。

### 4. Bean 生命周期（面试高频）

完整链路：

```
实例化（构造方法 / 工厂方法）
  → 属性注入（@Autowired / @Value）
  → BeanNameAware / BeanFactoryAware / ApplicationContextAware
  → BeanPostProcessor.postProcessBeforeInitialization
  → @PostConstruct（CommonAnnotationBeanPostProcessor 处理）
  → InitializingBean.afterPropertiesSet
  → 自定义 init-method
  → BeanPostProcessor.postProcessAfterInitialization
    —— AOP 代理在此生成 ——
  → 使用阶段
  → @PreDestroy
  → DisposableBean.destroy
  → 自定义 destroy-method
```

记忆口诀：**「实例化 → 注入 → Aware → Before → @PostConstruct → InitializingBean → init-method → After → 销毁」**。

### 5. 循环依赖与三级缓存

Spring 解决 **单例 + Setter 注入** 的循环依赖，靠三级缓存：

| 缓存名 | 类型 | 作用 |
|--------|------|------|
| `singletonObjects`（一级） | `Map<String, Object>` | 完整 Bean，初始化完成才放入 |
| `earlySingletonObjects`（二级） | `Map<String, Object>` | 早期引用，未填充属性 |
| `singletonFactories`（三级） | `Map<String, ObjectFactory<?>>` | ObjectFactory 工厂，可生成原始对象或代理对象 |

核心流程：A 创建 → 注入 B → B 创建 → 注入 A（从三级缓存拿到 ObjectFactory，生成早期引用放入二级）→ B 完成 → A 完成。

**构造器循环依赖为什么无解？** 因为实例化对象必须先调用构造器，但构造器在调用时尚未生成对象引用，无法提前暴露。所以 `@Autowired` 标注构造器且发生循环依赖时，Spring 会直接抛 `BeanCurrentlyInCreationException`。

### 6. AOP 原理

**AOP（Aspect-Oriented Programming）** 把横切关注点（日志、事务、权限）抽离成独立切面，避免代码冗余。

#### 两种代理方式

| 维度 | JDK 动态代理 | CGLIB |
|------|--------------|-------|
| 原理 | 实现目标接口，`Proxy.newProxyInstance` 生成新类 | 继承目标类，生成子类字节码 |
| 要求 | 目标必须实现接口 | 不需要接口 |
| 性能 | 反射调用，略慢 | 方法调用快，类加载稍慢 |
| final 方法 | 不支持 | **不支持 final 方法**（子类无法覆盖） |

**Spring Boot 2.x 起默认使用 CGLIB**，可通过 `spring.aop.proxy-target-class=false` 切换回 JDK 代理。

#### 关键术语

- **Aspect（切面）**：横切关注点的模块化，`@Aspect` 标注的类。
- **Pointcut（切点）**：匹配连接点的表达式，如 `@Pointcut("execution(* com.xx.service..*(..))")`。
- **Advice（通知）**：切面在切点执行的动作：
  - `@Before`：前置通知
  - `@After`：后置通知（无论是否异常）
  - `@Around`：环绕通知（最强大）
  - `@AfterReturning`：返回后通知
  - `@AfterThrowing`：异常通知
- **JoinPoint（连接点）**：程序执行的某个点（方法调用、异常处理），Spring AOP 只支持方法级。
- **Introduction（引入）**：为已有类型动态添加方法/字段。

### 7. Spring 事务（@Transactional）

`@Transactional` 本质是基于 AOP 的方法拦截，由 `TransactionInterceptor` 实现。

#### 7 种传播行为（必须背）

| 传播行为 | 含义 |
|----------|------|
| `REQUIRED`（默认） | 存在事务则加入，否则新建 |
| `SUPPORTS` | 存在事务则加入，否则以非事务执行 |
| `MANDATORY` | 必须存在事务，否则抛异常 |
| `REQUIRES_NEW` | 无论是否存在，都新建事务（挂起当前） |
| `NOT_SUPPORTED` | 以非事务执行，挂起当前事务 |
| `NEVER` | 必须不存在事务，否则抛异常 |
| `NESTED` | 嵌套事务（保存点） |

#### 隔离级别

支持 `READ_UNCOMMITTED` / `READ_COMMITTED` / `REPEATABLE_READ` / `SERIALIZABLE`，默认使用数据库自身隔离级别（MySQL InnoDB 为 `REPEATABLE_READ`）。

#### 失效场景（面试必问）

1. **自调用**：同类中 `this.methodB()` 不会经过代理，事务失效。
2. **异常被 catch**：事务只对未捕获的运行时异常回滚。
3. **非 public 方法**：AOP 默认只代理 public 方法。
4. **抛出的异常不在回滚列表**：`rollbackFor` 默认只回滚 `RuntimeException` 和 `Error`。
5. **数据库引擎不支持**：如 MySQL 的 MyISAM 引擎无事务。

### 8. Spring Boot 自动配置

`@SpringBootApplication` 是个组合注解：

```
@SpringBootApplication
  ├── @SpringBootConfiguration（本质 @Configuration）
  ├── @EnableAutoConfiguration
  │     └── 加载 META-INF/spring/AutoConfiguration.imports 或 spring.factories 中声明的配置类
  └── @ComponentScan（默认扫描启动类所在包及子包）
```

#### 自动配置核心流程

1. `@EnableAutoConfiguration` 导入 `AutoConfigurationImportSelector`。
2. `selectImports()` 读取 `META-INF/spring/AutoConfiguration.imports`（Spring Boot 3.x，取代 2.x 的 `spring.factories`）。
3. 每个 `XxxAutoConfiguration` 都是 `@Configuration` 类，内部用 `@ConditionalOnXxx` 系列条件注解决定是否生效：
   - `@ConditionalOnClass`：类路径下存在某类
   - `@ConditionalOnMissingBean`：容器中没有某 Bean
   - `@ConditionalOnProperty`：配置项匹配
4. 用户通过 `application.yml` / `application.properties` 自定义行为。

#### Starter 原理

每个 Starter 是一个空 jar，仅负责**依赖管理**。例如 `spring-boot-starter-web` 引入了 Tomcat + Spring MVC，用户只需要 `spring-boot-starter-web` 一个依赖即可开发 Web 应用。

### 9. Spring Boot 内嵌服务器

| 服务器 | 特点 | 适用场景 |
|--------|------|----------|
| **Tomcat**（默认） | 老牌稳定，生态最好 | 绝大多数 Web 应用 |
| Jetty | 轻量、长连接友好 | WebSocket、高并发长连接 |
| Undertow | 性能最强、内存占用最低 | 高吞吐微服务 |

切换方式：排除 `spring-boot-starter-tomcat`，引入其他服务器的 Starter 即可。

### 10. Spring Cloud 生态

#### 核心组件对比

| 组件 | Netflix / 官方 | Alibaba | 备注 |
|------|----------------|---------|------|
| 注册中心 | Eureka / Consul | **Nacos** | Nacos 同时是配置中心，国内首选 |
| 配置中心 | Config Server | **Nacos** | Nacos 国内首选 |
| 负载均衡 | Ribbon（停更）/ **LoadBalancer** | Dubbo LB | Spring Cloud 官方推 LoadBalancer |
| 服务调用 | OpenFeign | Dubbo | OpenFeign 基于 HTTP |
| 网关 | **Gateway** | — | Spring 官方 |
| 熔断 | Hystrix（停更）/ Resilience4j | **Sentinel** | Sentinel 国内主流 |
| 分布式事务 | — | **Seata** | AT/TCC/SAGA/XA |
| 消息 | Stream | **RocketMQ** | 国内主流 |
| 链路追踪 | Sleuth + Zipkin | SkyWalking | 国产更主流 |

#### Nacos = 注册中心 + 配置中心

- **注册中心**：服务启动时向 Nacos 注册自己的 IP/Port/Nacos定时心跳续约；消费者订阅服务列表。
- **配置中心**：使用 `@RefreshScope` + `spring-cloud-starter-alibaba-nacos-config` 动态刷新配置。

#### Sentinel = 流控 + 熔断 + 降级

- **流控规则**：QPS / 并发线程数限流，支持直接、关联、链路三种模式。
- **熔断降级**：统计慢调用/异常比例/异常数，触发后熔断一段时间。
- **热点参数限流**：针对特定参数值限流（如秒杀场景）。
- 控制台 `sentinel-dashboard` 可视化配置。

---

## 代码示例

### 示例 1：构造器注入 + Bean 生命周期

```java
@Component
public class UserService {
    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) { // 构造器注入
        this.userRepository = userRepository;
    }

    @PostConstruct
    public void init() {
        System.out.println("UserService 初始化完成");
    }

    @PreDestroy
    public void destroy() {
        System.out.println("UserService 销毁");
    }
}
```

### 示例 2：AOP 切面 + 环绕通知实现日志

```java
@Aspect
@Component
public class LogAspect {

    private static final Logger log = LoggerFactory.getLogger(LogAspect.class);

    @Pointcut("execution(* com.example.service..*(..))")
    public void serviceLayer() {}

    @Around("serviceLayer()")
    public Object aroundLog(ProceedingJoinPoint pjp) throws Throwable {
        long start = System.currentTimeMillis();
        String method = pjp.getSignature().toShortString();
        try {
            Object result = pjp.proceed();
            log.info("[{}] cost={}ms", method, System.currentTimeMillis() - start);
            return result;
        } catch (Throwable e) {
            log.error("[{}] failed: {}", method, e.getMessage());
            throw e;
        }
    }
}
```

### 示例 3：事务传播行为实战

```java
@Service
public class OrderService {

    @Autowired private OrderRepository orderRepository;
    @Autowired private LogService logService; // 独立事务

    @Transactional(propagation = Propagation.REQUIRED)
    public void placeOrder(Order order) {
        orderRepository.save(order);
        // logService 内部独立事务，主事务回滚不影响日志保存
        logService.recordLog("订单创建: " + order.getId());
    }
}

@Service
public class LogService {

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordLog(String msg) {
        // 即使外层事务回滚，日志也能保存
        logRepository.save(new Log(msg));
    }
}
```

### 示例 4：自定义 Spring Boot Starter

```java
// 1. 自动配置类
@AutoConfiguration
@ConditionalOnClass(MyService.class)
@EnableConfigurationProperties(MyProperties.class)
public class MyAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public MyService myService(MyProperties props) {
        return new MyService(props.getPrefix());
    }
}

// 2. META-INF/spring/AutoConfiguration.imports
com.example.MyAutoConfiguration
```

```yaml
# 3. application.yml
my:
  prefix: hello
```

---

## 易错点 / 最佳实践

1. **优先使用构造器注入**：可以注入 `final` 字段、保证依赖不为空、便于单元测试（不需要反射）。Spring 团队从 4.x 起就明确推荐构造器注入。

2. **避免循环依赖**：三级缓存是「事后补救」机制，依赖过深时应考虑重构。**构造器注入** 不应出现循环依赖，一旦出现应重新设计模块边界。

3. **@Transactional 必须注意失效场景**：
   - 同类自调用 → 注入自身代理（`@Autowired private OrderService self;`）或改用 `@Autowired` 注入 `ApplicationContext` 拿到代理。
   - 异常被 catch 时**必须重新抛出**。
   - 明确指定 `rollbackFor = Exception.class`，覆盖 checked 异常。
   - `readOnly = true` 可提示数据库走只读优化。

4. **Spring Boot 配置优先级（从高到低）**：命令行参数 > `application-{profile}.yml` > `application.yml` > 默认值。生产环境推荐用 `-Dspring.profiles.active=prod` 切换。

5. **AOP 切面不要滥用**：能用普通方法抽离就别上 AOP；AOP 增加了调试难度和隐式调用关系，新人接手成本高。事务这种强需求才是 AOP 主战场。

---

## 面试常见问题

**Q1：Spring IoC 容器的启动流程（refresh 方法）大致是什么？**

A：`refresh()` 是 Spring 容器的核心方法，步骤大致为：① 准备刷新环境（`prepareRefresh`）；② 加载并校验 BeanDefinition（`obtainFreshBeanFactory`）；③ 注册 Bean 后置处理器（`registerBeanPostProcessors`）；④ 国际化、事件多播器初始化；⑤ 实例化所有非懒加载单例 Bean（`finishBeanFactoryInitialization`）；⑥ 完成刷新发布事件（`finishRefresh`）。每个步骤都是 Spring 留出的扩展点。

**Q2：Spring 是如何解决循环依赖的？一定要三级缓存吗？**

A：核心是利用三级缓存中 `singletonFactories`（`ObjectFactory`）提前暴露「未初始化完成」的 Bean 引用。**理论上二级缓存也能解决**普通循环依赖，但三级缓存的 ObjectFactory 在调用 `getObject()` 时才会判断是否需要生成 AOP 代理——这样既支持循环依赖，又能避免无意义的代理创建，是延迟 + 解耦的设计。

**Q3：构造器注入为什么无法解决循环依赖？**

A：构造器调用必须在对象存在引用之前完成，而三级缓存暴露的「早期引用」要求对象已经实例化（哪怕属性没填）。构造器还没跑完就没有引用可以暴露，所以 Spring 检测到构造器循环依赖会直接抛 `BeanCurrentlyInCreationException`。**解决办法**：① 改用 Setter 注入；② 用 `@Lazy` 延迟注入；③ 重构拆分模块。

**Q4：JDK 动态代理和 CGLIB 有什么区别？Spring Boot 2.x 为什么默认 CGLIB？**

A：JDK 动态代理要求目标类实现接口，原理是 `Proxy.newProxyInstance` 在运行时生成一个新类实现同一组接口；CGLIB 通过 ASM 生成目标类的子类实现代理，因此不要求接口但不能代理 `final` 类 / `final` 方法。Spring Boot 2.x 改默认 CGLIB 是因为：① 业务类不必强制实现接口；② CGLIB 调用性能更优（避免反射）；③ 一致性更好。

**Q5：@Transactional 自调用为什么会失效？**

A：Spring AOP 代理是**基于调用方拿到的 Bean 引用**进行拦截的。同类内部 `this.methodB()` 是直接调用目标对象的方法，`this` 是原始对象而非代理对象，因此不触发拦截器。解决办法：① 注入自身代理 `private OrderService self;`；② 启用 `@EnableAspectJAutoProxy(exposeProxy=true)`，方法中通过 `((OrderService) AopContext.currentProxy()).methodB()` 调用。

**Q6：Spring Boot 自动配置的原理？**

A：`@SpringBootApplication` 包含 `@EnableAutoConfiguration`，后者导入 `AutoConfigurationImportSelector`。该 Selector 读取 `META-INF/spring/AutoConfiguration.imports`（3.x）或 `spring.factories`（2.x）下所有自动配置类，每个配置类通过 `@ConditionalOnClass`、`@ConditionalOnMissingBean`、`@ConditionalOnProperty` 等条件注解判断是否生效。用户通过 `application.yml` 覆盖默认参数。

**Q7：Spring 事务的传播行为中，REQUIRES_NEW 和 NESTED 有什么区别？**

A：`REQUIRES_NEW` 是**完全独立的事务**，挂起当前事务，新事务失败提交后即使外层事务回滚也不会影响其结果；`NESTED` 是基于外层事务的**嵌套子事务**，保存点机制，外层回滚子事务也回滚，子事务回滚只回滚到保存点。REQUIRES_NEW 用得更多（如日志、消息发送场景）。

**Q8：Spring Cloud Alibaba 和 Spring Cloud Netflix 的核心区别？**

A：Netflix 组件（Eureka/Hystrix/Ribbon/Feign）大多已停更或进入维护模式。Alibaba 体系（Nacos/Sentinel/Seata/RocketMQ）在生产可用性、中文社区、二开难度上更适合国内业务。**字节、阿里、美团**面试更关注 Nacos / Sentinel / Seata 三件套。Spring Cloud Alibaba 完全兼容 Spring Cloud 标准接口，可以无痛接入 OpenFeign、Gateway 等官方组件。

**Q9：BeanFactory 和 FactoryBean 的区别？**

A：`BeanFactory` 是 IoC 容器最顶层接口；`FactoryBean` 是个特殊的 Bean，用于**自定义 Bean 创建逻辑**，例如 `SqlSessionFactoryBean`、`FeignClientFactoryBean`。当 Spring 检测到实现了 `FactoryBean` 接口时，调用 `getObject()` 拿到真实对象（要拿到 FactoryBean 本身，需要在名字前加 `&`）。

**Q10：Spring Boot 内嵌 Tomcat 的原理？**

A：Spring Boot 通过 `ServletWebServerFactoryAutoConfiguration` 自动配置 `TomcatServletWebServerFactory`，启动时调用 `getWebServer()` 创建 `Tomcat` 对象并启动。**关键点**：Spring Boot 不再要求打成 WAR 部署到外部 Tomcat，而是通过 `java -jar` 直接运行一个包含内嵌容器的 Spring Boot 应用，本质上是用 `Tomcat` API 手动启动了容器。

---

## 延伸阅读

1. **《Spring 实战（第 5 版）》** — Craig Walls，Spring 官方入门经典，适合快速建立全局认知。
2. **《Spring 源码深度解析（第 2 版）》** — 郝佳，源码级剖析 IoC、AOP、事务、MVC，适合面试突击。
3. **官方文档：Spring Framework Reference** — <https://docs.spring.io/spring-framework/reference/> 最权威，面试前翻 IoC/AOP/Transaction 章节。
4. **视频：黑马 / 尚硅谷 Spring 全家桶 + Spring Cloud Alibaba 实战** — 跟做一遍微服务项目，对理解 Nacos / Sentinel / Seata 编排帮助极大。