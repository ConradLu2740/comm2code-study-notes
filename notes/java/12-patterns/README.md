# 12 - 设计模式

## 概述

**设计模式（Design Pattern）** 是软件工程中针对**反复出现的问题**所给出的**可复用解决方案**，是前人在大量生产实践中总结出的最佳实践。GoF（Gang of Four，四人组）1994 年在《设计模式：可复用面向对象软件的基础》中提出 23 种经典模式，按目的分为三类：**创建型**（5）、**结构型**（7）、**行为型**（11）。设计模式不是具体的代码，而是**面向对象设计原则（SOLID+D）的具体应用**，掌握它们是阅读 JDK / Spring / MyBatis 等主流框架源码的钥匙。

**速览表**

| 分类 | 数量 | 关注点 | 面试高频 |
|------|------|--------|----------|
| 创建型 | 5 | 对象怎么创建 | 单例、工厂、建造者 |
| 结构型 | 7 | 类/对象怎么组合 | 代理、装饰器、适配器 |
| 行为型 | 11 | 对象间职责与通信 | 观察者、责任链、策略 |
| 六大原则 | — | OOP 设计的指导思想 | 必问，几乎每次面试都考 |

---

## 核心概念

### 一、六大设计原则

设计模式不是凭空出现，它们是六大原则的具体体现。**口诀：单开里依接迪**（SRP、OCP、LSP、DIP、ISP、LoD）。

| 原则 | 英文 | 一句话 | 反例 |
|------|------|--------|------|
| 单一职责 | SRP | 一个类只负责一件事 | 一个 `UserService` 既管登录又管订单 |
| 开闭原则 | OCP | 对扩展开放，对修改关闭 | 改一个需求就要动老代码 |
| 里氏替换 | LSP | 父类能出现的地方子类一定能 | 子类重写 `getX()` 抛异常破坏父类契约 |
| 依赖倒置 | DIP | 依赖抽象，不依赖具体 | `Service` 直接 `new MySQLConn()` |
| 接口隔离 | ISP | 客户端不应依赖不需要的接口 | 一个 `Animal` 接口让 `Dog` 实现 `fly()` |
| 迪米特法则 | LoD | 只与朋友说话 | A 调 B 调 C，A 居然直接调 C |

> **ponytail 提示**：面试问到六大原则时，**不要死背定义**，每个原则配一个**反例 + 一句话改进**会让回答非常出彩。例：「依赖倒置原则的经典案例是 Spring 的 DI，原本 `Service` 直接 `new Dao()` 紧耦合，现在通过构造器注入 `Dao` 接口，具体实现由 Spring 容器决定，调用方根本不感知 MySQL 还是 Oracle」。

### 二、创建型模式（5 种）

#### 2.1 单例模式（Singleton）

**唯一性 + 全局访问点**。五种实现对比：

| 实现 | 线程安全 | 懒加载 | 防反射 | 防反序列化 | 推荐度 |
|------|----------|--------|--------|------------|--------|
| 饿汉式（静态变量） | ✅ | ❌ | ❌ | ❌ | ⭐⭐ |
| 懒汉式（synchronized 方法） | ✅ | ✅ | ❌ | ❌ | ⭐ |
| 双重检查锁（DCL + volatile） | ✅ | ✅ | ❌ | ❌ | ⭐⭐⭐⭐ |
| 静态内部类 | ✅ | ✅ | ❌ | ❌ | ⭐⭐⭐⭐ |
| **枚举** | ✅ | ❌ | ✅ | ✅ | **⭐⭐⭐⭐⭐（Effective Java 推荐）** |

> 枚举单例为什么最安全？枚举类是 `final` 子类，无法被 `Class.forName().newInstance()` 反射攻击；JVM 保证枚举实例只被反序列化一次。**所有需要单例的 Bean 都可以用枚举实现**（但 Spring 默认不接管，需要 `@Component` 配合 `getInstance()`）。

#### 2.2 工厂方法模式（Factory Method）

**定义一个创建对象的接口，让子类决定实例化哪个类**。把 `new` 交给工厂，调用方只依赖抽象产品。

