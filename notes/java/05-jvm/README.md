# 05 - JVM

## 概述

**JVM（Java Virtual Machine）** 是 Java 平台「一次编写、处处运行」的核心载体，也是 Java 后端面试最高频的考察模块（大厂一面基本必问 GC、二面必问调优）。本模块按架构 → 内存 → 对象 → GC → 类加载 → 字节码 → 调优的顺序展开，覆盖字节、阿里、美团、网易等大厂的常考题与实战调优命令。

| 关键点 | 一句话速览 |
| --- | --- |
| 内存区域 | 线程私有（PC、栈、本地方法栈）vs 线程共享（堆、方法区） |
| 对象布局 | 对象头（Mark Word + 类型指针）+ 实例数据 + 对齐填充 |
| GC 算法 | 标记-清除 / 标记-整理 / 复制 / 分代收集 |
| 主流收集器 | Serial、ParNew、Parallel Scavenge、CMS、G1、ZGC、Shenandoah |
| 类加载 | 7 阶段 + 双亲委派模型（Bootstrap → Ext → App） |
| 调优核心 | 选对收集器 + 合理分配堆 + 控制停顿时间 |

---

## 核心概念

### 1. JVM 架构总览

JVM 主要由三部分组成：

- **类加载子系统（ClassLoader）**：负责把 `.class` 字节码加载到内存，并完成验证、准备、解析、初始化。
- **运行时数据区（Runtime Data Area）**：JVM 管理的内存区域，是 GC 和调优的主战场。
- **执行引擎（Execution Engine）**：包含解释器、JIT 编译器（Client/Server Compiler）、GC 回收器。
- **本地方法接口（JNI）**：调用 C/C++ 实现的 native 方法（如 `Object.hashCode`、`Unsafe`）。

HotSpot 把字节码编译成机器码的方式有两种：**解释执行**（启动快、运行慢）和 **JIT 即时编译**（热点代码编译成本地代码，运行快），HotSpot 默认采用**混合模式**（Mixed Mode），C1 + C2 编译器分层编译。

### 2. 运行时数据区（重点）

按线程归属划分：

| 区域 | 线程 | 作用 | 异常 |
| --- | --- | --- | --- |
| **程序计数器（PC）** | 私有 | 记录当前线程执行的字节码行号 | 唯一不会 OOM 的区域 |
| **虚拟机栈（JVM Stack）** | 私有 | 每个方法对应一个**栈帧**，存储局部变量、操作数栈等 | StackOverflowError / OOM |
| **本地方法栈** | 私有 | 服务于 native 方法 | StackOverflowError |
| **堆（Heap）** | 共享 | 对象实例、数组 | OOM（最常见的 OOM 来源） |
| **方法区（Method Area）** | 共享 | 类信息、常量、静态变量、JIT 编译产物 | OOM（Java 8 起叫元空间） |

**虚拟机栈的栈帧结构**（每个方法调用都会压入一个栈帧）：

- **局部变量表**：基本类型、引用、returnAddress，以 **Slot** 为单位（long/double 占 2 Slot）。
- **操作数栈**：方法执行时的中转区，字节码指令从这里取/放数据。
- **动态链接**：指向运行时常量池的方法引用，支持多态。
- **方法返回地址**：方法正常返回或异常退出后回到调用者的位置。

**堆的内部结构**（分代模型，G1 之前的主流模型）：

- **新生代（Young Gen）**：Eden + 2 个 Survivor（S0、S1），默认比例 `Eden : S0 : S1 = 8 : 1 : 1`，由 `-XX:SurvivorRatio=8` 控制。
- **老年代（Old Gen）**：存放长期存活的对象（默认年龄阈值 15，可由 `-XX:MaxTenuringThreshold` 调整）。
- 大对象（数组、字符串）直接进入老年代，由 `-XX:PretenureSizeThreshold` 控制。

**方法区的演进**：

- Java 7：叫 **永久代（PermGen）**，使用 JVM 堆内存，受 `-XX:MaxPermSize` 限制，**容易 OOM**（典型的 OOM: PermGen space）。
- Java 8 起：改为 **元空间（Metaspace）**，使用**本地内存**，默认无上限（受操作系统限制）。字符串常量池、静态变量从永久代移到堆。

