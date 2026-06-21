# 07 - Netty

## 概述

**Netty** 是一个 **异步事件驱动** 的网络应用框架，本质是对 JDK NIO 的高级封装和增强，极大地简化了 NIO 服务器/客户端的开发。它修复了 NIO 的大量已知 bug，提供了更易用、更稳定、更高性能的 API，是 Java 生态下构建 RPC 框架、IM、游戏服务器、长连接网关、HTTP 服务的事实标准。

| 关键点 | 说明 |
| --- | --- |
| 本质 | 基于 NIO 的事件驱动网络框架 |
| 线程模型 | 主从 Reactor 多线程（bossGroup + workerGroup） |
| 核心组件 | Bootstrap、EventLoopGroup、Channel、ChannelPipeline、ChannelHandler、ByteBuf |
| 典型应用 | Dubbo、RocketMQ、gRPC、Hystrix、Elasticsearch 等 |
| 学习意义 | 字节/阿里/美团等大厂后端高频考点，也是理解中间件源码的钥匙 |

> 一句话：**Netty = NIO + Reactor + 事件驱动 + 高性能 ByteBuf**。

---

## 核心概念

### 1. Netty 是什么？为什么要用 Netty？

直接用 JDK NIO 写一个可用的服务器非常痛苦：Selector 编写复杂、bug 多（**epoll 空轮询导致 CPU 100%**）、需要自己处理半包/粘包、需要自己实现多线程模型。

Netty 解决了这些问题：

- **API 简单**：几行代码就能起一个高性能服务器
- **高性能**：零拷贝、对象池化、Reactor 线程模型、锁优化
- **稳定可靠**：修复了 NIO 大量 bug（空轮询、Selector wakeup 异常等）
- **社区活跃**：被大量顶级开源项目（Dubbo、RocketMQ、Hadoop Avro、Elasticsearch）采用
- **协议丰富**：内置 HTTP、WebSocket、SSL、Protobuf 等编解码器

### 2. 整体架构与核心组件

```
                                                                ChannelPipeline
[Bootstrap/ServerBootstrap]                                       ┌──────────┐
        │                                                        │ Head ──→ H1 ──→ H2 ──→ Tail │
        ▼                                                        └──────────┘
[EventLoopGroup] ──▶ [EventLoop] ──▶ [Channel] ──▶ [ChannelHandlerContext]
                       (Reactor)        (Socket)        (上下文)
                                                          │
                                                          ▼
                                                       [ByteBuf]
```

- **Bootstrap / ServerBootstrap**：启动引导类，前者是客户端/UDP，后者是服务端/TCP
- **EventLoopGroup**：一组 EventLoop，bossGroup 负责 accept，workerGroup 负责 read/write
- **EventLoop**：无限循环，处理 IO 事件、普通任务、定时任务
- **Channel**：对 Socket 的抽象（每条连接一个 Channel）
- **ChannelPipeline**：每个 Channel 独有的一条**双向链表**，承载所有 Handler
- **ChannelHandler**：业务处理单元，分 Inbound（入站）和 Outbound（出站）
- **ByteBuf**：Netty 自研的字节容器，详见下文

### 3. 线程模型：主从 Reactor 多线程

```
                ┌──────────────────────────┐
                │     bossGroup (1~N)      │   只处理 OP_ACCEPT
                │  - EventLoop(boss)       │
                └────────────┬─────────────┘
                             │ accept 后注册到 workerGroup
                             ▼
                ┌──────────────────────────┐
                │    workerGroup (M 个)    │   处理 OP_READ/OP_WRITE
                │  - EventLoop(worker)     │
                └──────────────────────────┘
```

**核心规则**：**一个 Channel 在其生命周期内只会被一个 EventLoop 处理**，但一个 EventLoop 可以服务多个 Channel。Handler 中的代码默认由 IO 线程执行，所以**不能在 Handler 中做阻塞操作**，否则要丢到业务线程池（`ctx.channel().eventLoop().execute(...)` 或自定义 `ChannelInitializer`）。