```java
public interface Logger { void log(String msg); }
public class FileLogger implements Logger { ... }
public class ConsoleLogger implements Logger { ... }

public interface LoggerFactory { Logger createLogger(); }
public class FileLoggerFactory implements LoggerFactory {
    public Logger createLogger() { return new FileLogger(); }
}
```

#### 2.3 抽象工厂模式（Abstract Factory）

工厂方法的升级版——**创建一组相关/相互依赖的对象家族**（产品族）。例：更换整套 UI 主题（按钮 + 文本框 + 滚动条都换成 Mac 风格），不用一个个替换。

#### 2.4 建造者模式（Builder）

**分离复杂对象的构建过程与表示**，让同样的构建过程可以创建不同的表示。**链式调用**是它的招牌。

```java
User u = User.builder()
    .name("Tom").age(18).email("tom@x.com")
    .build();
```

JDK：`StringBuilder`、`Stream.Builder`；第三方：`OkHttp Request.Builder`、`Lombok @Builder`、`MyBatis SqlSessionFactoryBuilder`、Spring 的 `UriComponentsBuilder`。

> 适用场景：**构造参数 ≥ 4 个 + 部分可选**。否则直接用静态工厂或 Lombok `@AllArgsConstructor`。

#### 2.5 原型模式（Prototype）

通过**复制**已有对象创建新对象。核心是 `Cloneable` + `clone()`。Java 字段拷贝默认是**浅拷贝**（引用类型共享内存），实现深拷贝需要重写 `clone()` 手动克隆引用字段，或用**序列化**（实现 `Serializable`，写入 `ObjectOutputStream` 再读出）。

> **使用场景**：对象创建成本高（如数据库连接、大对象拷贝）。Spring Bean 默认 scope = singleton，需要 prototype 时 `@Scope("prototype")`。

### 三、结构型模式（7 种）

#### 3.1 代理模式（Proxy）—— 面试超高频

为某对象提供**代理**以控制对它的访问。**Spring AOP 的核心**。

| 类型 | 原理 | 目标要求 | 代表 |
|------|------|----------|------|
| 静态代理 | 手写代理类 | 接口 | — |
| **JDK 动态代理** | `Proxy.newProxyInstance` + `InvocationHandler` | **必须实现接口** | Spring AOP 默认 |
| **CGLIB 动态代理** | 字节码技术生成子类 | 任意非 final 类 | Spring AOP（无接口时） |

> Spring AOP 选择策略：目标对象**有接口 → JDK Proxy**，否则 CGLIB。Spring Boot 2.x 之后通过 `spring.aop.proxy-target-class=true`（默认）**强制 CGLIB**，因为它性能更好、功能更强。

#### 3.2 适配器模式（Adapter）

**把一个类的接口转换成客户端期望的另一种接口**，让原本因接口不兼容不能一起工作的类可以一起工作。

- **类适配器**：继承被适配者（Java 不支持多继承，故不常用）
- **对象适配器**：持有被适配者引用（更灵活，常用）

JDK 经典案例：`InputStreamReader` 把 `InputStream`（字节流）适配成 `Reader`（字符流），本质是 `StreamDecoder` 这个适配器在干活。`Arrays.asList()` 把 `Array` 适配成 `List` 也是适配器。

#### 3.3 装饰器模式（Decorator）—— 必考

**动态地给对象添加额外职责**，比继承更灵活。**装饰器 = 包装**。JDK 经典案例：

```java
BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream("a.txt")));
```

每一层都是装饰：`FileInputStream`（被装饰）→ `InputStreamReader`（适配器 + 装饰器）→ `BufferedReader`（加缓冲）。

> **装饰器 vs 代理**：结构几乎一样，区别是**意图**。装饰器关注**为对象增强功能**（调用方知道在装饰），代理关注**控制访问**（调用方不感知代理存在）。

#### 3.4 桥接模式（Bridge）

**抽象与实现分离**，两者可以独立变化。JDBC `DriverManager` 是经典案例：`Driver` 接口（实现层）和 `Connection` 接口（抽象层）通过 `DriverManager` 桥接，MySQL、Oracle 各自实现 `Driver` 而不影响调用方。

