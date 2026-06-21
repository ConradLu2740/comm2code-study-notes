# 03 - Java 集合框架

## 概述

Java 集合框架（Java Collections Framework, JCF）是 `java.util` 包下提供的一组**容器类**，用于存储和操作一组对象。核心是 **Collection**（单列集合）和 **Map**（双列键值对）两大根接口，下分 List/Set/Queue 三大线性容器和 Map 树形容器，加上线程安全的并发包实现，几乎覆盖了日常开发 90% 的数据存储场景。

**关键点速览**

| 接口 | 特点 | 主要实现 | 线程安全版本 |
| --- | --- | --- | --- |
| `List` | 有序、可重复、可按下标访问 | `ArrayList`、`LinkedList` | `CopyOnWriteArrayList` |
| `Set` | 无序、不可重复 | `HashSet`、`LinkedHashSet`、`TreeSet` | `CopyOnWriteArraySet` |
| `Queue` | 队列/堆，先进先出或按优先级 | `ArrayDeque`、`PriorityQueue`、`LinkedList` | `ArrayBlockingQueue`、`ConcurrentLinkedQueue` |
| `Map` | 键值对，键不可重复 | `HashMap`、`LinkedHashMap`、`TreeMap` | `ConcurrentHashMap`、`Hashtable` |

## 核心概念

### 1. 整体架构

**Collection vs Collections**：两个完全不同的东西。
- `Collection` 是**接口**，是 List/Set/Queue 的父接口。
- `Collections` 是**工具类**（类似 `Arrays`），提供 `sort`、`synchronizedList`、`unmodifiableList` 等静态方法。

**Iterable vs Iterator**：
- `Iterable` 是**可迭代的接口**，定义 `iterator()` 方法，所有 Collection 都实现它（JDK 8 还加了 `forEach` 和 `spliterator`）。
- `Iterator` 是**迭代器**，定义了 `hasNext()`、`next()`、`remove()` 三个方法，用于遍历集合。
- `ListIterator` 是 Iterator 的增强版，支持双向遍历和修改，专门给 List 用。

```java
// for-each 底层就是 Iterator
List<String> list = new ArrayList<>();
for (String s : list) {       // 编译后等价于 Iterator 循环
    System.out.println(s);
}
```

### 2. List 家族

#### ArrayList —— 动态数组，查询 O(1)，增删 O(n)

核心源码（JDK 17 为参考）：

```java
// 默认容量 10，底层是 Object[] 数组
transient Object[] elementData;
private int size;

// 扩容：旧容量 + 旧容量右移 1 位 = 1.5 倍
private Object[] grow(int minCapacity) {
    int oldCapacity = elementData.length;
    if (oldCapacity > 0 || elementData != DEFAULTCAPACITY_EMPTY_ELEMENTDATA) {
        int newCapacity = ArraysSupport.newLength(oldCapacity,
            minCapacity - oldCapacity,        // 最小需要增长多少
            oldCapacity >> 1);                // 偏好增长：旧的 1/2
        return elementData = Arrays.copyOf(elementData, newCapacity);
    }
    return elementData = new Object[Math.max(10, minCapacity)];
}
```

**扩容规则**：新容量 = `oldCapacity + (oldCapacity >> 1)`，即 **1.5 倍**。如果还不够，则直接扩到需要的容量（`minCapacity`）。`ArrayList(int initialCapacity)` 可以在构造时指定初始容量，避免反复扩容。

**modCount 与 fail-fast**：每次结构性修改（add/remove/clear）会让 `modCount++`。迭代器初始化时记录 `expectedModCount = modCount`，每次 `next()/remove()` 时检查，不一致就抛 `ConcurrentModificationException`。这是**快速失败**机制，只检测不修复。

```java
Iterator<String> it = list.iterator();
while (it.hasNext()) {
    String s = it.next();
    if ("x".equals(s)) {
        list.remove(s);   // ❌ 抛 ConcurrentModificationException
    }
    it.remove();          // ✅ 迭代器自己的 remove 会更新 expectedModCount
}
```

#### LinkedList —— 双向链表，增删 O(1)，查询 O(n)

实现了 `List` + `Deque` + `Queue` 三个接口，底层是 `Node<E>` 双向链表：

```java
private static class Node<E> {
    E item;
    Node<E> next;
    Node<E> prev;
}
```

**注意**：LinkedList 的 `get(i)` 是从头部/尾部**就近遍历**，所以按 index 取值的平均复杂度是 O(n/2)，仍是 O(n)。很多人误以为是 O(1)，**这是高频面试坑**。

#### Vector —— 已过时，不要用

