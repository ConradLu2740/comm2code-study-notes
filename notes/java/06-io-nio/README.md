## 概述

Java IO 是 JDK 处理输入输出的核心 API 集合，覆盖 **BIO（同步阻塞）**、**NIO（同步非阻塞）**、**AIO（异步非阻塞）** 三种模型。理解 IO 模型是掌握 Netty、Tomcat、Redis、Kafka 等高性能中间件的必经之路，也是后端面试必考内容。

| 模型 | 全称 | 同步性 | 阻塞性 | 典型应用 |
|------|------|--------|--------|---------|
| BIO | Blocking I/O | 同步 | 阻塞 | 传统 Servlet、连接数少的内部工具 |
| NIO | Non-blocking I/O | 同步 | 非阻塞 | Netty、Tomcat 8+、Nginx、Redis |
| AIO | Asynchronous I/O | 异步 | 非阻塞 | 长轮询、大文件异步读写 |
| Signal-Driven | Signal-Driven I/O | 同步 | 非阻塞 | 较少直接使用，Linux 特有 |

**核心要点速览**

- BIO 一连接一线程，无法应对 C10K。
- NIO = Channel + Buffer + Selector，核心是**多路复用**。
- Reactor 是 NIO 的高级抽象模式，Netty 用主从 Reactor 多线程。
- 零拷贝（mmap / sendfile）是高吞吐文件的标配，Kafka 用得很深。
- AIO 在 Linux 平台实现不成熟，实际项目大多仍选 NIO。

---

## 核心概念

### 1. BIO（同步阻塞 IO）

JDK 1.4 之前唯一的 IO 模型，基于 **流（Stream）** 实现。流是单向的，要么是 InputStream，要么是 OutputStream。

**特点**

- 一连接一线程：服务端 accept 一个连接就 new 一个 Thread 去 read/write。
- 客户端并发量与线程数 1:1。
- 编程模型最简单，调试最直观。

**经典 ServerSocket 写法**

```java
ServerSocket server = new ServerSocket(8080);
while (true) {
    Socket socket = server.accept();   // 阻塞直到客户端连接
    new Thread(() -> {
        try (InputStream in = socket.getInputStream();
             OutputStream out = socket.getOutputStream()) {
            byte[] buf = new byte[1024];
            int n;
            while ((n = in.read(buf)) != -1) {  // 阻塞读
                out.write(buf, 0, n);
            }
        } catch (IOException e) {
            // log
        }
    }, "bio-worker").start();
}
```

**C10K 问题**：1 万个并发连接 = 1 万个线程。每个线程默认栈 1MB，仅虚拟内存就占 10GB，加上线程上下文切换开销（Linux 上 1ms 大约能完成 1 万次切换，但 1 万线程同时唤醒会让调度器抖动），单机撑不住。Steve Souders 2003 年提出 C10K 后，行业普遍转向 NIO + 多路复用方案。

### 2. NIO 三大核心组件

JDK 1.4 引入 java.nio 包，核心三件套：**Buffer + Channel + Selector**。

#### 2.1 Buffer（缓冲区）

本质是一块**连续内存区域**，所有数据读写都先经过 Buffer 再到 Channel。核心实现类 `ByteBuffer`（最常用），还有 `CharBuffer`、`IntBuffer`、`LongBuffer` 等。

四个核心属性：

- **capacity**：容量，分配后不可变。
- **position**：下一个读 / 写位置，初始为 0。
- **limit**：读写边界。写模式下等于 capacity，读模式下等于已写入数据末尾。
- **mark**：标记位置，配合 `reset()` 回到此处。

四者满足 `0 <= mark <= position <= limit <= capacity`。

**关键 API**：`flip()` 把写模式切到读模式（limit = position, position = 0），`clear()` 重置属性但数据仍在，`compact()` 把未读数据前移。

