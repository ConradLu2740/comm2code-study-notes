# 04 - 并发编程

## 概述

Java 并发编程是后端面试必考模块，也是日常业务开发（异步、缓存、分布式协调）的底层基石。核心围绕 **线程、锁、内存模型、JUC 工具类** 四块展开，吃透本节即可覆盖字节/阿里/美团等大厂 P6-P7 面试 70% 的并发考点。

**关键点速览表：**

| 模块 | 核心作用 | 面试权重 |
|------|---------|---------|
| 线程基础 | Thread/Runnable/Callable | ★★ |
| synchronized / volatile | 内置锁与轻量级同步 | ★★★★ |
| JMM | 内存模型 + happens-before | ★★★★★ |
| AQS | JUC 锁与同步器基石 | ★★★★★ |
| 线程池 | ThreadPoolExecutor 七大参数 | ★★★★★ |
| ThreadLocal | 线程封闭与内存泄漏 | ★★★★ |
| 并发容器 | ConcurrentHashMap 等 | ★★★★ |

---

## 核心概念

### 1. 线程基础：三种创建方式

Java 提供三种创建线程的方式，**优先使用实现接口的方式**（避免单继承局限、解耦任务与线程）。

- **Thread**：继承 Thread 类，重写 `run()`。
- **Runnable**：实现 `Runnable` 接口，更灵活（lambda 写法 `() -> {}`）。
- **Callable + Future**：有返回值、可抛异常；通过 `Future.get()` 获取结果（阻塞）。

`FutureTask` 是 `Runnable` 和 `Future` 的合体实现，既能交给 `Thread` 执行，又能拿到返回值，是 `ExecutorService.submit()` 返回值的实际类型。

### 2. 线程生命周期

JDK 源码 `Thread.State` 枚举定义了 6 种状态：

```
NEW → RUNNABLE → (BLOCKED | WAITING | TIMED_WAITING) → TERMINATED
```

- **NEW**：新建未启动
- **RUNNABLE**：就绪或运行中（JVM 层面统一视为可调度）
- **BLOCKED**：等待进入 synchronized 块（被动阻塞）
- **WAITING**：调用 `wait()/join()/park()`（无限期，主动）
- **TIMED_WAITING**：`sleep(n)/wait(n)/join(n)`
- **TERMINATED**：执行结束

⚠️ **易混淆**：操作系统层面 RUNNING 已合并到 JVM 的 RUNNABLE 中；BLOCKED 仅适用于 synchronized 锁等待，Lock 等待是 WAITING。

### 3. synchronized：原理与锁升级

**底层原理**：JVM 通过 `monitorenter` / `monitorexit` 指令实现，对象头 Mark Word 存储锁信息。每个对象都关联一个 Monitor（监视器锁），同一时刻只允许一个线程持有。

**锁升级过程（JDK 1.6+ 优化）**：

```
无锁 → 偏向锁 → 轻量级锁 → 重量级锁
```

- **偏向锁**：单线程反复进入，无 CAS 开销（Mark Word 记线程 ID）。JDK 15 已默认禁用。
- **轻量级锁**：多线程交替执行，CAS 自旋抢锁。
- **重量级锁**：竞争激烈时升级到 OS 互斥锁（Mutex），挂起线程。

**锁优化**：
- **自旋锁**：短时间等待不挂起，循环 CAS（避免线程切换开销）。
- **锁消除**：JIT 编译器检测到不可能竞争的锁直接去掉（如 `StringBuffer` 局部变量）。
- **锁粗化**：把多次连续加锁解锁合并为一次。

### 4. volatile：可见性与有序性

**两大语义**：
1. **内存可见性**：写操作立即刷新到主内存，读操作从主内存重新加载。
2. **禁止指令重排序**：通过插入 **内存屏障**（Memory Barrier）实现。