#### 3.5 组合模式（Composite）

将对象组合成**树形结构**表示「部分-整体」层次，使客户端对单个对象和组合对象的使用**具有一致性**。例：`HashMap.Node` 嵌套 `Node`（红黑树节点）、文件系统（文件夹包含文件 + 文件夹）、UI 容器嵌套组件。

#### 3.6 外观模式（Facade）

为子系统中的一组接口提供**统一的高层接口**，客户端只和外观交互，降低耦合。`Spring ApplicationContext` 统一了 BeanFactory、AOP、事件、资源加载、国际化等子系统；`Tomcat RequestFacade` 包装了 `HttpServletRequest` 内部复杂实现。

#### 3.7 享元模式（Flyweight）

**共享细粒度对象**，减少内存占用。JDK 经典：

- `Integer.valueOf(int)`：-128~127 用 `IntegerCache` 缓存
- `String` 常量池
- `Boolean.valueOf`：只有 `TRUE`、`FALSE` 两个实例

> 享元的前提是**对象状态分为内部状态（共享）和外部状态（不共享）**。例如棋盘游戏，棋子的颜色是内部状态，位置是外部状态。

### 四、行为型模式（11 种）

#### 4.1 策略模式（Strategy）

**定义算法族，分别封装，可互相替换**。`Comparator` 是最经典例子：`Collections.sort(list, comparator)` 传入不同比较器就是不同排序策略。Spring `Resource` 接口和它的实现类（`FileSystemResource`、`ClassPathResource`、`UrlResource`）也是策略。

#### 4.2 模板方法模式（Template Method）

**定义算法骨架，把某些步骤延迟到子类实现**。父类控制流程，子类控制细节。`HttpServlet.service()` 调用 `doGet/doPost`，`AbstractApplicationContext.refresh()` 是 Spring 启动的模板方法（共 12 步，子类可重写 `postProcessBeanFactory` 等钩子）。

> 钩子方法 = 父类给出默认实现（往往是空方法）的回调。

#### 4.3 观察者模式（Observer）—— 必考

**一对多依赖**，目标对象状态变化时通知所有观察者。JDK `java.util.Observer/Observable` 已过时（Java 9），推荐 `PropertyChangeListener` 或 `EventListener`。`ApplicationEvent` + `@EventListener` 是 Spring 的事件机制，ZooKeeper `Watcher`、Redis Keyspace Notification、Vue/React 响应式都是观察者变体。

#### 4.4 责任链模式（Chain of Responsibility）—— 必考

**请求沿链传递，直到有对象处理它为止**。

| 框架 | 链 |
|------|-----|
| Servlet | `Filter` 链 |
| Spring MVC | `HandlerInterceptor` 链 |
| Netty | `ChannelPipeline`（`ChannelHandler` 链） |
| Dubbo | `Filter` 链 |
| Tomcat | `Valve` 链 |

#### 4.5 命令模式（Command）

**将请求封装为对象**，从而支持撤销、队列、日志等功能。`Runnable`、`Callable`、`Thread` 是命令；`PreparedStatement` 把 SQL 封装为可执行对象；Redis 命令、`MySQL binlog` 事件流也是命令。

#### 4.6 状态模式（State）

**对象内部状态改变时改变行为**，看起来像改了类。有限状态机（FSM）就是状态模式的应用。订单系统（待支付/已支付/已发货/已收货）、Spring `Lifecycle` 接口（停止中/运行中/已停止）都用了状态模式。

#### 4.7 备忘录模式（Memento）

**捕获并外部化对象的内部状态**，以便之后恢复。撤销（Ctrl+Z）、事务回滚、游戏存档、Redis `RDB` 快照都是这个模式。

#### 4.8 访问者模式（Visitor）

**在不修改已有类的前提下增加新操作**，通过「双重分派」实现。JDK `FileVisitor`、`AnnotationValueVisitor` 是访问者。常用于编译器 AST、XML 解析、复杂对象结构的导出。

