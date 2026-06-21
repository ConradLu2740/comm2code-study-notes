# 11 - 消息队列（Message Queue）

## 概述

**消息队列（MQ）** 是分布式系统中的异步通信中间件，本质是一个「生产者 - 队列 - 消费者」模型，通过把消息的发送与处理解耦，实现**异步、削峰、解耦**三大核心价值。Java 后端面试中，MQ 是必问的高频模块，重点考察原理、可靠性保证、消息幂等和顺序性，以及 Kafka / RabbitMQ / RocketMQ 的对比选型。

**关键点速览**

| 维度 | Kafka | RabbitMQ | RocketMQ |
| --- | --- | --- | --- |
| 定位 | 日志流式处理、大数据管道 | 传统企业级消息 | 阿里开源，金融级可靠消息 |
| 协议 | 自定义二进制 | AMQP | 自定义 |
| 性能 | 极高（百万级 TPS） | 较高（万级 TPS） | 高（十万级 TPS） |
| 顺序消息 | 分区内有序 | 单队列有序 | 严格顺序 |
| 事务消息 | 支持（0.11+） | 不支持原生 | 支持（核心特性） |
| 延迟消息 | 不支持原生 | TTL + DLX 或插件 | 内置多级延迟 |
| 适用场景 | 日志、大数据、流计算 | 业务解耦、复杂路由 | 交易、订单、金融 |

> 选型建议：日志/流计算选 **Kafka**；业务解耦、复杂路由选 **RabbitMQ**；订单/交易/金融选 **RocketMQ**。

---

## 核心概念

### 1. 为什么用 MQ

**异步**：下单链路要调「扣库存 200ms / 支付 300ms / 发短信 500ms」，串行 1000ms。引入 MQ 后，主流程 50ms 返回，剩余逻辑异步处理，RT 降一个量级。

**削峰**：秒杀瞬时 10 万 QPS，下游 DB 只能扛 1 万。用 MQ 缓存请求洪峰，下游按自己节奏消费，避免被打挂。

**解耦**：订单系统发一条 `order_created` 消息，库存、支付、物流、营销各自订阅，互相不感知。新增订阅方无需改订单代码。

> MQ 不是银弹。引入 MQ 后会**降低强一致性、提高复杂度**，需要幂等、补偿、监控。简单同步调用能搞定的场景不要硬上 MQ。

### 2. 消息模型

- **点对点（Point-to-Point / Queue）**：消息进入队列后，只能被**一个**消费者消费，消费完即删除。RabbitMQ 默认模式、ActiveMQ。
- **发布订阅（Pub-Sub / Topic）**：发布者将消息发到 Topic，**所有订阅者**都能收到，每条消息会被所有订阅者消费一次。Kafka、RocketMQ 的 Topic 模型。

Kafka 的「消费组」模型是两者的结合：**同一个 Group 内是点对点**（一条消息只被一个消费者消费），**不同 Group 是发布订阅**（每条消息所有组都能消费到）。

### 3. 消息可靠性通用问题

消息从生产到消费会经过 **Producer → Broker → Consumer** 三个节点，每个节点都有丢消息的可能。

| 问题 | 根本原因 | 解决思路 |
| --- | --- | --- |
| 消息丢失 | 网络抖动、Broker 宕机、Consumer 没落库就 ack | 生产者 ack + 持久化 + 消费后提交 |
| 重复消费 | 至少一次语义 + 消费失败重试 | 幂等：唯一 ID + 去重表 |
| 消息顺序 | 多分区/多消费者并行 | 单分区消费 + 业务 key 路由 |
| 消息积压 | 消费速度 < 生产速度 | 扩容消费者 + 降级非核心 + 监控告警 |

> 可靠性、幂等、顺序、积压是 MQ 面试四大问题，必背。

---

### 4. RabbitMQ 详解

#### 架构

```
Producer ──> Exchange ──(Binding + Routing Key)──> Queue ──> Consumer
```

**核心角色**：
- **Producer**：消息生产者
- **Exchange**：交换机，**不存消息**，只做路由
- **Queue**：真正存消息的队列
- **Consumer**：消费者
- **Binding**：Exchange 和 Queue 的绑定规则

#### Exchange 类型

| 类型 | 路由规则 | 典型场景 |
| --- | --- | --- |
| **direct** | Routing Key 完全匹配 | 精准路由（点对点） |
| **topic** | `*` 匹配一个单词，`#` 匹配多个 | 多级路由（订单状态变化） |
| **fanout** | 忽略 Routing Key，广播 | 日志广播、配置下发 |
| **headers** | 匹配消息 header（不常用） | 复杂属性路由 |