### 3. 对象的创建与内存布局

**对象创建流程（new 一个对象发生了什么）**：

1. **类加载检查**：检查常量池是否已加载该类的符号引用，没有则执行类加载。
2. **分配内存**：从堆中划分一块确定大小的内存。
   - **指针碰撞（Bump the Pointer）**：堆规整时（Serial、ParNew），用过的内存放一边，空闲的放另一边，指针往空闲方向挪即可。
   - **空闲列表（Free List）**：堆不规整时（CMS），维护一个空闲块链表。
3. **初始化零值**：把分配到的内存全部初始化为 0（不包括对象头），保证字段不赋值也能直接用。
4. **设置对象头（Object Header）**：写入 Mark Word（哈希码、GC 分代年龄、锁状态）和类型指针（指向类元数据）。
5. **执行 `<init>`**：执行构造函数，按程序员意愿初始化对象。

**内存分配并发问题**：通过 **TLAB（Thread Local Allocation Buffer）** 解决——每个线程在 Eden 区预先分配一小块私有内存，避免多线程竞争。

**对象内存布局**（以 64 位 JVM 为例，普通对象头占 12 字节，数组占 16 字节）：

```
+-----------------------+----------------------+----------------+----------------+
|     Mark Word (8B)    | Klass Pointer (4B)   | Instance Data  |   Padding      |
+-----------------------+----------------------+----------------+----------------+
```

- **Mark Word**：哈希码、GC 年龄、锁标志位（无锁、偏向锁、轻量级锁、重量级锁）、偏向线程 ID。
- **类型指针**：指向方法区中类的元数据（开启压缩指针 `-XX:+UseCompressedOops` 时占 4 字节）。
- **实例数据**：字段的实际内容，按 `longs/doubles → ints/floats → shorts/chars → bytes/booleans → references` 顺序排列。
- **对齐填充**：保证对象大小是 8 字节的整数倍。

### 4. 垃圾回收算法

| 算法 | 原理 | 优点 | 缺点 |
| --- | --- | --- | --- |
| **标记-清除** | 先标记存活对象，再清除未标记的 | 实现简单 | 产生**内存碎片**、效率低 |
| **标记-整理** | 标记后把所有存活对象向一端移动，清理边界外内存 | **无碎片**，适合老年代 | 移动对象成本高 |
| **复制算法** | 内存分两块，只用一块，GC 时把存活对象复制到另一块 | **无碎片、效率高** | 内存利用率仅 50%（Apple 式回收通过 S0/S1 改善） |
| **分代收集** | 新生代用复制，老年代用标记-清除/整理 | 扬长避短 | 需要分代设计 |

**GC 类型**：

- **Minor GC / Young GC**：只回收新生代，**频繁、速度快**。
- **Major GC / Old GC**：只回收老年代（只有 CMS 有这个模式）。
- **Full GC**：回收整个堆 + 方法区，**慢、应尽量避免**。
- **Mixed GC**：G1 独有，回收新生代 + 部分老年代 Region。

### 5. 垃圾收集器（重点）

| 收集器 | 代 | 算法 | 目标 | 适用场景 |
| --- | --- | --- | --- | --- |
| **Serial** | 新生代 | 复制 | 单线程、停顿短 | 客户端、小内存 |
| **ParNew** | 新生代 | 复制 | Serial 的多线程版 | 配合 CMS |
| **Parallel Scavenge** | 新生代 | 复制 | **吞吐量优先** | 后台计算型 |
| **Serial Old** | 老年代 | 标记-整理 | 单线程 | 兜底 |
| **Parallel Old** | 老年代 | 标记-整理 | 吞吐量优先 | 配合 Parallel Scavenge |
| **CMS** | 老年代 | 标记-清除 | **停顿时间短** | 互联网中服务端（Java 9 弃用、Java 14 删除） |
| **G1** | 全堆 | 标记-整理 + 复制 | **可预测停顿** | 默认收集器（Java 9+） |
| **ZGC** | 全堆 | 染色指针 + 读屏障 | **<10ms 停顿** | 大堆、低延迟 |
| **Shenandoah** | 全堆 | 转发指针 + 读屏障 | 与 ZGC 类似 | RedHat 主推 |