> 缺点：违背开闭原则，**被访问的类每加一个字段，所有 Visitor 都要改**。所以没有特殊需求别用。

#### 4.9 中介者模式（Mediator）

**用一个中介对象封装一组对象之间的交互**，各对象不显式互相引用。`Executor` 框架（`ThreadPoolExecutor` 作为中介，封装任务与线程的交互）、`Tomcat Connector`（CoyoteAdapter 作为中介连接 `Connector` 和 `Container`）、空中交通管制系统是中介者。

#### 4.10 迭代器模式（Iterator）—— 最常用

**顺序访问集合元素，且不暴露底层表示**。`java.util.Iterator` 接口 `hasNext()`/`next()`/`remove()`，`Iterable` 配合 `for-each` 使用。`ArrayList`、`LinkedList` 内部都通过内部类 `Itr`/`ListItr` 实现迭代器。

#### 4.11 解释器模式（Interpreter）

**定义语法表示 + 解释器**，解释语言中的句子。Java `Pattern`（正则）、`Spring SpEL`、`MyBatis` 的动态 SQL、Java EL 表达式都是解释器。**一般用不到**，面试中提一下即可。

### 五、模式在 Spring 中的应用（面试加分点）

| 模式 | Spring 体现 |
|------|-------------|
| 工厂 | `BeanFactory`、`FactoryBean` |
| 单例 | Bean 默认 scope = singleton |
| 代理 | AOP（Spring 核心） |
| 模板方法 | `JdbcTemplate`、`RestTemplate`、`AbstractApplicationContext.refresh()` |
| 观察者 | `ApplicationEvent` + `@EventListener` |
| 策略 | `Resource`、`HandlerMapping`（多个 `HandlerMapping` 实现） |
| 适配器 | `HandlerAdapter`（`RequestMappingHandlerAdapter` 等） |
| 装饰器 | `BeanDefinitionDecorator`（XML 配置） |
| 责任链 | `HandlerInterceptor`、`Filter` |
| 组合 | `CompositeCacheManager`、`CompositeDataSource` |

### 六、模式在 JDK 中的应用

| 模式 | JDK 体现 |
|------|----------|
| 单例 | `Runtime.getRuntime()` |
| 工厂 | `URL`、`Calendar.getInstance()`、`Charset.forName()` |
| 装饰器 | IO 流（`BufferedReader` 装饰 `Reader`） |
| 适配器 | `InputStreamReader`、`Arrays.asList()` |
| 迭代器 | `Iterator`、`Enumeration` |
| 模板方法 | `AbstractList`、`InputStream.read()` |
| 享元 | `IntegerCache`、`String` 常量池 |
| 观察者 | `PropertyChangeListener`、`AWT Event` |
| 责任链 | ClassLoader 双亲委派模型 |

---

## 代码示例

### 示例 1：DCL 单例（生产可用）

```java
public class IdGenerator {
    // volatile 防止指令重排：对象 new 出来分三步（分配内存、初始化、赋值引用）
    // 没有 volatile 的话，另一个线程可能拿到未初始化的对象
    private static volatile IdGenerator instance;

    private IdGenerator() {
        if (instance != null) {
            throw new RuntimeException("禁止反射攻击");
        }
    }

    public static IdGenerator getInstance() {
        if (instance == null) {                    // 第一次检查：避免每次都进锁
            synchronized (IdGenerator.class) {
                if (instance == null) {            // 第二次检查：防止并发下多次创建
                    instance = new IdGenerator();
                }
            }
        }
        return instance;
    }
}
```

### 示例 2：JDK 动态代理（AOP 核心）

```java
public class MetricsProxy implements InvocationHandler {
    private final Object target;
    public MetricsProxy(Object target) { this.target = target; }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        long start = System.nanoTime();
        Object result = method.invoke(target, args);   // 真实方法调用
        long cost = System.nanoTime() - start;
        System.out.println(method.getName() + " cost: " + cost + "ns");
        return result;
    }
}

// 使用：必须传接口
UserService userService = (UserService) Proxy.newProxyInstance(
    classLoader,
    new Class[]{UserService.class},
    new MetricsProxy(new UserServiceImpl())
);
```