```java
// 经典 DCL 单例
private volatile static Singleton instance;

public static Singleton getInstance() {
    if (instance == null) {
        synchronized (Singleton.class) {
            if (instance == null) {
                instance = new Singleton(); // 必须 volatile
            }
        }
    }
    return instance;
}
```

`new Singleton()` 不是原子操作，分为：①分配内存 ②初始化对象 ③引用赋值。无 volatile 时可能发生重排序，导致其他线程拿到未初始化的对象。

**vs synchronized**：volatile 不保证原子性（如 `i++` 仍可能出错），不阻塞线程，性能更好但能力更弱。

### 5. Java 内存模型（JMM）

JMM 抽象出 **主内存**（共享）和 **工作内存**（线程私有）两层。线程对变量的所有操作必须在工作内存中进行，不能直接读写主内存。

**happens-before 原则**（8 条）是判断数据是否存在竞争、是否需要加锁的黄金标准：

| 规则 | 说明 |
|------|------|
| 程序次序规则 | 同一线程内，前面的操作 happens-before 后面的 |
| 锁定规则 | unlock happens-before 后续的 lock |
| volatile 规则 | volatile 写 happens-before 后续的读 |
| 传递性 | A hb B，B hb C → A hb C |
| 线程启动 | `Thread.start()` hb 线程内任何操作 |
| 线程中断 | `interrupt()` hb 被中断线程检测到中断 |
| 线程终止 | 线程所有操作 hb 其它线程 `join()` 返回 |
| 对象终结 | 构造 hb `finalize()` |

**as-if-serial**：单线程内执行结果不能被重排序改变（保证程序正确性），多线程才需要关注可见性。

### 6. AQS：JUC 的基石

`AbstractQueuedSynchronizer` 是 `ReentrantLock`、`CountDownLatch`、`Semaphore` 等的父类。

**三要素**：
- **state**：`int` 类型，表示同步状态（如锁重入次数、剩余许可数）。
- **CLH 队列**：变种 CLH 锁队列，存放等待线程，FIFO。
- **模板方法**：`tryAcquire/tryRelease/tryAcquireShared/tryReleaseShared`，子类只需实现这些钩子，AQS 负责排队、阻塞、唤醒。

`ReentrantLock` 内部 `Sync` 继承 AQS，`state` 表示持有次数，0 表示未锁定；`acquire()` 失败则线程入队并 `LockSupport.park()` 阻塞。

### 7. JUC 核心类

- **ReentrantLock**：可重入互斥锁，支持公平/非公平、`tryLock()`、`lockInterruptibly()`、`Condition` 多条件变量。性能在 JDK 1.6 后与 synchronized 持平，但更灵活。
- **CountDownLatch**：一次性倒计时门闩，`countDown()` 减一，`await()` 阻塞至 0。适合「主线程等待多个子任务完成」。
- **CyclicBarrier**：可循环使用的屏障，线程到达后互相等待至全部就绪再一起放行。适合分治任务合并。
- **Semaphore**：信号量，控制并发许可数。适合限流、数据库连接池。
- **ReadWriteLock / StampedLock**：读写锁分离。StampedLock 是 JDK 8 引入的乐观读锁（版本戳机制），性能更优但不可重入。

### 8. 原子类与 CAS

`AtomicInteger`、`AtomicLong`、`AtomicReference` 等基于 **CAS**（Compare-And-Swap）实现无锁并发。

**CAS 原理**：`compareAndSet(expected, new)`，CPU 原语指令（`cmpxchg`），失败则重试。

**LongAdder**（JDK 8）：高并发下比 `AtomicLong` 性能更好。采用 **分段累加** 思想，将 value 分散到多个 `Cell` 数组，最终求和（牺牲空间换时间，适合写多读少）。

**ABA 问题**：CAS 误判「值未变」。解决：`AtomicStampedReference` 加版本号。

### 9. 线程池：ThreadPoolExecutor

**七大参数**（必背）：