**G1（Garbage-First）的核心思想**：把堆分成约 2048 个大小相等的 **Region**（每个 1~32M），跟踪每个 Region 的回收价值（垃圾占比），优先回收价值最大的 Region，所以叫 Garbage-First。它用 **Remembered Set** 记录跨 Region 的引用，避免全堆扫描。

**ZGC 为什么能做到 <10ms**：核心是 **染色指针（Colored Pointer）**——在 64 位指针的高位借 4 bit 存 GC 状态，配合 **读屏障（Load Barrier）** 在引用读取时触发对象迁移。GC 过程几乎所有阶段都能与用户线程并发，所以停顿时间不随堆大小增长（TB 级堆也是 <10ms）。

### 6. 类加载机制

**7 阶段**：

1. **加载（Loading）**：通过类的全限定名获取二进制字节流，生成 `Class` 对象。
2. **验证（Verification）**：文件格式、元数据、字节码、符号引用验证，**确保字节流符合 JVM 规范**。
3. **准备（Preparation）**：为类的**静态变量**分配内存并赋**零值**（如 `static int x = 1` 此时 x 是 0）。
4. **解析（Resolution）**：把常量池的**符号引用**替换为**直接引用**。
5. **初始化（Initialization）**：执行 `<clinit>`，给静态变量赋值 + 执行静态代码块（**唯一会主动引用的阶段**）。
6. **使用（Using）**：正常使用。
7. **卸载（Unloading）**：满足 3 个条件（类所有实例被回收、ClassLoader 被回收、Class 对象无引用）。

**触发初始化的 5 种主动引用**：`new`、`putstatic/getstatic`、`invokestatic`、反射调用、子类初始化触发父类初始化。**被动引用不会触发初始化**，比如 `SubClass.staticField`、`Class.forName` 指定 `initialize=false`。

**双亲委派模型**：类加载请求先交给父加载器，父加载器加载不到时才自己加载。

```
BootstrapClassLoader（启动类加载器，C++ 实现，加载 rt.jar）
        ↑
ExtClassLoader（扩展类加载器，加载 ext 目录）
        ↑
AppClassLoader（应用类加载器，加载 classpath）
        ↑
自定义 ClassLoader（如 Tomcat 的 WebAppClassLoader）
```

**为什么这样设计**：避免类重复加载、防止核心 API 被篡改（比如自己写一个 `java.lang.String` 也加载不了）。

**打破双亲委派的典型场景**：

- **Tomcat WebAppClassLoader**：每个 Web 应用独立加载自己的类，避免 Web 应用之间的类冲突（两个应用依赖同一个库的不同版本）。
- **OSGi**：模块化热部署。
- **JDBC SPI**：`DriverManager` 用 **线程上下文类加载器** 加载第三方驱动实现，破坏双亲委派的「向上委托」环节。

### 7. 字节码指令

使用 `javap -c -v ClassName` 反编译 class 文件：

- **invokeStatic**：调用静态方法。
- **invokeVirtual**：调用实例方法（虚方法，支持多态）。
- **invokeSpecial**：调用构造方法、私有方法、父类方法（**非虚方法**）。
- **invokeInterface**：调用接口方法。
- **invokedynamic**：JDK 7 引入，Lambda 表达式的实现基础。

---

## 代码示例

### 示例 1：用 javap 看懂方法调用的字节码

```java
public class Demo {
    public static void main(String[] args) {
        int a = 1 + 2;
        System.out.println("hello");
    }
}
```

```bash
javac Demo.java
javap -c -v Demo.class
```

输出片段：

```
0: iconst_1
1: iconst_2
2: iadd
3: istore_1
4: getstatic     #2  // Field java/lang/System.out:Ljava/io/PrintStream;
7: ldc           #3  // String hello
9: invokevirtual #4  // Method java/io/PrintStream.println:(Ljava/lang/String;)V
12: return
```

### 示例 2：常用调优参数（生产环境推荐配置）