#### 高级特性

- **死信队列（DLX, Dead Letter Exchange）**：消息被拒（nack/reject 且 requeue=false）、TTL 过期、队列满 → 进入死信交换机。用来兜底异常消息。
- **延迟队列**：RabbitMQ 原生不支持任意延迟。两种实现：
  1. **TTL + DLX**：消息设 TTL 过期后自动路由到死信队列，监听死信队列即可。
  2. **`rabbitmq-delayed-message-exchange` 插件**：装插件后交换机类型选 `x-delayed-message`，发消息时设 `x-delay` 头，单位毫秒。
- **消息确认**：
  - `channel.basicAck(deliveryTag, false)`：手动 ack，告诉 Broker 已成功处理
  - `channel.basicNack(deliveryTag, false, true)`：处理失败，第三个参数 `requeue=true` 重新入队，`false` 走死信
- **QoS 预取（Prefetch）**：`channel.basicQos(10)` 控制单消费者最多未 ack 的消息数，避免一次推太多把消费者打挂。

#### 集群模式

- **普通集群（默认）**：多节点共享元数据（哪些队列存在），但**队列只在单个节点上**。其他节点接收消息后转发到队列所在节点，性能有损耗。
- **镜像集群（Mirror Queue）**：队列在多个节点上镜像，主从同步。**生产环境推荐**，任一节点宕机消息不丢。配置 `policy` 设置 `ha-mode=all`、`ha-sync-mode=automatic` 即可开启。

---

### 5. Kafka 详解

#### 架构

```
Producer ──> Broker (Topic ──> Partition[0..n]) ──> Consumer Group
```

**核心角色**：
- **Producer**：发消息到指定 Topic
- **Broker**：Kafka 服务节点，一个 Broker 上有多个 Partition
- **Topic**：逻辑分类，**物理上拆成多个 Partition**
- **Partition**：**Kafka 的最小并行单位**，每个 Partition 是一个有序、不可变的日志文件
- **Replica**：每个 Partition 有多个副本（默认 1），1 个 Leader + N 个 Follower
- **Consumer Group**：消费组，组内**每个 Partition 只能被一个消费者消费**

#### 核心概念

- **Partition（分区）**：提高并发。1 个 Topic 3 个 Partition，最多 3 个消费者并行。
- **Replica（副本）**：保证高可用。`replication.factor=3` 表示每分区 3 副本。
- **ISR（In-Sync Replicas）**：与 Leader 保持同步的副本集合。Follower 落后太多会被踢出 ISR。**只有 ISR 里的副本才有资格被选为 Leader**。
- **HW（High Watermark）高水位**：消费端能看到的最大 offset，HW 之前的数据对消费者可见。
- **LEO（Log End Offset）**：当前日志末端 offset，新消息写入位置。
- **Epoch（纪元）**：Kafka 0.11+ 引入，Controller 每次变更 Leader 自增。Follower 通过比较 Epoch 拒绝「过期」的 Leader 写请求，**防止脑裂导致数据不一致**。

#### 选举

- **Controller 选举**：Kafka 集群有一个 Controller 负责管理所有 Partition 的 Leader/Follower 状态。
  - **旧版**（依赖 ZooKeeper）：所有 Broker 抢 `/controller` 临时节点，谁创建成功谁就是 Controller。
  - **新版（KRaft，Kafka 2.8+ GA，3.3+ 生产可用）**：用 Kafka 内部 `__cluster_metadata` topic 做 Raft 选举，去 ZK。

#### 消息可靠性

`acks` 是 Producer 端最关键的配置：

| acks | 行为 | 可靠性 | 性能 |
| --- | --- | --- | --- |
| `0` | 发完不等待 | 最低 | 最高 |
| `1` | Leader 写入即返回 | 中等 | 高 |
| `all` (或 `-1`) | 等待所有 ISR 同步完成 | 最高 | 最低 |

**幂等性**：`enable.idempotence=true`（Kafka 0.11+ 默认开启）。Producer 自动给每条消息加 PID + Sequence Number，Broker 端去重，**解决单分区单会话内的重复**。

**事务**：`transactional.id` + `initTransactions()` + `beginTransaction()` + `commitTransaction()`。跨分区跨 Topic 的原子写，但吞吐量降一个量级，生产环境慎用。