```
corePoolSize      核心线程数（即使空闲也不回收）
maximumPoolSize   最大线程数
keepAliveTime     空闲线程存活时间
unit              时间单位
workQueue         任务队列（BlockingQueue）
threadFactory     线程工厂（命名、守护）
handler           拒绝策略
```

**工作流程**：
1. 任务到达，核心线程未满 → 创建核心线程执行。
2. 核心线程满 → 入队等待。
3. 队列满 → 创建非核心线程（最大线程数内）。
4. 达到最大线程 → 触发 **拒绝策略**。

**四种拒绝策略**：

| 策略 | 行为 |
|------|------|
| AbortPolicy | 抛 `RejectedExecutionException`（默认） |
| CallerRunsPolicy | 由提交任务的线程执行 |
| DiscardPolicy | 静默丢弃 |
| DiscardOldestPolicy | 丢弃队列头部任务 |

**Executors 为什么不推荐**：其四个工厂方法（`newFixedThreadPool` 等）使用 `LinkedBlockingQueue`（无界队列，OOM）或 `SynchronousQueue`（无缓冲，配合 Integer.MAX_VALUE 线程数易耗资源）。生产环境一律手动 `new ThreadPoolExecutor`。

### 10. ThreadLocal

**原理**：每个 `Thread` 内部维护一张 `ThreadLocalMap`（类似 HashMap），key 为 `ThreadLocal` 弱引用，value 为实际值。

**为什么 key 是弱引用**：防止 ThreadLocal 对象无法回收造成内存泄漏。但 value 仍是强引用，若线程长期存活（如线程池），仍会泄漏 → **必须手动 `remove()`**。

**典型场景**：Spring `RequestContextHolder` 传递请求上下文、MyBatis `SqlSession` 绑定、分布式链路追踪 traceId 传递。

`InheritableThreadLocal` 解决父子线程值传递问题，但子线程修改不影响父线程。ForkJoinPool 的 `InheritableThreadLocal` 与线程池复用冲突，推荐 `TransmittableThreadLocal`（TTL）。

### 11. 并发容器

- **ConcurrentHashMap**：JDK 1.7 分段锁（Segment），JDK 1.8 改为 **CAS + synchronized（锁桶头节点）** + 红黑树（链表 ≥8 转树）。并发度与桶数一致，性能大幅提升。
- **CopyOnWriteArrayList**：写时复制（数组），适合 **读多写极少** 场景（如白名单、监听器列表）。写操作加锁复制新数组，内存占用翻倍。
- **BlockingQueue**：阻塞队列，接口方法分四组（抛异常、返回特殊值、阻塞、超时）。常用实现：`ArrayBlockingQueue`（有界数组）、`LinkedBlockingQueue`（链表，默认 Integer.MAX_VALUE 易 OOM）、`PriorityBlockingQueue`（优先级）、`DelayQueue`（延迟）。

---

## 代码示例

### 示例 1：三种线程创建方式

```java
// 1. Runnable（最常用）
new Thread(() -> System.out.println("Hello Runnable")).start();

// 2. Callable + FutureTask（有返回值）
FutureTask<Integer> task = new FutureTask<>(() -> {
    TimeUnit.SECONDS.sleep(1);
    return 42;
});
new Thread(task).start();
System.out.println(task.get()); // 阻塞至结果返回

// 3. 线程池提交 Callable
ExecutorService pool = Executors.newFixedThreadPool(2);
Future<String> future = pool.submit(() -> "from pool");
System.out.println(future.get());
pool.shutdown();
```

### 示例 2：ReentrantLock + Condition 实现生产者消费者