```bash
# 堆内存：初始 = 最大，避免堆动态伸缩带来的性能波动
-Xms4g -Xmx4g

# 新生代大小（推荐占堆 1/4 ~ 1/3），注意 Xmn 设置后 SurvivorRatio 才生效
-Xmn1g -XX:SurvivorRatio=8

# 元空间（方法区）：一般 512M 足够
-XX:MetaspaceSize=256m -XX:MaxMetaspaceSize=512m

# 选用 G1 收集器，目标是最大停顿 100ms
-XX:+UseG1GC -XX:MaxGCPauseMillis=100

# OOM 时自动 dump 堆快照（事后分析关键）
-XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/tmp/oom.hprof

# 打印 GC 日志（JDK 9+ 统一格式）
-Xlog:gc*,gc+heap=info:file=/tmp/gc.log:time,uptime,level,tags:filecount=5,filesize=100m
```

### 示例 3：线上排查 4 件套（推荐 Arthas 替代前 3 个）

```bash
# 1. jstat 实时看 GC 情况，每秒采样 10 次
jstat -gcutil <pid> 1000 10

# 2. jmap 导出堆快照 + 查看内存占用 Top 对象
jmap -dump:format=b,file=heap.hprof <pid>
jmap -histo <pid> | head -30

# 3. jstack 看线程栈，定位死锁或 CPU 飙高
jstack <pid> | grep -A 20 "BLOCKED"

# 4. Arthas（强烈推荐，阿里开源）
dashboard           # 仪表盘
thread -n 3         # CPU Top 3 线程
trace com.demo.Service method  # 方法调用链路
```

### 示例 4：写一段会触发 OOM 的代码并验证

```java
public class OOMDemo {
    public static void main(String[] args) {
        // 限制堆为 50M：-Xms50m -Xmx50m
        List<byte[]> list = new ArrayList<>();
        try {
            while (true) {
                list.add(new byte[1024 * 1024]); // 每次 1MB
            }
        } catch (Throwable t) {
            System.out.println("触发 OOM: " + t);
            t.printStackTrace();
        }
    }
}
```

跑完用 `jmap -dump` 导出堆快照，导入 **MAT（Memory Analyzer）** 或 **VisualVM** 分析，看是哪个对象占用了最多内存——这是定位内存泄漏的标准流程。

---

## 易错点 / 最佳实践

1. **元空间无界≠永不 OOM**。Metaspace 用了本地内存，配 `-XX:MaxMetaspaceSize` 上限，否则物理内存耗尽会被操作系统 OOM Killer 干掉。
2. **不要用 -Xmn 配 G1**。G1 自己管理 Region 大小，手动设新生代大小反而会让 G1 的自适应策略失效。需要用 `-XX:G1NewSizePercent`、`-XX:G1MaxNewSizePercent`。
3. **CMS 已被弃用**。Java 9 开始 `UseConcMarkSweepGC` 被标记 deprecated，Java 14 彻底移除。新项目直接选 **G1**（默认）或 **ZGC**（超低延迟场景），不要再选 CMS。
4. **`System.gc()` 是定时炸弹**。它会触发 Full GC，建议在启动参数加 `-XX:+DisableExplicitGC` 禁用。阿里巴巴开发手册也明令禁止显式调用。
5. **字符串常量池在堆里**，不在方法区。`String.intern()` 在 JDK 7 之后会复用堆里的字符串对象，节省空间但**慎用**（JDK 7 之前会复制到 PermGen，可能 OOM）。
6. **不要把大对象（如大数组、长字符串）塞进新生代**。通过 `-XX:PretenureSizeThreshold`（仅 Serial/ParNew 有效，G1 无效）让大对象直接进老年代，避免新生代反复复制大对象。

---

## 面试常见问题

**Q1：说一下 JVM 内存区域划分，哪些是线程私有的？**

A：分 5 块。线程私有：**程序计数器、虚拟机栈、本地方法栈**（随线程生灭，栈内存连续分配）；线程共享：**堆、方法区（元空间）**。**程序计数器是唯一不会 OOM 的区域**，因为它只存一个行号。

**Q2：`new Object()` 经历了哪些步骤？**

A：5 步——① 类加载检查；② 分配内存（指针碰撞或空闲列表，多线程用 TLAB）；③ 初始化零值；④ 设置对象头（Mark Word + 类型指针）；⑤ 执行 `<init>` 构造函数。

**Q3：讲讲双亲委派模型，为什么要这样设计？**