```java
ByteBuffer buf = ByteBuffer.allocate(1024);   // 堆内内存
buf.put("hello".getBytes());                  // 写入 5 字节，position=5
buf.flip();                                   // limit=5, position=0，切换读模式
byte b = buf.get();                           // 读一个字节
buf.clear();                                  // position=0, limit=capacity，可复用
```

**HeapByteBuffer vs DirectByteBuffer**：后者分配堆外内存（`-XX:MaxDirectMemorySize` 控制，默认与堆一致），避免 GC 拷贝。零拷贝、Socket 读写都要用 DirectByteBuffer。代价是分配 / 释放更贵，依赖 `Cleaner`（或 `sun.misc.Cleaner`）回收，泄漏排查也更麻烦。

#### 2.2 Channel（通道）

与流相比，Channel 是**双向的、可读可写**，可与 Buffer 直接交互。常见实现：

- **FileChannel**：文件读写，支持 `transferTo` / `transferFrom`（零拷贝）。
- **SocketChannel**：TCP 客户端，支持非阻塞 connect / read / write。
- **ServerSocketChannel**：TCP 服务端，用于监听 accept。
- **DatagramChannel**：UDP。
- **Pipe.SinkChannel / Pipe.SourceChannel**：进程内管道。

```java
try (FileChannel ch = new FileInputStream("a.txt").getChannel()) {
    ByteBuffer buf = ByteBuffer.allocate(1024);
    int n;
    while ((n = ch.read(buf)) != -1) {
        buf.flip();
        // ... 处理 buf
        buf.clear();
    }
}
```

#### 2.3 Selector（多路复用器）

NIO 区别于 BIO 的最核心机制。一个线程通过 Selector 监听成百上千个 Channel 的事件，**底层调 epoll（Linux）/ kqueue（macOS）/ IOCP（Windows）**。这是事件驱动 + 单线程处理多连接的物理基础。

四种事件类型（SelectionKey 常量）：

- `OP_READ = 1 << 0`：可读。
- `OP_WRITE = 1 << 2`：可写。
- `OP_CONNECT = 1 << 3`：连接建立（客户端）。
- `OP_ACCEPT = 1 << 4`：连接请求到达（服务端）。

### 3. NIO 编程流程

1. `Selector.open()` 创建 Selector。
2. 打开 `ServerSocketChannel`，`bind` 端口，**`configureBlocking(false)`**（不设非阻塞等于没开 NIO）。
3. `channel.register(selector, OP_ACCEPT)` 注册关心的事件。
4. 循环 `selector.select()`，阻塞直到至少一个 Channel 就绪或 wakeup。
5. `selectedKeys()` 拿到就绪的 `SelectionKey` 集合。
6. 迭代处理：根据 `key.isAcceptable() / isReadable() / isWritable()` 分支处理，**业务处理完必须 `it.remove()`**。
7. 关闭 Selector、Channel。

### 4. Reactor 模型

Doug Lea 在《Scalable IO in Java》中定义了 Reactor 模式，是 NIO 的高级抽象：**多路分发 + 事件驱动 + 异步处理**。本质是把「单线程 select 多 Channel」的思想扩展成可水平扩展的服务器架构。

#### 4.1 单 Reactor 单线程

- 所有 IO（accept / read / write）+ 业务处理都在同一个线程。
- 优点：实现最简单、无锁。
- 缺点：单线程瓶颈；业务耗时长会拖垮整个 IO 事件循环。
- 代表：**Redis 6.0 之前**的单进程模型（Redis 6+ 只是把网络 IO 拆到多线程，业务仍是单线程）。

#### 4.2 单 Reactor 多线程

- Reactor 线程负责 accept + read + dispatch。
- 业务交给 worker 线程池处理。
- 缺点：Reactor 线程仍单点，高并发下 accept / dispatch 也是热点。

#### 4.3 主从 Reactor 多线程

- **Main Reactor**（通常 1 个或少量线程）：只处理 accept，新连接分发给 Sub Reactor。
- **Sub Reactor**（多个线程）：每个 Sub Reactor 维护一个 Selector，处理 read / write。
- 业务仍交给独立的 worker 线程池。
- 代表：**Netty、Nginx、Memcached**。