```java
class BoundedBuffer {
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition notFull = lock.newCondition();
    private final Condition notEmpty = lock.newCondition();
    private final Queue<Integer> queue = new ArrayDeque<>();
    private final int capacity;

    public void produce(int item) throws InterruptedException {
        lock.lock();
        try {
            while (queue.size() == capacity) notFull.await();
            queue.offer(item);
            notEmpty.signal();
        } finally { lock.unlock(); }
    }

    public int consume() throws InterruptedException {
        lock.lock();
        try {
            while (queue.isEmpty()) notEmpty.await();
            int item = queue.poll();
            notFull.signal();
            return item;
        } finally { lock.unlock(); }
    }
}
```

### 示例 3：手动创建线程池（生产推荐）

```java
ThreadPoolExecutor executor = new ThreadPoolExecutor(
    4,                                  // core
    16,                                 // max
    60, TimeUnit.SECONDS,               // keepAlive
    new ArrayBlockingQueue<>(1000),     // 有界队列，防止 OOM
    new ThreadFactory() {
        private final AtomicInteger seq = new AtomicInteger(1);
        public Thread newThread(Runnable r) {
            return new Thread(r, "biz-pool-" + seq.getAndIncrement());
        }
    },
    new ThreadPoolExecutor.CallerRunsPolicy() // 拒绝时由调用方执行，削峰
);
```

### 示例 4：ThreadLocal + try-finally 防泄漏

```java
private static final ThreadLocal<UserContext> CTX = new ThreadLocal<>();

public void handle() {
    CTX.set(new UserContext(req.getUserId()));
    try {
        // 业务逻辑，任意处可直接 CTX.get()
        bizService.process();
    } finally {
        CTX.remove(); // 必须！线程池场景下尤其重要
    }
}
```

---

## 易错点 / 最佳实践

1. **synchronized 锁对象要一致**：`synchronized(this)` 锁的是对象实例，`synchronized(ClassName.class)` 锁的是类对象，混用等于没锁。

2. **volatile 不保证原子性**：`volatile int i; i++` 仍可能并发错误，需要 `AtomicInteger` 或 `synchronized`。

3. **ThreadLocal 必须 `remove()`**：尤其在线程池场景下，线程复用导致 value 堆积，最终 OOM。推荐 `try { ... } finally { remove(); }`。

4. **不要用 `Executors` 默认工厂**：`newFixedThreadPool` 用 `LinkedBlockingQueue` 无界，堆积任务会 OOM；`newCachedThreadPool` 允许 `Integer.MAX_VALUE` 线程数。生产必须手动 `new ThreadPoolExecutor` 并配置有界队列。

5. **CountDownLatch vs CyclicBarrier**：前者一次性、用完即弃；后者可循环复用、屏障点所有线程互相等待。选错会写得很别扭。

6. **锁粗化慎用**：循环内加锁可能引起 JIT 锁粗化，导致其他线程长时间等待。需要在「循环内」还是「循环外」加锁要明确判断。

---

## 面试常见问题

**Q1：synchronized 和 ReentrantLock 有什么区别？**

A：①synchronized 是 JVM 内置关键字，异常自动释放锁；ReentrantLock 是 API，需 `lock/unlock` 手动释放（推荐 `finally`）。②synchronized 不支持公平锁、中断、`tryLock()`、多条件变量；ReentrantLock 都支持。③JDK 1.6 后两者性能接近，synchronized 优化更彻底（偏向锁、轻量级锁），简单场景优先 synchronized。

**Q2：volatile 的实现原理是什么？**

A：①可见性：写 volatile 变量时，JVM 插入 `StoreLoad` 屏障，将工作内存值刷回主内存；读时插入 `LoadLoad` 屏障，从主内存重新加载。②禁止重排序：通过内存屏障禁止编译器和 CPU 的指令重排序优化。DCL 单例必须用 volatile 防指令重排序。

**Q3：什么是 happens-before？和 as-if-serial 有什么区别？**

A：happens-before 是 JMM 定义的多线程间的偏序关系（A hb B 表示 A 的结果对 B 可见），是判断数据竞争的依据；as-if-serial 是单线程内的串行语义保证。两者协同保证：单线程 as-if-serial 正确性 + 多线程 happens-before 可见性。