#### 消费模型

- **至少一次（at-least-once）**：默认。Consumer 处理完手动 commit offset，处理失败不 commit，下次重试。
- **最多一次（at-most-once）**：拉到就 commit，挂了丢消息。几乎不用。
- **正好一次（exactly-once）**：
  - 幂等 + 事务 + 消费 offset 也作为消息写入下游存储（`read_committed` 隔离级别）
  - 仅 Kafka → Kafka 场景能严格保证，跨系统只能「业务幂等」近似。

> Kafka 写入非常快：顺序写磁盘 + 零拷贝（sendfile 系统调用）+ 页缓存 + 批量压缩 + 分区并行，**单机百万 TPS 是常规操作**。

---

### 6. 消息幂等性

**为什么需要？** 至少一次语义保证消息不丢，但代价是可能重复。消费端必须**幂等**。

**三种实现方案**：

1. **业务唯一 ID + 数据库唯一索引**（最常用）
   - 生产时给消息加 `biz_id`，消费时 `INSERT ... ON DUPLICATE KEY UPDATE` 或靠唯一索引报错兜底。
2. **Redis SETNX**
   - 消费前 `SET biz_id 1 NX EX 86400`，返回成功才处理。
3. **消费前查去重表**
   - 维护一张 `message_processed(message_id, processed_at)` 表，消费前 SELECT。

> 幂等性是高并发系统的**必修课**，而不是可选项。面试时一定要主动说出来。

---

### 7. 消息顺序性

**Kafka**：
- Topic 单 Partition 内部天然有序
- Producer 发送时把**相同业务 key** 路由到同一 Partition（`key.hashCode() % partitionNum`）
- Consumer 单线程消费该 Partition
- **代价**：单分区吞吐有上限，顺序和并发不可兼得

**RabbitMQ**：
- 路由到**同一 Queue** + 该 Queue **单消费者** + 手动 ack
- 多消费者会导致同一 Queue 内的消息分发到不同消费者，破坏顺序

> 顺序性的核心：**生产有序 + 路由集中 + 消费单线程**。任何一环被打破，全局顺序都没了。

---

### 8. 分布式事务

跨服务数据一致性，MQ 通常用来实现**最终一致性**。

| 方案 | 一致性 | 复杂度 | 性能 | 场景 |
| --- | --- | --- | --- | --- |
| **2PC** | 强一致 | 中 | 低 | 数据库层，强阻塞 |
| **TCC** | 最终一致 | 高 | 中 | Try/Confirm/Cancel 三个接口 |
| **本地消息表** | 最终一致 | 中 | 高 | 最常用，订单系统标配 |
| **可靠消息服务**（最大努力通知） | 弱一致 | 中 | 高 | 跨平台通知 |
| **RocketMQ 事务消息** | 最终一致 | 低 | 高 | 阿里系标配 |

**本地消息表**流程（最经典）：
1. 业务表和消息表在**同一个 DB 同一事务**写入（强一致）
2. 后台定时任务轮询消息表，投递到 MQ
3. 投递成功后标记消息已发送
4. 消费端幂等处理

**RocketMQ 事务消息**（两阶段）：
1. Producer 发送「半消息」到 Broker，Broker 写入但对 Consumer 不可见
2. 执行本地事务（订单入库）
3. 根据本地事务结果发 `commit` 或 `rollback`
4. Broker 端有「事务回查」机制：如果长时间没收到二次确认，主动回查 Producer 状态

---

### 9. RocketMQ 详解

**架构**：

```
Producer ──> NameServer (集群，无状态) ──> Broker (Master + Slave)
                                                  │
                                              Consumer
```

- **NameServer**：轻量级注册中心，**无状态互不通信**，Broker 注册路由信息，Producer/Consumer 任意拉取。比 ZooKeeper 简单太多。
- **Broker**：消息存储和转发，**主从架构**（Dledger 0.9+ 支持自动选主）。
- **CommitLog**：Broker 上**所有 Topic 顺序写一个文件**，是 RocketMQ 高吞吐的关键。
- **ConsumeQueue**：每个 Queue 一个索引文件，记录消息在 CommitLog 中的 offset、大小、tag 哈希。**消费先读 ConsumeQueue，再去 CommitLog 拿消息**。
- **IndexFile**：可选索引，支持按 key 查消息。