所有方法都用 `synchronized` 修饰，**全表锁**性能差。要线程安全用 `CopyOnWriteArrayList` 或 `Collections.synchronizedList(new ArrayList<>())`。

#### CopyOnWriteArrayList —— 读写分离，写时复制

**写操作**（add/set/remove）：先 `Arrays.copyOf` 复制一份新数组，修改后再把引用指向新数组，期间读操作读的是旧数组，**完全无锁**。

**读操作**：无锁直接返回，**适合读多写少**的场景（如配置、黑名单）。

**缺点**：写操作内存占用 2 倍，不适合大对象；数据不保证实时一致，只能保证最终一致。

### 3. Set 家族

#### HashSet —— 就是 HashMap 的马甲

```java
public HashSet() {
    map = new HashMap<>();
}
// add 实际就是 map.put(e, PRESENT)，PRESENT 是一个空 Object
private static final Object PRESENT = new Object();
```

**特点**：
- 元素唯一性靠 `hashCode` + `equals` 保证
- **无序**（不保证插入顺序）
- 允许 `null` 元素
- 增删查平均 O(1)

#### LinkedHashSet —— HashSet + 双向链表

继承 HashSet，底层用 LinkedHashMap 实现，**保留插入顺序**（默认）或访问顺序。性能略低于 HashSet。

#### TreeSet —— 红黑树，自动排序

基于 `TreeMap` 实现，元素实现 `Comparable` 或构造时传 `Comparator`。**有序、唯一**，但增删查 O(log n)，不支持 `null`。

### 4. Queue 家族

#### PriorityQueue —— 堆（默认小顶堆）

底层是**二叉堆**（数组实现），`offer` 和 `poll` 都是 O(log n)，`peek` 是 O(1)。**注意**：它**不保证元素的有序性**，只保证堆顶是最小（或最大）元素。常用于 Top K、合并有序流等场景。

```java
PriorityQueue<Integer> minHeap = new PriorityQueue<>();  // 小顶堆
PriorityQueue<Integer> maxHeap = new PriorityQueue<>((a, b) -> b - a);  // 大顶堆
```

#### ArrayDeque —— 双端队列，栈和队列的首选

底层是**循环数组**，比 `LinkedList` 更快（无节点对象开销，且 CPU 缓存友好），可以同时当**栈**和**队列**用。**注意**：不允许 `null` 元素。

```java
ArrayDeque<String> stack = new ArrayDeque<>();
stack.push("a");      // 入栈
stack.push("b");
stack.pop();          // 出栈 → "b"

ArrayDeque<String> queue = new ArrayDeque<>();
queue.offer("a");
queue.poll();         // 队列头出队
```

**面试题**：实现一个栈用什么？**优先用 `ArrayDeque`**，不要用 `Stack`（Vector 的子类，已过时且全表锁）。

### 5. Map 家族（重点中的重点）

#### HashMap 源码 —— 面试必背

**底层结构**（JDK 8+）：**数组 + 链表 + 红黑树**。

```java
transient Node<K,V>[] table;     // 桶数组
static class Node<K,V> implements Map.Entry<K,V> { ... }
static final class TreeNode<K,V> extends LinkedHashMap.Entry<K,V> { ... }
```

**关键字段**：
- `DEFAULT_INITIAL_CAPACITY = 16`：默认容量
- `DEFAULT_LOAD_FACTOR = 0.75f`：默认负载因子
- `TREEIFY_THRESHOLD = 8`：链表长度 ≥ 8 时考虑转红黑树
- `UNTREEIFY_THRESHOLD = 6`：红黑树节点 ≤ 6 时退化为链表
- `MIN_TREEIFY_CAPACITY = 64`：桶数组容量 ≥ 64 时才允许树化

**负载因子 0.75** 是**空间和时间的折中**：太小浪费空间，太大哈希冲突多，0.75 是泊松分布下冲突概率的较优点。

**put 流程**（必背）：

1. 调 `hash(key)`：`(h = key.hashCode()) ^ (h >>> 16)`，高低位异或减少冲突。
2. 如果 table 为空，先 `resize()` 初始化。
3. 根据 `(n - 1) & hash` 定位桶下标。
4. 桶为空：直接放新 Node。
5. 桶不为空：
   - key 相同（`==` 或 `equals`）：覆盖 value。
   - 是 TreeNode：调红黑树插入。
   - 是普通链表：`尾插法`（JDK 7 是头插，多线程下会形成死循环；JDK 8 已修复），遍历到尾部插入，**插入后判断链表长度 ≥ 8 且 table.length ≥ 64 → 树化**。
6. `++size > threshold` → `resize()`。