### 4. EventLoop：无限循环的处理单元

EventLoop 本质是一个 `SingleThreadEventLoop`，内部跑一个 while 循环，处理三类任务：

1. **IO 任务**：select 出就绪的 channel，处理 OP_READ/OP_ACCEPT
2. **普通任务**：`execute(Runnable)` 提交，串行执行
3. **定时任务**：`schedule(Runnable, delay, unit)`

> 由于**单线程串行**，所以 Netty 的 Handler 默认线程安全，无需加锁——这也是 Netty 高性能的关键之一。

### 5. ChannelPipeline 与 ChannelHandler

- **ChannelPipeline**：双向链表，每个节点是 `ChannelHandlerContext`，持有前后节点的引用
- **传播顺序**：入站事件从 head → tail，出站事件从 tail → head
- **ChannelHandler**：
  - **Inbound**：`channelActive`、`channelRead`、`channelInactive`，对应 `ChannelInboundHandlerAdapter`
  - **Outbound**：`write`、`flush`、`close`，对应 `ChannelOutboundHandlerAdapter`
  - 简化基类 `SimpleChannelInboundHandler<I>`：会自动调用 `ReferenceCountUtil.release(msg)` 释放 ByteBuf

```java
public class NettyServerHandler extends ChannelInboundHandlerAdapter {
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        ByteBuf buf = (ByteBuf) msg;
        System.out.println("server received: " + buf.toString(CharsetUtil.UTF_8));
        ctx.writeAndFlush(Unpooled.copiedBuffer("pong\r\n", CharsetUtil.UTF_8));
    }
}
```

### 6. ByteBuf：Netty 封装的字节容器

相比 NIO 的 `ByteBuffer`，ByteBuf 有四大优势：

| 特性 | ByteBuffer (NIO) | ByteBuf (Netty) |
| --- | --- | --- |
| 读写索引 | 单指针，需 flip | readerIndex / writerIndex 分离，无需 flip |
| 扩容 | 固定容量 | 自动扩容（`AbstractByteBuf` 默认 4MB 上限） |
| 池化 | 不支持 | `PooledByteBufAllocator` 支持对象池 |
| 零拷贝 | 无 | `CompositeByteBuf`、`slice`、`duplicate`、`Unpooled.wrappedBuffer` |

常用 API：

```java
ByteBuf buf = Unpooled.buffer(256);
// 写
buf.writeBytes("hello".getBytes());
buf.writeInt(1024);
// 读
while (buf.isReadable()) {
    System.out.print((char) buf.readByte());
}
// 索引操作
buf.readerIndex(0);
buf.writerIndex(0);
// 释放（重要！避免内存泄漏）
ReferenceCountUtil.release(buf);
```

### 7. 编解码器与粘包拆包

**粘包拆包** 是 TCP 编程的经典问题，Netty 通过**解码器**统一处理：

| 方案 | 解码器 | 适用场景 |
| --- | --- | --- |
| 定长 | `FixedLengthFrameDecoder` | 每帧长度固定 |
| 分隔符 | `DelimiterBasedFrameDecoder` | 文本协议（按 `\r\n` 切分） |
| 长度字段 | `LengthFieldBasedFrameDecoder` | **工业级方案**，自定义二进制协议 |

`LengthFieldBasedFrameDecoder` 关键参数（务必记住）：

```
lengthFieldOffset   = 0    // 长度字段在帧中的偏移
lengthFieldLength   = 4    // 长度字段占字节数
lengthAdjustment    = 0    // 长度字段值与真实数据长度的偏差
initialBytesToStrip = 0    // 解码后跳过的字节数（可剥离长度头）
maxFrameLength      = 65535
```

### 8. 心跳机制：IdleStateHandler