Netty 的具体落地：

- `bossGroup` = MainReactor，负责 accept 与 register。
- `workerGroup` = SubReactor 数组，默认 `2 * CPU` 个线程，每个线程一个 Selector。
- `ChannelPipeline` + `ChannelHandler` 链处理业务，编解码、Idle、心跳等都作为 Inbound / Outbound handler 注入。

### 5. 零拷贝（Zero-Copy）

传统 `read + write` 把文件通过网络发送，需要 **4 次拷贝 + 4 次上下文切换**：

1. 磁盘 → 内核缓冲区（DMA）。
2. 内核缓冲区 → 用户缓冲区（CPU）。
3. 用户缓冲区 → Socket 缓冲区（CPU）。
4. Socket 缓冲区 → 网卡（DMA）。

**零拷贝**目标是减少中间两次 CPU 拷贝，让数据不经过用户态。

#### 5.1 mmap（内存映射）

`mmap()` 把文件映射到用户进程的虚拟内存，读写映射区相当于读写文件，无需先 `read` 到用户缓冲区。**3 次拷贝 + 4 次切换**，省掉一次 CPU 拷贝。Java 对应 `FileChannel.map()`。

适用：小文件、共享内存（多进程共享一块映射区）、Kafka 的索引文件。

#### 5.2 sendfile

Linux 2.1 引入 `sendfile(out_fd, in_fd, offset, count)`：数据从内核文件缓冲区直接到 Socket 缓冲区，全程内核态。**2 次拷贝（DMA→内核、内核→Socket）+ 2 次切换**，仍有一次 CPU 拷贝。

Linux 2.4 改进 `sendfile + DMA gather`：DMA 收集器直接把内核缓冲区数据送到网卡，**2 次 DMA + 0 次 CPU 拷贝 + 2 次切换**，真正零 CPU 拷贝。

Java 通过 `FileChannel.transferTo()` / `transferFrom()` 触发，本质是 sendfile 系统调用。

```java
try (FileChannel src = new FileInputStream("src.txt").getChannel();
     FileChannel dst = new FileOutputStream("dst.txt").getChannel()) {
    long pos = 0, size = src.size();
    while (pos < size) {
        pos += src.transferTo(pos, size - pos, dst);  // sendfile
    }
}
```

**注意**：`transferTo` 在 Linux 上若文件超过 2GB 或底层不支持，会回退到 mmap + 循环拷贝，吞吐量下降。生产代码必须**循环调用**直到 position 走完。

Kafka 用 `transferTo` 把 page cache 里的日志发送到 broker，吞吐高就是因为零拷贝 + 顺序写 + page cache。

### 6. AIO（异步非阻塞 IO）

JDK 1.7（NIO 2.0 同期）引入 java.nio.channels 包下的异步通道，**Proactor 模型**：发起 IO 请求后立即返回，OS 完成 IO 后回调通知线程。

核心 API：

- **AsynchronousChannel**：异步通道接口。
- **CompletionHandler<V, A>**：回调处理器，含 `completed(V result, A attachment)` 和 `failed(Throwable exc, A attachment)`。
- AsynchronousFileChannel、AsynchronousSocketChannel、AsynchronousServerSocketChannel。

```java
AsynchronousServerSocketChannel server = AsynchronousServerSocketChannel
        .open()
        .bind(new InetSocketAddress(8080));

server.accept(null, new CompletionHandler<AsynchronousSocketChannel, Void>() {
    @Override
    public void completed(AsynchronousSocketChannel ch, Void att) {
        server.accept(null, this);  // 递归 accept，保持异步接收
        ByteBuffer buf = ByteBuffer.allocate(1024);
        ch.read(buf, null, new CompletionHandler<Integer, Void>() {
            @Override
            public void completed(Integer len, Void att) {
                buf.flip();
                ch.write(buf, null, new CompletionHandler<Integer, Void>() {
                    @Override public void completed(Integer r, Void att) {}
                    @Override public void failed(Throwable exc, Void att) {}
                });
            }
            @Override public void failed(Throwable exc, Void att) {}
        });
    }
    @Override public void failed(Throwable exc, Void att) {}
});
```