**扩容机制**：容量翻倍，**2 倍**。新下标要么是原下标，要么是 `原下标 + 旧容量`（因为长度是 2 的幂，可以用 `hash & oldCap` 一次性判断）。这也是为什么**容量必须设计成 2 的幂**——`&` 运算比 `%` 快，且扩容时数据迁移更高效。

```java
final Node<K,V>[] resize() {
    Node<K,V>[] oldTab = table;
    int oldCap = (oldTab == null) ? 0 : oldTab.length;
    int oldThr = threshold;
    int newCap, newThr = 0;
    if (oldCap > 0) {
        if (oldCap >= MAXIMUM_CAPACITY) { threshold = Integer.MAX_VALUE; return oldTab; }
        else if ((newCap = oldCap << 1) < MAXIMUM_CAPACITY && oldCap >= 16)
            newThr = oldThr << 1;   // 阈值也翻倍
    }
    // ... 省略 newTab 创建
    table = newTab;
    // 重新哈希：遍历 oldTab，把每个桶拆到 loHead / hiHead
}
```

**为什么重写 equals 必须重写 hashCode？**
HashMap 先用 `hashCode` 定位桶，再用 `equals` 在桶内查找 key。如果两个对象 `equals` 但 `hashCode` 不同，它们会被分到不同桶，**永远找不到彼此**，导致重复插入或丢失数据。

#### LinkedHashMap —— 继承 HashMap，加双向链表

内部类 `Entry` 比 HashMap 多 `before` 和 `after` 指针，可以维护**插入顺序**或**访问顺序**（构造时 `accessOrder=true`）。

**经典面试题：手写 LRU 缓存**就是继承 LinkedHashMap，重写 `removeEldestEntry`：

```java
class LRUCache<K, V> extends LinkedHashMap<K, V> {
    private final int maxSize;
    public LRUCache(int maxSize) {
        super(16, 0.75f, true);  // accessOrder = true
        this.maxSize = maxSize;
    }
    @Override
    protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
        return size() > maxSize;
    }
}
```

#### TreeMap —— 红黑树，按 key 排序

key 必须实现 `Comparable` 或传 `Comparator`，增删查 O(log n)，支持范围查询：`subMap`、`headMap`、`tailMap`。

#### Hashtable —— 已过时

和 Vector 一样，全表 `synchronized` 锁，性能差；key 和 value **都不允许 null**。要线程安全的 Map 用 `ConcurrentHashMap`。

### 6. ConcurrentHashMap（并发编程必考）

#### JDK 1.7：分段锁 Segment

底层是 `Segment[]`，每个 Segment 继承 `ReentrantLock`，相当于把数据分成 N 段，每段独立加锁。**最大并发度 = Segment 数量，默认 16**。

缺点：Segment 数量在初始化就定好，扩容不灵活；查询要两次哈希。

#### JDK 1.8：CAS + synchronized + 红黑树

弃用 Segment，改为 **Node[] 数组 + 链表 + 红黑树**，粒度细化到**每个桶的头节点**。

**put 流程**：

1. table 为空 → CAS 初始化。
2. 桶为空 → CAS 插入新 Node，失败则自旋重试。
3. 桶不为空 → `synchronized (头节点)`，然后按链表/红黑树插入。
4. 链表长度 ≥ 8 且 table ≥ 64 → 树化。
5. `addCount` 用 CAS 更新 baseCount。

**get 完全无锁**，因为 Node 的 `val` 和 `next` 都用 `volatile` 修饰，保证可见性。

**JDK 1.7 vs 1.8 对比**

| 维度 | JDK 1.7 | JDK 1.8+ |
| --- | --- | --- |
| 数据结构 | Segment + HashEntry[] + 链表 | Node[] + 链表 + 红黑树 |
| 锁机制 | Segment 分段锁（ReentrantLock） | 桶头节点锁（synchronized）+ CAS |
| 并发度 | Segment 数量（默认 16） | 桶数量（默认 16，扩容可增长） |
| 树化 | 不支持 | 链表 ≥ 8 且 table ≥ 64 |
| 哈希冲突查询 | O(n) | O(log n) |

### 7. 工具类

#### Collections

- `synchronizedList(List<T>)`：返回一个所有方法都加 `synchronized` 的包装类，**适合读多写少**。
- `synchronizedMap(Map)`：同上。
- `unmodifiableList/Map`：返回只读视图，对原集合修改会影响视图，反之抛 `UnsupportedOperationException`。
- `emptyList()` / `emptyMap()`：返回空集合常量，避免返回 `null`。

#### Arrays