```java
pipeline.addLast(new IdleStateHandler(30, 0, 0)); // 30 秒读空闲
pipeline.addLast(new HeartbeatHandler());          // 自定义 handler

public class HeartbeatHandler extends ChannelInboundHandlerAdapter {
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        if (evt instanceof IdleStateEvent) {
            ctx.close(); // 读空闲超时关闭连接
        }
    }
}
```

### 9. Netty 实战项目方向

- **IM 系统**：基于 Netty + Protobuf + WebSocket 的聊天室
- **RPC 框架**：Netty 做传输层 + 动态代理 + 服务注册发现（参考 Dubbo）
- **HTTP 网关**：基于 Netty 自研轻量网关，比 Spring Cloud Gateway 性能高一个量级
- **游戏服务器**：长连接 + 自定义协议

---

## 代码示例

### 示例 1：最简服务端启动

```java
public class NettyServer {
    public static void main(String[] args) throws InterruptedException {
        EventLoopGroup boss = new NioEventLoopGroup(1);
        EventLoopGroup worker = new NioEventLoopGroup();
        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(boss, worker)
             .channel(NioServerSocketChannel.class)
             .option(ChannelOption.SO_BACKLOG, 1024)
             .childOption(ChannelOption.SO_KEEPALIVE, true)
             .childHandler(new ChannelInitializer<SocketChannel>() {
                 @Override
                 protected void initChannel(SocketChannel ch) {
                     ch.pipeline()
                       .addLast(new LengthFieldBasedFrameDecoder(1024, 0, 4, 0, 4))
                       .addLast(new StringDecoder())
                       .addLast(new NettyServerHandler());
                 }
             });
            ChannelFuture f = b.bind(8080).sync();
            f.channel().closeFuture().sync();
        } finally {
            boss.shutdownGracefully();
            worker.shutdownGracefully();
        }
    }
}
```

### 示例 2：客户端连接

```java
public class NettyClient {
    public static void main(String[] args) throws InterruptedException {
        EventLoopGroup group = new NioEventLoopGroup();
        try {
            Bootstrap b = new Bootstrap();
            b.group(group)
             .channel(NioSocketChannel.class)
             .handler(new ChannelInitializer<SocketChannel>() {
                 @Override
                 protected void initChannel(SocketChannel ch) {
                     ch.pipeline().addLast(new NettyClientHandler());
                 }
             });
            ChannelFuture f = b.connect("127.0.0.1", 8080).sync();
            f.channel().closeFuture().sync();
        } finally {
            group.shutdownGracefully();
        }
    }
}
```

### 示例 3：自定义协议（长度字段拆包 + 业务 Handler）

```java
// 自定义协议：4 字节长度 + body
pipeline.addLast(new LengthFieldBasedFrameDecoder(1024 * 1024, 0, 4, 0, 4));
pipeline.addLast(new LengthFieldPrepender(4));
pipeline.addLast(new StringEncoder());
pipeline.addLast(new StringDecoder());
pipeline.addLast(new SimpleChannelInboundHandler<String>() {
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, String msg) {
        System.out.println("received: " + msg);
        ctx.writeAndFlush("ack: " + msg);
    }
});
```

### 示例 4：Netty + Protobuf（推荐生产方案）

```protobuf
// order.proto
syntax = "proto3";
option java_package = "com.example.proto";
message Order {
    int64 orderId = 1;
    string product = 2;
}
```

```java
// 编译后：OrderOuterClass.Order
pipeline.addLast(new ProtobufVarint32FrameDecoder());
pipeline.addLast(new ProtobufDecoder(OrderProto.Order.getDefaultInstance()));
pipeline.addLast(new ProtobufVarint32LengthFieldPrepender());
pipeline.addLast(new ProtobufEncoder());
pipeline.addLast(new SimpleChannelInboundHandler<OrderProto.Order>() {
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, OrderProto.Order msg) {
        System.out.println("orderId=" + msg.getOrderId());
    }
});
```

---

## 易错点 / 最佳实践