### 示例 3：责任链（手写 Spring 拦截器）

```java
public abstract class Handler {
    protected Handler next;
    public Handler setNext(Handler next) { this.next = next; return next; }
    public abstract void doFilter(Request req, Response resp);
    protected void next(Request req, Response resp) {
        if (next != null) next.doFilter(req, resp);
    }
}

public class AuthFilter extends Handler {
    public void doFilter(Request req, Response resp) {
        if (!checkToken(req)) { resp.setStatus(401); return; }
        next(req, resp);
    }
}
// 链路装配：new AuthFilter().setNext(new LogFilter()).setNext(new BizFilter());
```

### 示例 4：建造者 + 策略（贴近业务）

```java
public class Query<T> {
    private final String table;
    private final List<String> conditions = new ArrayList<>();
    private Comparator<T> sorter = (a, b) -> 0;  // 默认策略

    private Query(Builder b) { this.table = b.table; }
    public static Builder of(String table) { return new Builder(table); }
    public Query<T> where(String cond) { conditions.add(cond); return this; }
    public Query<T> sortBy(Comparator<T> s) { this.sorter = s; return this; }

    public static class Builder {
        private final String table;
        Builder(String table) { this.table = table; }
        public Query<String> build() { return new Query<>(this); }
    }
}
```

---

## 易错点 / 最佳实践

1. **DCL 必须加 `volatile`**：对象 `new` 操作会被重排序（分配内存 → 初始化 → 赋值引用可能被优化成 1→3→2），不 volatile 别的线程可能拿到没初始化的对象，**这是 NPE 隐患，不是性能问题**。

2. **Spring AOP 自调用失效**：`this.method()` 不走代理，必须**从 Spring 容器拿到自己的代理对象（`@Autowired ApplicationContext` + `getBean(本类.class)`）**才能让 AOP 生效。`@Transactional` 同理。

3. **装饰器 ≠ 代理**：装饰器**保留类型**（`BufferedReader` is-a `Reader`），增强功能；代理**不暴露实现**（`Proxy.newProxyInstance` 返回的是接口类型），控制访问。面试画图时容易混。

4. **不要为了用模式而用模式**：`UserService` 就三个方法别硬套工厂。判断标准是**「将来扩展时改多少地方」**，如果加一种实现要改 5 个文件 → 需要抽象；只改 1 个文件 → 不需要。

5. **策略模式的 if-else 替换**：超过 3 个分支的 if-else（按类型/枚举分发）几乎一定可以改成策略模式 + Map 注入，比维护一长串 `if-else` 干净得多。Spring 中常用 `Map<String, Handler>` 自动注入所有实现。

---

## 面试常见问题

**Q1：DCL 为什么要双重检查？只检查一次不行吗？**
A：外层 if 是**性能优化**（绝大多数情况下 instance 已被创建，不用进 synchronized）；内层 if 是**线程安全保证**（防止两个线程同时通过外层检查，在锁内重复创建）。少任何一层都会导致要么性能差，要么线程不安全。

**Q2：Spring AOP 是 JDK 动态代理还是 CGLIB？怎么选？**
A：两者都用。**有接口优先 JDK Proxy**（更轻量、Java 原生）；**无接口或方法不是 public/final 时用 CGLIB**（生成子类）。Spring Boot 2.x+ 默认 `spring.aop.proxy-target-class=true` 强制 CGLIB，性能更好。**Spring 5.x 引入 `objenesiscglib` 优化 CGLIB，无需构造器**。

**Q3：装饰器模式和代理模式区别？**
A：两者结构几乎一样（包装 + 持有原对象 + 实现同一接口），**核心区别在意图**：
- **装饰器**：调用方**明确知道**自己在装饰，目的是**叠加功能**（如 IO 加缓冲）。
- **代理**：调用方**不感知**代理存在，目的是**控制访问**（如 AOP 加日志/事务）。