- `asList(T... a)`：**注意！** 返回的是 `Arrays.ArrayList`（不是 `java.util.ArrayList`），**定长**，不能 add/remove，但可以 set。`Arrays.asList(1,2,3).add(4)` 会抛异常。
- `sort(int[] a)`：Dual-Pivot Quicksort，O(n log n)，对基本类型用快排，对对象用 TimSort。
- `binarySearch`、`equals`、`copyOf`、`toString` 都是常用方法。

### 8. fail-fast vs fail-safe

| 维度 | fail-fast（快速失败） | fail-safe（安全失败） |
| --- | --- | --- |
| 代表 | `ArrayList`、`HashMap`、`HashSet` | `CopyOnWriteArrayList`、`ConcurrentHashMap` |
| 原理 | 迭代时检查 modCount | 迭代时操作副本 |
| 触发 | 迭代过程中修改集合 | 不会抛异常 |
| 性能 | 高 | 写慢（需复制） |
| 实时性 | 看到的是最新数据 | 不保证实时一致 |

## 代码示例

### 示例 1：HashMap 优雅初始化，避免扩容

```java
// ❌ 不知道要存多少个，扩容会触发 N 次
Map<String, Integer> map1 = new HashMap<>();

// ✅ 预估容量 = ceil(预期元素数 / 0.75)，避免反复 resize
int expected = 1000;
int capacity = (int) (expected / 0.75f) + 1;
Map<String, Integer> map2 = new HashMap<>(capacity);
```

### 示例 2：Top K Frequent（PriorityQueue 实战）

```java
public int[] topKFrequent(int[] nums, int k) {
    Map<Integer, Integer> freq = new HashMap<>();
    for (int n : nums) freq.merge(n, 1, Integer::sum);

    // 小顶堆保留 top k，堆顶是最小的
    PriorityQueue<Map.Entry<Integer, Integer>> heap =
        new PriorityQueue<>(Comparator.comparingInt(Map.Entry::getValue));

    for (Map.Entry<Integer, Integer> e : freq.entrySet()) {
        heap.offer(e);
        if (heap.size() > k) heap.poll();
    }

    return heap.stream().mapToInt(Map.Entry::getKey).toArray();
}
```

### 示例 3：ConcurrentHashMap 原子操作

```java
ConcurrentHashMap<String, AtomicInteger> map = new ConcurrentHashMap<>();
// 复合操作要用方法，别自己 if-else + put，多线程下不安全
map.computeIfAbsent("counter", k -> new AtomicInteger(0)).incrementAndGet();
map.merge("counter2", 1, Integer::sum);  // 累加
```

### 示例 4：LinkedHashMap 实现 LRU

```java
// 容量为 3 的 LRU
LRUCache<String, String> cache = new LRUCache<>(3);
cache.put("a", "1"); cache.put("b", "2"); cache.put("c", "3");
cache.get("a");   // 访问 a，a 变成最新
cache.put("d", "4");  // 插入新元素，淘汰最久未使用的 b
System.out.println(cache);  // {c=3, a=1, d=4}
```

## 易错点 / 最佳实践

1. **指定初始容量**：HashMap/ArrayList 如果能预估大小，构造时直接传 `new HashMap<>(expectedSize / 0.75f + 1)`，避免反复扩容。LinkedHashMap/HashSet 同理。

2. **不要在 foreach 循环里修改集合**：`for (String s : list) { list.remove(s); }` 会抛 `ConcurrentModificationException`。要么用 `Iterator.remove()`，要么 `removeIf(Predicate)`（JDK 8+）。

3. **`Arrays.asList` 不能 add/remove**：它返回的是定长 `Arrays$ArrayList`，修改会抛 `UnsupportedOperationException`。要可变长用 `new ArrayList<>(Arrays.asList(...))`。

4. **多线程场景下集合的选型**：
   - 读多写少：List 用 `CopyOnWriteArrayList`，Map 用 `ConcurrentHashMap`。
   - 写多读多：考虑 `Collections.synchronizedXxx`，但要手动加锁或用并发包专用类（如 `ConcurrentLinkedQueue`、`ConcurrentSkipListMap`）。
   - **永远不要用 `HashMap` + 自己加锁当并发 Map**。

5. **equals 和 hashCode 必须一起重写**：自定义 key 放进 HashMap/HashSet 时尤其重要。可以让 IDE 自动生成，或用 `Objects.hash(...)` 简化。

6. **不要用 `Vector`、`Stack`、`Hashtable`**：都是 JDK 1.0 的遗留类，全表锁、性能差，新代码应该用 `ArrayList`、`ArrayDeque`、`ConcurrentHashMap` 替代。