1. **`handler` vs `childHandler`**：前者只作用在 ServerBootstrap 的 ServerChannel 上，后者作用在每个 accepted 子 Channel 上。**业务 Handler 必须加到 childHandler**。

2. **Handler 中不要阻塞 IO 线程**。Netty 的 EventLoop 是单线程串行模型，IO 线程被阻塞 → 该线程上所有 Channel 卡死。需要阻塞操作（数据库/远程调用）时，要么丢到业务线程池（`ctx.channel().eventLoop().execute(...)` 仍然在 IO 线程，应改用 `ctx.executor().execute(...)` 或自定义 `EventExecutorGroup`），要么用 `SimpleChannelInboundHandler` 配合业务线程池。

3. **ByteBuf 释放**：入站消息（`channelRead` 的 msg）默认由 Netty 在 pipeline 末尾自动释放，但**如果中途抛出异常或被丢弃，可能泄漏**。`SimpleChannelInboundHandler` 已经自动处理 release；如果是继承 `ChannelInboundHandlerAdapter`，**务必在 finally 里 release 或调用 `ctx.fireChannelRead` 让下游处理**。

4. **`ctx.writeAndFlush` vs `channel.writeAndFlush`**：前者从当前 Handler 位置**逆向上溯出站 Handler**；后者从 pipeline 末尾（Tail）开始，**会跳过你前面添加的 Outbound Handler**（比如编码器），导致裸数据写出。**通常用 ctx**。

5. **优雅停机**：务必调用 `bossGroup.shutdownGracefully()` 和 `workerGroup.shutdownGracefully()`，否则 JVM 不退出。优雅停机会先关监听、再等任务完成、最后释放资源。

6. **NIO Bug 的前世今生**：Netty 4 通过重建 Selector（rebuildSelector）规避了 epoll 空轮询 bug；4.x 默认 1 秒检测一次 selector.select(timeout) 返回值为 0 的次数，超过 512 次就重建。

---

## 面试常见问题

**Q1：Netty 是什么？和传统 NIO 相比优势在哪？**
A：Netty 是基于 NIO 的**异步事件驱动**网络框架。优势：① API 简单，避免直接写 NIO 的 Selector 繁琐代码；② 修复 NIO 大量 bug（如 epoll 空轮询 CPU 100%、Selector wakeup 异常）；③ 内置主从 Reactor 线程模型，无需自己实现；④ 自带 ByteBuf 对象池化、零拷贝；⑤ 编解码器丰富，粘包拆包一站式解决。

**Q2：Netty 的线程模型是怎样的？为什么不用一个线程处理所有连接？**
A：**主从 Reactor 多线程**。`bossGroup` 只负责 accept 连接（通常 1 个线程），accept 后把 channel 注册到 `workerGroup`（默认 `2 * CPU` 个线程）的某个 EventLoop；该 channel 的所有 IO 事件都由同一个 EventLoop 处理。优点：① 职责分离，accept 和 read/write 互不阻塞；② 单 Channel 串行处理，业务无锁；③ 多 Channel 并行，整体吞吐高。

**Q3：Channel、ChannelPipeline、ChannelHandlerContext、ChannelHandler 的关系？**
A：Channel 代表一条连接；每个 Channel 有且仅有一条 ChannelPipeline（双向链表）；Pipeline 上每个节点是 ChannelHandlerContext（上下文，持有前后节点和 Handler 引用）；Context 包装真正的 ChannelHandler。事件通过 Context 传递，调用 `ctx.fireChannelRead(msg)` 传给下一个节点。

**Q4：ByteBuf 相比 ByteBuffer 有什么改进？**
A：① **读写索引分离**（readerIndex、writerIndex），不用 flip；② **自动扩容**（4MB 内按需翻倍，超过走申请新数组）；③ **支持池化**（`PooledByteBufAllocator`），减少 GC；④ **零拷贝**支持（`slice`、`duplicate`、`CompositeByteBuf` 合并多个 Buffer 而不复制）；⑤ **引用计数**管理生命周期，避免内存泄漏。