**核心特性**：
- **严格顺序消息**：RocketMQ 顺序消息是真正的**全局顺序**（单队列），不依赖业务 key。代价是单队列单消费者。
- **事务消息**：见上文，两阶段 + 事务回查。
- **延迟消息**：内置 18 个固定延迟级别（1s 2s 5s 10s 30s 1m ... 2h），开箱即用，不用插件。
- **消息过滤**：Tag 过滤（`MessageSelector.byTag`）和 SQL92 过滤（`bySql`，自定义属性）。

> RocketMQ 几乎是为「订单/支付/金融」场景量身定做的：严格顺序、事务消息、延迟消息、消息回溯一应俱全。

---

## 代码示例

### 示例 1：RabbitMQ 生产者 + 消费者

```java
// Producer
public class RabbitProducer {
    public static void main(String[] args) throws Exception {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("127.0.0.1");
        factory.setUsername("guest");
        factory.setPassword("guest");

        try (Connection conn = factory.newConnection();
             Channel channel = conn.createChannel()) {

            channel.exchangeDeclare("order.exchange", "topic", true);
            channel.queueDeclare("order.queue", true, false, false, null);
            channel.queueBind("order.queue", "order.exchange", "order.#");

            // 开启 confirm 模式，确保消息到达 Broker
            channel.confirmSelect();

            String msg = "{\"orderId\":123,\"amount\":99.9}";
            AMQP.BasicProperties props = new AMQP.BasicProperties.Builder()
                    .deliveryMode(2) // 持久化
                    .contentType("application/json")
                    .messageId(UUID.randomUUID().toString()) // 用于消费端幂等
                    .build();

            channel.basicPublish("order.exchange", "order.created", props, msg.getBytes());
            boolean ack = channel.waitForConfirms(5000);
            System.out.println("发送结果: " + (ack ? "成功" : "失败"));
        }
    }
}

// Consumer
public class RabbitConsumer {
    public static void main(String[] args) throws Exception {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("127.0.0.1");
        try (Connection conn = factory.newConnection();
             Channel channel = conn.createChannel()) {

            channel.basicQos(10); // 预取 10 条

            DeliverCallback callback = (tag, delivery) -> {
                String msg = new String(delivery.getBody(), StandardCharsets.UTF_8);
                String msgId = delivery.getProperties().getMessageId();
                try {
                    handleOrder(msgId, msg); // 业务处理，内部做幂等
                    channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
                } catch (Exception e) {
                    // 失败 nack 走死信，不在队列里无限重试
                    channel.basicNack(delivery.getEnvelope().getDeliveryTag(), false, false);
                }
            };
            channel.basicConsume("order.queue", false, callback, tag -> {});
        }
    }
}
```

### 示例 2：Kafka 生产者 + 消费者（带幂等和 ack=all）

```java
// Producer
Properties props = new Properties();
props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
props.put(ProducerConfig.ACKS_CONFIG, "all");                          // 强可靠
props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);            // 幂等
props.put(ProducerConfig.RETRIES_CONFIG, Integer.MAX_VALUE);           // 无限重试
props.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);   // 幂等下可>1

KafkaProducer<String, String> producer = new KafkaProducer<>(props);

// 相同 key 路由到同一分区，保证顺序
for (int i = 0; i < 100; i++) {
    ProducerRecord<String, String> record = new ProducerRecord<>(
            "order-topic", "user_" + (i % 10), "{\"orderId\":" + i + "}");
    producer.send(record, (meta, ex) -> {
        if (ex != null) log.error("发送失败", ex);
    });
}
producer.flush();
producer.close();
```

```java
// Consumer
Properties props = new Properties();
props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
props.put(ConsumerConfig.GROUP_ID_CONFIG, "order-service");
props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);             // 手动提交
props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 100);                 // 一次拉 100 条

KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props);
consumer.subscribe(Collections.singletonList("order-topic"));

while (true) {
    ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));
    for (ConsumerRecord<String, String> record : records) {
        try {
            // 幂等处理：messageId = topic + partition + offset
            String msgId = record.topic() + "-" + record.partition() + "-" + record.offset();
            handleOrder(msgId, record.value());
        } catch (Exception e) {
            log.error("处理失败 offset={}", record.offset(), e);
            // 不提交，下次重试
        }
    }
    consumer.commitSync(); // 处理完一批再提交
}
```

### 示例 3：基于数据库唯一索引的幂等消费