**Q4：单例模式有几种写法？推荐哪种？**
A：5 种：**饿汉、懒汉、DCL、静态内部类、枚举**。**推荐枚举**（Effective Java 一书作者 Josh Bloch 强烈推荐），原因：写法最简单、线程安全、自动防反射（`Constructor` 拒绝反射创建）、防反序列化（枚举反序列化不调用 `readObject`）。**Spring 默认的 Bean 单例用 DCL/静态内部类**，因为需要懒加载和容器管理生命周期。

**Q5：观察者模式 vs 发布订阅模式区别？**
A：观察者模式中**观察者直接订阅目标对象**，两者有显式引用，强耦合；发布订阅模式通过**第三方事件总线**（broker）解耦，发布者和订阅者互相不知道对方存在。**Guava EventBus、Redis Pub/Sub、Kafka 是发布订阅**；**Java AWT 事件、Spring `ApplicationEvent`（同步模式）** 是观察者。

**Q6：策略模式和状态模式区别？**
A：策略模式中**策略之间互相独立**，由客户端选择（`Comparator.sort` 传哪个就用哪个）；状态模式中**状态之间会互相转换**（订单从待支付 → 已支付），状态转换由状态机内部驱动，客户端不需要知道当前状态。

**Q7：抽象工厂模式和工厂方法模式的区别？**
A：工厂方法针对**单一产品**的创建，通过子类化决定具体产品；抽象工厂针对**一族产品**（多个相关产品），通过组合一个「超级工厂」返回多个工厂方法。**简单区分：工厂方法 = `1 个产品 + 1 个工厂`；抽象工厂 = `N 个产品 + 1 个工厂`（里面 N 个方法）**。

**Q8：为什么模板方法模式用继承而不是组合？**
A：因为模板方法的核心是**控制算法流程**，子类只需实现差异点，**通过继承复用骨架代码**最直接。组合会变成「模板对象 + 钩子对象」的复杂协调，反而破坏「算法骨架」这个核心抽象。**Spring `AbstractApplicationContext.refresh()` 就是 12 步模板方法**。

**Q9：享元模式在 Java 中有哪些应用？**
A：`Integer.valueOf(int i)`：-128~127 缓存（`IntegerCache`）；`String` 常量池（`"abc"` 字面量）；`Long.valueOf`、`Boolean.valueOf` 都有缓存。**注意 `new Integer(1)` 不会走缓存**，编译器在 Java 9+ 已弃用该构造器。`Boolean` 只有 `TRUE`/`FALSE` 两个实例，任意 `Boolean.valueOf(true)` 都是同一个对象。

**Q10：设计模式 23 种中哪些在 Spring 中最重要？**
A：**单例、工厂、代理、模板方法、观察者、策略**这 6 个是核心中的核心。**代理 + 工厂 + 单例**是 Spring IoC/AOP 的基石（BeanFactory 工厂 + Bean 单例 + AOP 动态代理）；**模板方法 + 观察者 + 策略**是 Spring 行为扩展的灵魂（refresh 流程 + ApplicationEvent + Resource/HandlerMapping）。其他模式提一两个名字能背出场景即可。

---

## 延伸阅读

- **书籍**：《设计模式：可复用面向对象软件的基础》（GoF 原书，理论源头）；《Effective Java》第 3 版（Item 1-11 是设计模式的 Java 实践，必读）；《Head First 设计模式》（入门图解版）；极客时间王争《设计模式之美》（进阶 + 框架应用，**最推荐**）
- **在线**：[Refactoring Guru 设计模式](https://refactoringguru.cn/design-patterns)（中文图解最全，23 模式速查）；[Spring 官方文档 AOP 章节](https://docs.spring.io/spring-framework/docs/current/reference/html/core.html#aop)（看 AOP 怎么用代理实现）
- **视频**：尚硅谷宋红康《Java 设计模式》全集（B 站免费，覆盖 GoF 23 模式 + 框架源码）；黑马程序员《Spring 源码解析》（看 AOP 怎么用 CGLIB）
- **实战建议**：**学完立刻翻 Spring 源码**（`BeanFactory`、`ApplicationContext`、`AOP` 三个包），把 23 种模式对应到具体类上——这是面试时能讲出「Spring 为什么这么设计」的关键。