**Q4：ThreadPoolExecutor 的执行流程？七大参数关系？**

A：任务到达 → 核心线程未满则创建核心线程执行 → 核心满则入队 → 队列满则创建非核心线程（≤maxPoolSize）→ 超过 max 触发拒绝策略。核心思想：**优先扩容队列，再扩容线程**。队列必须是有界 BlockingQueue。

**Q5：ConcurrentHashMap 1.7 和 1.8 的区别？**

A：1.7 用 **Segment 分段锁**（默认 16 段，每段独立 ReentrantLock），并发度受限于段数。1.8 抛弃分段锁，改为 **CAS + synchronized 锁桶头节点** + 红黑树（链表 ≥8 且数组 ≥64 转树，≤6 退化为链表），并发度与桶数一致，并发扩容（多线程协助迁移），性能大幅提升。

**Q6：AQS 是什么？核心思想？**

A：`AbstractQueuedSynchronizer`，JUC 锁与同步器的基础框架。核心三要素：`volatile int state`（同步状态）、CLH 变种 FIFO 队列（管理等待线程）、模板方法（子类实现 `tryAcquire/tryRelease` 等）。AQS 负责排队、阻塞（`LockSupport.park`）、唤醒（`unpark`）等公共逻辑，子类只需关注 state 的更新语义。

**Q7：ThreadLocal 为什么会内存泄漏？为什么 key 用弱引用？**

A：ThreadLocalMap 的 Entry 继承 `WeakReference<ThreadLocal>`，key 是弱引用，value 是强引用。若 key 是强引用，线程池中的 Thread 长期存活 → ThreadLocalMap 永不被回收 → 内存泄漏。弱引用 key 在 GC 时会被回收，但 value 仍泄漏，所以**必须在用完后 `remove()`**。

**Q8：什么是 CAS？ABA 问题怎么解决？**

A：CAS（Compare-And-Swap）CPU 原子指令，比较预期值与当前值，相等则更新，否则重试。Java 通过 `Unsafe` 类封装为 `AtomicXxx.compareAndSet`。ABA 问题：值从 A→B→A，CAS 无法察觉中间变化。解决：`AtomicStampedReference` 增加版本号，每次更新版本号 +1。

**Q9：LongAdder 为什么比 AtomicLong 快？**

A：AtomicLong 多个线程竞争同一个 value，CAS 失败率高。LongAdder 把 value 分散到多个 `Cell`（`@Contended` 防止伪共享），每个线程分散写入不同 Cell，最终 `sum()` 时聚合。**写多读少**场景性能远超 AtomicLong，但牺牲了 `sum()` 的实时精度。

**Q10：wait() 和 sleep() 的区别？**

A：①`wait()` 释放锁，必须在 `synchronized` 内调用；`sleep()` 不释放锁。②`wait()` 是 `Object` 方法；`sleep()` 是 `Thread` 静态方法。③`wait()` 通过 `notify/notifyAll` 唤醒；`sleep()` 时间到自动唤醒。④`wait()` 通常用于线程间通信；`sleep()` 用于暂停当前线程。

---

## 延伸阅读

- **《Java 并发编程实战》**（Brian Goetz）：并发领域圣经，原理与实践并重，章节 5、10 是面试核心。
- **《深入理解 Java 虚拟机》（周志明）第 12、13 章**：JMM、线程安全、锁优化部分源码级解析。
- **JDK 源码**：`java.util.concurrent.locks` 包（`ReentrantLock`、`AbstractQueuedSynchronizer`）、`ThreadPoolExecutor`、`ConcurrentHashMap` —— 面试前必读。
- **官方文档**：[Java Concurrency Tutorial](https://docs.oracle.com/javase/tutorial/essential/concurrency/) —— Oracle 官方并发教程，配合 Liveness、Immutability 等章节食用更佳。