```java
@Service
public class IdempotentOrderConsumer {

    @Autowired private JdbcTemplate jdbc;

    @Transactional
    public void handle(String msgId, String payload) {
        // 关键：用 msgId 做唯一索引，INSERT 失败 = 重复消息
        int rows = jdbc.update(
            "INSERT INTO message_processed(message_id, processed_at) VALUES(?, NOW())",
            msgId);
        if (rows == 0) {
            log.info("重复消息，跳过 msgId={}", msgId);
            return;
        }
        // 真正业务逻辑
        Order order = parseOrder(payload);
        orderService.createOrder(order);
    }
}
```

### 示例 4：RocketMQ 事务消息

```java
public class TxOrderProducer {
    public static void main(String[] args) throws Exception {
        TransactionMQProducer producer = new TransactionMQProducer("order_producer_group");
        producer.setNamesrvAddr("127.0.0.1:9876");
        // 本地事务执行器
        producer.setTransactionListener(new TransactionListener() {
            @Override
            public LocalTransactionState executeLocalTransaction(Message msg, Object arg) {
                try {
                    // 1. 执行本地事务（订单入库）
                    orderService.createOrder(arg);
                    return LocalTransactionState.COMMIT_MESSAGE;
                } catch (Exception e) {
                    return LocalTransactionState.ROLLBACK_MESSAGE;
                }
            }
            @Override
            public LocalTransactionState checkLocalTransaction(MessageExt msg) {
                // 2. Broker 事务回查：根据订单表状态判断
                return orderService.exists(msg.getKeys())
                        ? LocalTransactionState.COMMIT_MESSAGE
                        : LocalTransactionState.ROLLBACK_MESSAGE;
            }
        });
        producer.start();
        // 发送半消息
        Message msg = new Message("order-topic", "create", "ORDER_001",
                "{\"orderId\":1}".getBytes());
        producer.sendMessageInTransaction(msg, order);
    }
}
```

---

## 易错点 / 最佳实践

1. **不要把 MQ 当数据库用**。MQ 设计目标是「瞬时存储 + 高吞吐传递」，不是「长期持久化」。消息消费完要么 ack 要么备份到 DB，**永远不要在 MQ 里堆几个月的数据**。

2. **消费失败一定要重试，不能直接丢弃**。RabbitMQ 用 `nack(requeue=true)` 或死信队列兜底；Kafka 关闭自动 commit 失败不提交；RocketMQ 设置重试次数。**重试多次仍失败的进死信，人工介入**。

3. **幂等性是必修课，不是可选项**。至少一次语义必然带来重复，金融场景（支付、扣款）必须保证幂等。优先用「业务唯一 ID + 数据库唯一索引」，最简单可靠。

4. **顺序性不要全局追求**。全链路顺序在分布式系统里几乎不可能，**业务分区有序**（同一订单的多个消息有序）就够了。强求全局顺序会牺牲并发和可用性。

5. **监控消息积压**。Kafka 看 `consumer-lag`、RabbitMQ 看队列消息数。设告警阈值（比如 lag > 1 万），积压时先**扩容消费者**、再排查消费慢的原因，必要时**降级非核心业务**。

6. **优先保证「不丢」，再考虑「不重」**。可靠性是底线，幂等是补救。`acks=all` + 持久化 + 手动 commit 是生产环境标配。

---

## 面试常见问题

**Q1: Kafka、RabbitMQ、RocketMQ 怎么选？**
A: 日志/流计算/大数据选 Kafka（吞吐之王）；业务解耦/复杂路由/老牌企业选 RabbitMQ（AMQP 协议生态成熟）；订单/交易/金融/需要事务消息选 RocketMQ（阿里双 11 验证）。性能上 Kafka > RocketMQ > RabbitMQ，可靠性和事务能力上 RocketMQ > RabbitMQ > Kafka。

**Q2: 如何保证消息不丢失？**
A: 三端协同。① 生产端：`acks=all`（Kafka）/ `confirm` 模式（RabbitMQ）/ 事务消息（RocketMQ），失败重试；② Broker 端：消息持久化到磁盘、镜像集群 / 多副本 ISR、刷盘策略 `flush.disk`；③ 消费端：关闭自动 commit，处理成功才 ack，失败重试不 commit。**任何一环没做，都可能丢消息**。