**为什么 Java 生态几乎不用 AIO？**

Linux 平台 glibc 的 AIO 是**用线程池 + epoll 模拟的**，并不是真正的内核异步，性能与 NIO 持平甚至更差；Windows 的 IOCP 才是真正的 AIO。Netty 想跨平台一致 + 性能可控，坚定选了 NIO。Linux 5.1+ 引入 `io_uring` 才是真·异步，但 JDK 目前没有原生支持。

### 7. Java NIO 2.0（JDK 7+ 改进）

NIO 2.0 不只是加 AIO，更重要的是**重写了 java.io / java.nio 的文件系统 API**，引入 java.nio.file 包：

- **Path**：替代 File，不可变路径对象，支持符号链接。
- **Paths**：Path 工厂，`Paths.get("a.txt")`。
- **Files**：工具类，大量静态方法（copy / move / delete / readAllLines / write / walk）。
- **StandardOpenOption**：READ / WRITE / CREATE / APPEND / TRUNCATE_EXISTING 等。
- **WatchService**：监听目录的 CREATE / MODIFY / DELETE 事件。
- **AsynchronousFileChannel**：异步文件通道。
- **FileVisitor**：递归遍历目录（SimpleFileVisitor）。

```java
Path path = Paths.get("a.txt");
byte[] bytes = Files.readAllBytes(path);
Files.write(Paths.get("b.txt"), "hello".getBytes(),
        StandardOpenOption.CREATE,
        StandardOpenOption.TRUNCATE_EXISTING);

// 递归遍历
Files.walk(Paths.get("src"))
        .filter(p -> p.toString().endsWith(".java"))
        .forEach(System.out::println);

// 监听目录
WatchService watcher = FileSystems.getDefault().newWatchService();
Paths.get(".").register(watcher, StandardWatchEventKinds.ENTRY_CREATE);
while (true) {
    WatchKey key = watcher.take();
    key.pollEvents().forEach(e -> System.out.println(e.context()));
    key.reset();
}
```

**相比 File 的优势**：Path 不可变（线程安全）、支持符号链接与丰富属性；Files 抛 IOException 而不是返回 boolean，符合 fail-fast 原则；新增的 `walk` / `WatchService` 是 File 时代完全没有的能力。

### 8. 适用场景对比

| 场景 | 推荐 | 原因 |
|------|------|------|
| 连接数少（C < 1000）、短生命周期 | BIO | 简单直接 |
| 高并发短请求（HTTP API） | NIO + 主从 Reactor | 线程少、吞吐高 |
| 长连接 / 长轮询 / WebSocket | NIO + Idle 心跳 | AIO 在 Linux 不可靠 |
| 大文件传输 / 日志采集 | NIO + 零拷贝 | sendfile 提升吞吐 |
| RPC 框架（Dubbo / gRPC） | NIO + Netty | 成熟、社区强 |

---

## 代码示例

### 示例 1：NIO Echo Server（完整可跑）

```java
public class NioEchoServer {
    public static void main(String[] args) throws IOException {
        Selector selector = Selector.open();
        ServerSocketChannel server = ServerSocketChannel.open();
        server.bind(new InetSocketAddress(8080));
        server.configureBlocking(false);
        server.register(selector, SelectionKey.OP_ACCEPT);

        while (selector.select() > 0) {
            Iterator<SelectionKey> it = selector.selectedKeys().iterator();
            while (it.hasNext()) {
                SelectionKey key = it.next();
                if (key.isAcceptable()) {
                    SocketChannel client = ((ServerSocketChannel) key.channel()).accept();
                    if (client != null) {
                        client.configureBlocking(false);
                        client.register(selector, SelectionKey.OP_READ);
                    }
                } else if (key.isReadable()) {
                    SocketChannel client = (SocketChannel) key.channel();
                    ByteBuffer buf = ByteBuffer.allocate(1024);
                    int len = client.read(buf);
                    if (len > 0) {
                        buf.flip();
                        client.write(buf);
                    } else if (len == -1) {
                        client.close();
                    }
                }
                it.remove();   // 必须移除，否则会重复处理
            }
        }
    }
}
```