A：类加载请求先委派给父加载器，父加载器加载不到时才自己加载。设计目的：① 避免类重复加载；② 防止核心 API 被篡改（如 `java.lang.String` 自己写也加载不了）。**打破双亲委派**的典型例子：Tomcat 的 WebAppClassLoader（多 Web 应用类隔离）、JDBC SPI（用线程上下文类加载器）。

**Q4：CMS 和 G1 的区别？**

A：CMS 是基于标记-清除的老年代收集器，会产生内存碎片；停顿时间短但不可预测。G1 把堆分成 Region，采用标记-整理 + 复制算法，**无碎片**，可设置 `MaxGCPauseMillis` 目标，**停顿时间可预测**。CMS 已弃用，G1 是 Java 9+ 的默认收集器。

**Q5：ZGC 为什么能做到 <10ms 的停顿？**

A：核心是**染色指针（Colored Pointer）**——在 64 位指针的高 4 bit 存 GC 状态（Marked0/Marked1/Remapped/Finalizable），配合**读屏障（Load Barrier）**在引用读取时自动处理对象迁移。整个 GC 过程几乎所有阶段都能与用户线程并发，停顿时间**不随堆大小增长**（TB 级也是 <10ms）。

**Q6：Minor GC、Major GC、Full GC 区别？**

A：Minor GC 只回收新生代，**频繁、STW 时间短**（通常几 ms~几十 ms）；Major GC 只回收老年代（只有 CMS 有这个模式）；Full GC 回收整个堆 + 方法区，**STW 时间长，应尽量避免**。Full GC 触发原因：老年代满、方法区满、显式 `System.gc()`、`Concurrent Mode Failure`（CMS 并发收集失败）。

**Q7：线上 OOM 怎么排查？**

A：四步走：① 启动参数加 `-XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=...`，OOM 时自动 dump；② 用 `jmap -dump:format=b,file=heap.hprof <pid>` 手动导出；③ 用 MAT 或 VisualVM 分析**Dominator Tree** 和 **Leak Suspects Report**，找占用最大的对象及其 GC Root；④ 修复代码（一般是内存泄漏，比如静态集合没清理、ThreadLocal 没 remove、未关闭的资源）。

**Q8：类加载的 7 阶段，「准备」和「初始化」有什么区别？**

A：**准备阶段**给类的**静态变量**分配内存并赋**零值**（如 `static int x = 1`，准备后 x=0）；**初始化阶段**才真正执行 `<clinit>`，按代码顺序给静态变量赋值 + 执行静态代码块（这时 x 才变成 1）。**只有 5 种主动引用会触发初始化**，被动引用不会（如通过子类引用父类的静态字段）。

**Q9：G1 的 Remembered Set 是什么？为什么需要它？**

A：Remembered Set（**RSet**）是每个 Region 维护的卡片表（Card Table），记录**其他 Region 引用本 Region 的对象**。GC 扫描时只需要扫描 RSet，不用全堆扫描，避免漏标对象。**代价**：RSet 本身占用堆内存（一般 1%~20%），写入时需要额外的 post-write barrier。

**Q10：TLAB 是什么？为什么需要它？**

A：TLAB（Thread Local Allocation Buffer）是 JVM 在 **Eden 区**为每个线程预先分配的一小块私有内存。分配对象时优先从 TLAB 拿，**避免多线程在堆上分配内存时的 CAS 竞争**。TLAB 默认开启（`-XX:+UseTLAB`），可通过 `-XX:TLABSize` 和 `-XX:TLABRefillWasteFraction` 调优。

---

## 延伸阅读

- **《深入理解 Java 虚拟机：JVM 高级特性与最佳实践》（第 3 版）**——周志明，JVM 圣经，**入门必读**，大厂面试题 90% 出自此书。
- **《Java 性能权威指南》（Java Performance: The Definitive Guide）**——Scott Oaks，**GC 调优与实战**的最佳参考书，有大量真实场景数据。
- **R 大（RednaxelaFX）的知乎与博客**——前 HotSpot 工程师，对 ZGC、GC 算法、对象内存布局有极深的技术解读，搜「R 大 JVM」即可。
- **OpenJDK 官方文档 [HotSpot VM**](https://openjdk.org/groups/hotspot/)：最权威的 JVM 实现细节，配合源码 `hotspot/src/share/vm/` 阅读，长期受益。