**Q3: 如何保证消息不重复消费？**
A: 根本是**幂等设计**。常用方案：① 业务唯一 ID + 数据库唯一索引（最稳）；② Redis `SETNX` 做分布式锁（高性能场景）；③ 消费前查去重表；④ Kafka 开启 `enable.idempotence=true` 解决单分区单会话内的重复。面试时主动提「幂等性 + 业务 ID」，比单纯答「去重」得分高。

**Q4: 如何保证消息顺序？**
A: 顺序性的本质是「**单分区 + 单消费者 + 手动提交**」。Kafka 用相同业务 key 路由到同一 Partition；RabbitMQ 路由到同一 Queue 且只起一个消费者；RocketMQ 用「顺序消息」类型（单队列）。**代价**：单分区吞吐有上限，顺序和并发不可兼得，只能做到「业务维度有序」。

**Q5: Kafka 为什么这么快？**
A: 四大原因：① **顺序写磁盘**（HDD 顺序写 ≈ 内存随机写）；② **零拷贝**（`sendfile` 系统调用，内核态直接转发，少 2 次用户态切换）；③ **页缓存**（读走 OS Page Cache，不打磁盘）；④ **批量压缩** + **分区并行**（Producer 批量发送，Consumer 批量拉取）。单机百万 TPS 是常规水平。

**Q6: 消息积压了怎么办？**
A: 三步走：① **应急扩容**：增加 Topic 分区数 + 扩容消费者实例；② **降级非核心**：临时关闭非关键消费逻辑，专注核心链路；③ **根因排查**：是消费慢（SQL 慢、远程调用多）还是生产者突增。**不能直接清队列**——消息是数据，清了就是数据丢失。实在扛不住就把消息转储到新 Topic，慢慢消费。

**Q7: 什么是消息幂等性？有哪些实现方案？**
A: 消息幂等 = 同一条消息被消费 N 次和消费 1 次**业务结果一致**。MQ 的「至少一次」语义必然导致重复，所以消费端必须幂等。方案：① 数据库唯一索引（最稳）；② Redis SETNX（注意 TTL）；③ 状态机（订单从「已下单 → 已支付」单向推进，回退就报错）；④ 消费前查重表。

**Q8: 延迟队列怎么实现？**
A: 三种典型实现：① **RabbitMQ TTL + DLX**（消息设 TTL 过期 → 路由到死信队列 → 监听死信）；② **RabbitMQ 延迟插件** `rabbitmq-delayed-message-exchange`（推荐，更灵活）；③ **RocketMQ 内置**（18 个固定延迟级别，1s/5s/10s/30s/1m/.../2h）；④ **JDK DelayQueue**（单机场景，不推荐分布式）。**Redis ZSet** 也常被面试提到（score=到期时间戳，轮询处理）。

**Q9: RocketMQ 事务消息原理？**
A: 两阶段 + 事务回查。① Producer 发送**半消息**到 Broker，Broker 写入但 Consumer 不可见；② Producer 执行本地事务（订单入库）；③ 根据本地事务结果发 `commit` 或 `rollback`；④ Broker 端开启后台线程**回查**：长时间没收到二次确认的，回查 Producer 状态决定 commit/rollback。本质是**用消息中间件做分布式事务协调器**，实现最终一致性。

**Q10: Kafka 的 ISR 是什么？和 OS 副本有什么区别？**
A: ISR（In-Sync Replicas）是**与 Leader 保持同步的副本集合**。Follower 落后 Leader 超过 `replica.lag.time.max.ms`（默认 30s）会被踢出 ISR。**Leader 只在 ISR 中选举**，所以 ISR 包含的副本一定能选为 Leader，**保证数据不丢**。`acks=all` 写入时也只等 ISR 中所有副本同步完成。OS 副本（OSR）是落后太多的副本，Kafka 视情况会重启追平拉回 ISR。

---

## 延伸阅读

- **《Kafka 权威指南》**（Neha Narkhede 等著）— Kafka 官方背书，第一手资料，从架构到源码
- **《RabbitMQ 实战：高效部署分布式消息队列》**（Vaughn Vernon 著）— 企业级 RabbitMQ 进阶
- **Apache Kafka 官方文档**：[https://kafka.apache.org/documentation/](https://kafka.apache.org/documentation/) — 1.0+ 中文版，质量极高
- **RocketMQ 官方文档**：[https://rocketmq.apache.org/docs/](https://rocketmq.apache.org/docs/) — 中文文档完善，事务消息章节必看
- **极客时间《Kafka 核心技术与实战》**（胡夕）— 中文社区最佳 Kafka 课，面试前刷一遍效果显著