### 示例 2：零拷贝文件传输

```java
public static void copy(String src, String dst) throws IOException {
    try (FileChannel in = new FileInputStream(src).getChannel();
         FileChannel out = new FileOutputStream(dst).getChannel()) {
        long pos = 0, size = in.size();
        while (pos < size) {
            pos += in.transferTo(pos, size - pos, out);  // sendfile
        }
    }
}
```

### 示例 3：NIO 文件夹监控（WatchService）

```java
public class DirWatcher {
    public static void main(String[] args) throws IOException, InterruptedException {
        WatchService watcher = FileSystems.getDefault().newWatchService();
        Path dir = Paths.get(".");
        dir.register(watcher,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_DELETE,
                StandardWatchEventKinds.ENTRY_MODIFY);

        while (true) {
            WatchKey key = watcher.take();
            for (WatchEvent<?> event : key.pollEvents()) {
                System.out.printf("%s: %s%n", event.kind(), event.context());
            }
            if (!key.reset()) break;   // 资源失效后退出
        }
    }
}
```

### 示例 4：内存映射（mmap）读大文件

```java
public static String readBigFile(String path) throws IOException {
    try (FileChannel ch = new FileInputStream(path).getChannel()) {
        MappedByteBuffer buf = ch.map(FileChannel.MapMode.READ_ONLY, 0, ch.size());
        Charset cs = StandardCharsets.UTF_8;
        CharsetDecoder decoder = cs.newDecoder();
        return decoder.decode(buf).toString();
    }
}
```

---

## 易错点 / 最佳实践

1. **`selectedKeys()` 一定要 `it.remove()`**：迭代过程中不移除，下次 `select()` 时同一个 key 还会被回调，导致重复读、重复写，业务逻辑被执行多次。这是最常见的 NIO 新人 bug。

2. **`flip()` 时机搞错会读出 0 字节**：写完 buffer 没 `flip()` 就读，limit 还在 capacity 位置但 position 已经走到末尾，`get()` 会读到空或错位。**写完 → flip → 读 → clear** 是固定套路。

3. **`ServerSocketChannel` 必须 `configureBlocking(false)`**：否则 `accept()` 阻塞当前线程，Selector 根本不会接管。Channel 的非阻塞是 Selector 多路复用的前提。

4. **Netty 选 NIO 不是 AIO 的原因**：Linux glibc 的 AIO 是线程池 + epoll 模拟，Windows 的 IOCP 才是真 AIO，跨平台实现差异大；Netty 想跨平台 + 性能可控，只选 NIO。Linux 5.1+ 的 `io_uring` 才是真异步，但 JDK 还没原生支持。

5. **`transferTo` 在大文件场景必须循环调用**：Linux 实现里 `transferTo` 一次最多发 2GB，剩下的会回退 mmap；循环直到 position 走完才能用满零拷贝。

6. **DirectByteBuffer 泄漏排查**：堆外内存不归 GC 管，依赖 `Cleaner`；如果要主动释放可通过 `((DirectBuffer) buf).cleaner().clean()`（JDK 8 反射）或者改用 Java 9+ `java.lang.ref.Cleaner`。生产环境务必监控 `-XX:MaxDirectMemorySize`。

---

## 面试常见问题

**Q1：BIO、NIO、AIO 有什么区别？**
A：核心维度是同步性 + 阻塞性。BIO 同步阻塞，一连接一线程；NIO 同步非阻塞，多路复用（Selector）让一个线程处理多个连接；AIO 异步非阻塞，发起 IO 后立即返回，OS 完成后回调。Java 在 Linux 上 AIO 不可靠，实际工程几乎都用 NIO。