**Q5：什么是 TCP 粘包拆包？Netty 如何解决？**
A：TCP 是流式协议，发送方多次 write 可能被合并为一个 Segment（粘包），一次 write 也可能被拆成多个 Segment（拆包）。Netty 提供四种解码器：`FixedLengthFrameDecoder`（定长）、`DelimiterBasedFrameDecoder`（分隔符）、`LineBasedFrameDecoder`（按 `\r\n`）、`LengthFieldBasedFrameDecoder`（长度字段，**最通用**）。生产环境通常用 **长度字段 + Protobuf**。

**Q6：Netty 的零拷贝是怎么实现的？**
A：三层含义：① **操作系统层**：`FileRegion` + `transferTo`，sendfile 系统调用，磁盘直接到网卡；② **JVM 层**：避免数据复制——`CompositeByteBuf` 合并多个 Buffer 不复制；`slice()`/`duplicate()` 共享底层数组；`Unpooled.wrappedBuffer()` 包装已有字节数组；③ **应用层**：buffer 池化复用，减少 GC 压力。

**Q7：ChannelPipeline 中入站和出站事件的传播顺序？**
A：入站（Inbound）：`head → ... → tail`，从前往后；出站（Outbound）：`tail → ... → head`，从后往前。`ctx.fireChannelRead(msg)` 传给下一个 Inbound Handler；`ctx.write(msg)` 从当前 Outbound Handler 向前传播；`channel.write(msg)` 从 pipeline 尾部开始。

**Q8：IdleStateHandler 怎么实现心跳？**
A：构造参数 `(readerIdleTime, writerIdleTime, allIdleTime)`，底层基于 EventLoop 的 schedule 任务，到时间后判断 channel 上次读/写时间与当前时间的差。超时则触发 `IdleStateEvent`（READER_IDLE/WRITER_IDLE/ALL_IDLE），由业务 Handler 在 `userEventTriggered` 中处理（关闭连接或发心跳包）。

**Q9：Netty 为什么高性能？**
A：① **Reactor 线程模型** + 单 Channel 串行处理无锁；② **对象池化**（PooledByteBufAllocator、Recycler 轻量级对象池）；③ **零拷贝**；④ **串行无锁设计**，减少上下文切换；⑤ **软中断绑定 / IO 优化**（可选）；⑥ **精心调优的锁**（如 `FastThreadLocal` 替代 JDK ThreadLocal）。

**Q10：什么是异步？Netty 的 Future 和 JDK Future 有什么不同？**
A：Netty 所有 IO 都是异步，调用 `connect/write` 立刻返回 `ChannelFuture`。区别：JDK `Future` 需要 `get()` 阻塞取结果；Netty `ChannelFuture` 通过**监听器**（`addListener`）异步回调，支持注册多个监听器，链式传播，并且区分 fire success/failure。

---

## 延伸阅读

- 书籍：《**Netty 实战**》（Norman Maurer），Netty 4 作者之一，原理 + 实战。
- 书籍：《**Netty 进阶之路：跟着案例学 Netty**》，国内 Netty 早期布道师李林锋著，偏源码分析。
- 官方文档：[https://netty.io/wiki/user-guide-for-4.x.html](https://netty.io/wiki/user-guide-for-4.x.html)（英文，最权威）
- 视频：尚硅谷 / 黑马 Netty 专题（B 站搜索"Netty 入门到精通"），适合快速搭建知识框架。
- 源码阅读建议：先看 `io.netty.bootstrap` → `io.netty.channel.nio.NioEventLoop#run` → `io.netty.channel.ChannelPipeline` → `io.netty.buffer.PooledByteBufAllocator`，配合 `io.netty.example` 下的官方 demo。

> 学习节奏建议：先跟着 demo 跑通一个 echo server，再看 NioEventLoop 源码（约 800 行，但很精华），最后尝试自己写一个极简 RPC 框架（传输层就用 Netty），效果最好。