## 面试常见问题

**Q1: ArrayList 扩容为什么是 1.5 倍而不是 2 倍？**
A: 1.5 倍是空间和性能的折中。2 倍扩容快但浪费内存（最坏 50% 浪费），1.5 倍扩容慢一点但内存利用率更高。1.5 = 1 + 1/2，扩容次数 log_{1.5}(n)，2 倍是 log_2(n)，差距不大。

**Q2: HashMap 为什么容量必须是 2 的幂？**
A: 两个原因：(1) 用 `(n-1) & hash` 代替 `%` 取模，位运算更快；(2) 扩容时数据迁移只需判断 `hash & oldCap` 是 0 还是 1，就能确定节点在新表中的下标是原下标还是 `原下标+oldCap`，避免重新计算哈希。

**Q3: HashMap 在 JDK 1.7 和 1.8 的核心区别？**
A: (1) 1.7 头插法 + 链表，1.8 尾插法 + 链表+红黑树；(2) 1.7 多线程下头插会导致死循环和数据丢失，1.8 修复了死循环但仍不是线程安全的；(3) 1.8 链表长度 ≥ 8 且 table ≥ 64 转红黑树，将最坏情况从 O(n) 降到 O(log n)。

**Q4: ConcurrentHashMap 1.7 和 1.8 怎么保证线程安全？**
A: 1.7 用 `Segment` 分段锁，锁粒度是段（默认 16 段），最大并发 16。1.8 弃用 Segment，改为 Node + CAS + synchronized 锁桶头节点，锁粒度更细，且红黑树优化了查询性能。读操作两者都无锁。

**Q5: 为什么重写 equals 必须要重写 hashCode？**
A: HashMap/HashSet 先用 hashCode 定位桶，再用 equals 在桶内找元素。如果两个对象 equals 但 hashCode 不同，它们会被分到不同桶，equals 永远不会被调用，导致"重复"插入或找不到已有 key。违反 `equals 相等 ⇒ hashCode 相等` 的约定。

**Q6: fail-fast 和 fail-safe 是什么？**
A: fail-fast（如 ArrayList、HashMap）在迭代过程中检测到集合被修改就立刻抛 `ConcurrentModificationException`，通过 modCount 实现。fail-safe（如 CopyOnWriteArrayList）迭代时操作的是集合的副本，修改不会影响遍历，但读不到最新数据。

**Q7: CopyOnWriteArrayList 适用什么场景？有什么缺点？**
A: 适合**读远多于写**的场景，比如配置、白名单、黑名单、监听器列表。优点是读无锁、性能高。缺点：(1) 写时复制数组，内存占用 2 倍；(2) 写操作开销大；(3) 数据弱一致，遍历时看不到最新数据。

**Q8: LinkedHashMap 怎么实现 LRU？**
A: 构造时设 `accessOrder = true`，每次 `get/put` 被访问的节点会被移到链表尾部，`removeEldestEntry` 在插入后判断并返回 true 时，删除链表头节点（最久未使用）。继承 LinkedHashMap 重写这个方法即可实现 LRU。

**Q9: HashMap 的负载因子为什么是 0.75？**
A: 是空间和查询性能的折中。负载因子越小，冲突越少但空间浪费越多；越大，冲突越多查询越慢。0.75 配合 2 的幂容量，根据泊松分布，桶中元素超过 8 的概率不到千万分之一，因此 `TREEIFY_THRESHOLD=8` 是合理的。

**Q10: PriorityQueue 是用什么数据结构实现的？插入删除复杂度？**
A: 用**二叉堆**实现（数组存层序遍历），不是红黑树。`offer` 和 `poll` 都是 O(log n)，`peek` 是 O(1)。默认是小顶堆，传 `Comparator` 可改为大顶堆。注意：它**不保证整体有序**，只保证堆顶是极值，遍历顺序不保证有序。

## 延伸阅读

- **官方文档**：[The Collections Framework (Oracle Java Tutorials)](https://docs.oracle.com/javase/tutorial/collections/) —— 集合框架的官方入门，最权威。
- **源码**：[OpenJDK 17 java.util](https://github.com/openjdk/jdk/tree/master/src/java.base/share/classes/java/util) —— 直接看 HashMap、ConcurrentHashMap 源码，比任何博客都准。
- **书籍**：《Java 核心技术 卷 I》第 9 章、《Effective Java》第 3 章（关于 equals/hashCode 的契约必须看）。
- **视频**：黑马程序员 / 尚硅谷的 Java 集合框架源码课（B 站免费），重点看 HashMap 和 ConcurrentHashMap 的源码讲解。