**Q2：什么是 C10K？怎么解决？**
A：C10K（Concurrent 10,000 Connections）指单机同时维持 1 万个并发连接。BIO 模型下 1 万连接 = 1 万线程，资源耗尽。解法：NIO 多路复用（epoll）+ 线程池处理业务 + 非阻塞 IO，主从 Reactor 是工业级答案。

**Q3：Selector 底层用 epoll 还是 select？**
A：Linux 上 JDK 默认实现是 **epoll**（`sun.nio.ch.EPollSelectorProvider`），macOS 用 kqueue，Windows 用 IOCP 模拟。epoll 没有 fd 上限（select 默认 1024）且复杂度 O(1)，所以高并发场景首选 epoll。

**Q4：Buffer 的 capacity / position / limit / mark 关系？**
A：`0 <= mark <= position <= limit <= capacity`。capacity 容量固定，position 当前读写位置，limit 读写边界，mark reset 回退点。`flip()` 把 limit 设为 position、position 归零；`clear()` 重置属性但数据仍在。

**Q5：Reactor 模型有几种？Netty 用哪种？**
A：三种：单 Reactor 单线程（Redis）、单 Reactor 多线程、主从 Reactor 多线程（Netty / Nginx）。Netty 用主从：`bossGroup` 是 MainReactor 处理 accept，`workerGroup` 是 SubReactor 池处理 read/write，业务交给独立 handler 链。

**Q6：零拷贝原理？Java 怎么用？**
A：零拷贝指数据不经过用户态 CPU 拷贝。Linux 有 `mmap`（省一次 CPU 拷贝）和 `sendfile`（DMA gather 后 0 次 CPU 拷贝）。Java 通过 `FileChannel.map()`（mmap）和 `FileChannel.transferTo() / transferFrom()`（sendfile）触发。Kafka、Netty 文件传输都靠它提吞吐。

**Q7：为什么 NIO 用 Channel 而不是 Stream？**
A：Channel 双向、可读可写、与 Buffer 直接交互，支持异步和零拷贝；Stream 单向且阻塞，只能和数组交互。Channel 是 NIO 与传统 IO 的核心区别。

**Q8：`select()` 和 `selectedKeys()` 的关系？**
A：`select()` 阻塞直到至少一个 Channel 就绪（或 wakeup / 超时），返回就绪数量；`selectedKeys()` 返回就绪的 SelectionKey 集合，需要手动 `remove()` 已处理的 key，下次 `select()` 才会重新检测。

**Q9：AIO 为什么在 Java 生态用得少？**
A：Linux glibc 的 AIO 是线程池模拟，不是真异步，性能与 NIO 持平或更差；Windows 的 IOCP 才是真 AIO。Netty 想跨平台一致只选 NIO。Linux 5.1+ 的 io_uring 是新希望，但 JDK 还没原生支持。

**Q10：NIO 2.0 的 Path 和 Files 比 File 好在哪？**
A：Path 不可变（线程安全）、支持符号链接和丰富属性；Files 工具类抛 IOException 替代 File 返回 boolean，符合 fail-fast；`walk` 递归遍历和 `WatchService` 监听目录事件是 File 时代完全没有的能力。

---

## 延伸阅读

- **《Netty 实战》Norman Maurer 等著**：第 4 章 NIO 详解 + 第 5 章 Netty 架构，Reactor 模型与 Netty 主从多线程落地讲得最透彻。
- **Doug Lea《Scalable IO in Java》**：Reactor 模式的开山论文，所有讲 Netty 的资料都引用这篇。直接搜 "Doug Lea Scalable IO in Java PDF" 即可下载。
- **JDK 官方文档 java.nio.channels / java.nio.file**：https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/package-summary.html，建议重点读 `Selector` / `FileChannel` / `AsynchronousChannel` 的 javadoc。
- **B 站搜 "Java NIO 深入剖析"**：韩顺平或黑马程序员的 NIO + Netty 教程，面试前快速过一遍原理和手写 Reactor 代码，性价比高。