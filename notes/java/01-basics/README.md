# 01 - Java 基础语法

## 概述

Java 基础语法是构建 Java 程序的基石，涵盖**类型系统**、**字符串体系**、**控制流**、**数组**、**异常**、**常用工具类**与**传参机制**。这一模块是面试必问的「八股文」基础，几乎每场 Java 后端面试都会涉及，重要性极高。

**关键点速览表：**

| 主题 | 核心要点 |
|---|---|
| 数据类型 | 8 种基本类型 + 对应包装类、自动装箱/拆箱、**IntegerCache** 缓存池 |
| String | 不可变性、字符串常量池（String Pool）、StringBuilder/StringBuffer 区别 |
| 控制流 | if/for/while/switch、Java 14+ **switch 表达式**新特性 |
| 数组 | 定长、**varargs** 可变参数、二维数组（数组的数组） |
| 异常 | Throwable 体系、Checked/Unchecked、**try-with-resources** |
| 常用类 | Object（equals/hashCode/toString）、Math、Arrays、Collections |
| 传参 | **值传递**（Java 只有值传递）、深浅拷贝 |

---

## 核心概念

### 一、八大基本数据类型与包装类

**8 种基本类型速查：**

| 类型 | 大小 | 包装类 | 默认值 | 范围 |
|---|---|---|---|---|
| byte | 1 字节 | Byte | 0 | -128 ~ 127 |
| short | 2 字节 | Short | 0 | -32768 ~ 32767 |
| int | 4 字节 | Integer | 0 | -2^31 ~ 2^31-1 |
| long | 8 字节 | Long | 0L | -2^63 ~ 2^63-1 |
| float | 4 字节 | Float | 0.0f | IEEE 754 单精度 |
| double | 8 字节 | Double | 0.0d | IEEE 754 双精度 |
| char | 2 字节 | Character | '\u0000' | 0 ~ 65535（Unicode） |
| boolean | 1 字节（JVM 实际按 int 处理） | Boolean | false | true / false |

**自动装箱 / 拆箱**（JDK 5+ 引入）：

- **装箱**：基本类型 → 包装类。`Integer i = 10;` 编译器自动调用 `Integer.valueOf(10)`
- **拆箱**：包装类 → 基本类型。`int n = i;` 编译器自动调用 `i.intValue()`
- 频繁装拆箱会带来**堆分配 + GC 压力**，在热路径上应避免（如 LongAdder 之于 AtomicLong 的部分场景）

**IntegerCache 缓存池**（重点）：

- `Integer.valueOf(int)` 内部使用了 `IntegerCache`，**默认缓存范围 -128 ~ 127**
- 命中缓存范围时直接返回缓存数组中的对象；超出范围才 `new Integer(i)`
- 缓存上限可通过 JVM 参数 `-XX:AutoBoxCacheMax=<upper>` 调整，最大不超过 `Integer.MAX_VALUE - 129`
- **面试经典坑**：`Integer a = 127; Integer b = 127; a == b` → `true`；`Integer a = 128; Integer b = 128; a == b` → `false`
- 类似缓存机制：`Byte/Short/Long` 全部缓存，`Character` 缓存 0~127，`Boolean` 只有 TRUE/FALSE 两个实例，`Float/Double` **没有**缓存

### 二、String / StringBuilder / StringBuffer

**String 的不可变性**：

- `String` 类被 `final` 修饰，无法继承
- 内部 `value` 字段也被 `final` 修饰：JDK 8 是 `char[]`，JDK 9+ 改为 `byte[] + byte coder`（Latin-1 编码节省内存，因为绝大多数字符串是 ASCII）
- 一旦创建不可修改，任何修改都会生成新对象
- 不可变的四个核心收益：**常量池**、**安全**（HashMap key、URL、文件路径）、**线程安全**、**哈希缓存**（`hash` 字段懒加载）

**字符串常量池（String Pool）**：

- 位置变迁：JDK 7 之前在方法区（PermGen），**JDK 7+ 移到了堆中**（因为 PermGen 太小，且 GC 频率低）
- 两种创建方式对比：
  - 字面量 `String s = "hello"`：先去常量池找，没有则创建并放入池中
  - `new String("hello")`：**一定**在堆中创建新对象（不会自动入池）
- `intern()` 方法：把字符串放入常量池并返回池中的引用（首次调用时入池，后续直接返回已有引用）

**StringBuilder vs StringBuffer：**

| 特性 | StringBuilder | StringBuffer |
|---|---|---|
| 线程安全 | 否 | 是（方法 `synchronized`） |
| 性能 | 高 | 低（锁开销） |
| 引入版本 | JDK 1.5+ | JDK 1.0+ |
| 底层（JDK 9+） | `byte[]` + `coder` | `byte[]` + `coder` |
| 使用场景 | 单线程字符串拼接 | 多线程共享 StringBuffer |

> 单线程场景永远优先选 **StringBuilder**；多线程共享 StringBuilder 需外部加锁或换 StringBuffer。

### 三、控制流

**Java 14+ switch 表达式**（正式特性）：

```java
// 传统写法：易漏 break 导致 fall-through
String day;
switch (num) {
    case 1: day = "Mon"; break;
    case 2: day = "Tue"; break;
    default: day = "Sun";
}

// 新写法：箭头语法自动 break
String day = switch (num) {
    case 1 -> "Mon";
    case 2 -> "Tue";
    default -> "Sun";
};

// 多值匹配
String level = switch (score / 10) {
    case 9, 10 -> "A";
    case 7, 8  -> "B";
    case 6     -> "C";
    default    -> "D";
};
```

**增强 for 循环（for-each）**：

- 只能用于**数组**或实现 **`Iterable`** 接口的集合
- 内部使用 `Iterator`，因此**不能在循环中通过集合自身的 `add/remove` 修改元素**（会抛 `ConcurrentModificationException`），需用迭代器的 `remove()` 或改用普通 for 循环

### 四、数组与可变参数

**数组核心特性**：

- **定长**：初始化后长度不可变，需变长只能用 `ArrayList` 等集合
- 数组是**对象**，继承自 `Object`，数组长度是**属性** `arr.length`（注意 String 的 `length()` 是**方法**）
- 多维数组本质是「数组的数组」，每行长度可以不一致：`int[][] arr = new int[3][]; arr[0] = new int[5];`

**可变参数 varargs**（JDK 5+）：

```java
public static int sum(int... nums) {
    int total = 0;
    for (int n : nums) total += n;
    return total;
}
sum();        // OK，nums 为空数组
sum(1, 2, 3); // OK
sum(new int[]{1, 2}); // OK，本质就是数组
```

- 本质是**数组**，编译器把可变参数打包成 `int[] nums`
- 必须是方法的**最后一个参数**，一个方法只能有一个 varargs
- 注意重载歧义：`m(List<String>...)` 与 `m(String...)` 在某些调用下不明确

### 五、异常体系

**异常类层次结构：**

```
Throwable
├── Error（错误，不可恢复，不应捕获）
│   ├── OutOfMemoryError
│   ├── StackOverflowError
│   └── ...
└── Exception（异常，可捕获处理）
    ├── IOException / SQLException（受检异常 Checked）
    └── RuntimeException（非受检异常 Unchecked）
        ├── NullPointerException
        ├── ArrayIndexOutOfBoundsException
        ├── ClassCastException
        ├── IllegalArgumentException
        └── NumberFormatException
```

**Checked vs Unchecked**：

- **Checked（受检异常）**：编译期强制处理（try-catch 或 throws），如 `IOException`、`SQLException`
- **Unchecked（非受检异常）**：均为 `RuntimeException` 子类，编译期不强制处理，如 NPE、数组越界

**try-catch-finally 要点**：

- `finally` 块**大多数情况下**会执行（特殊例外：`System.exit()`、JVM 崩溃、守护线程死亡）
- `finally` 中 `return` 会**覆盖** `try` 中的 `return`
- 不要在 `finally` 里 `return` 或抛异常，会吞掉 try 块中的异常
- JDK 7+ 用 `Throwable.addSuppressed` 保留被 finally 吞掉的异常

**try-with-resources**（JDK 7+，强烈推荐）：

```java
try (BufferedReader br = new BufferedReader(new FileReader("a.txt"));
     BufferedWriter bw = new BufferedWriter(new FileWriter("b.txt"))) {
    // 使用资源
} catch (IOException e) {
    log.error("read failed", e);
}
```

- 自动调用 `close()`，无论是否抛异常
- 资源类必须实现 **`AutoCloseable`**（JDK 7+）或 `Closeable` 接口
- JDK 9+ 可以在外部声明 `final` 变量引用，代码更简洁

**throws vs throw**：

- `throws`：声明方法**可能**抛出的异常，写在方法签名上（`public void m() throws IOException`）
- `throw`：手动**抛出**异常对象（`throw new IllegalArgumentException("param invalid");`）

### 六、Object 类核心方法

**equals & hashCode 契约**：

1. `equals` 相等 → `hashCode` 必须相等（反之不一定）
2. `equals` 必须满足**自反性、对称性、传递性、一致性、非空性**
3. **重写 equals 必须重写 hashCode**（HashMap、HashSet 依赖）
4. 重写建议用 IDE 生成或 `Objects.hash(field1, field2)`

**其他方法**：

- `toString()`：默认返回 `类名@hash16进制`，强烈建议重写为有意义的字符串
- `getClass()`：`final` 方法不能重写，返回运行时类
- `wait/notify/notifyAll`：用于多线程协作（与 synchronized 配合）
- `clone()`：`protected`，实现 `Cloneable` 接口后才能使用，默认**浅拷贝**

### 七、运算符优先级与类型转换

**自动类型转换（小 → 大）**：`byte → short → int → long → float → double`，`char → int`

**运算时的类型提升**：`byte + byte` 结果是 `int`（即使不溢出也会提升），因此：

```java
byte b = 1;
b = b + 1; // 编译报错，int 不能直接赋给 byte
b += 1;    // 正确，等价于 b = (byte)(b + 1)，复合赋值自带强转
b = (byte)(b + 1); // 正确
```

**强制类型转换（大 → 小）**：`(目标类型) 值`，可能**精度丢失或溢出**。如 `(int) 3.14 = 3`，`(byte) 200 = -56`（溢出）

**运算符优先级**（从高到低，节选）：`()` → 单目 `++ -- !` → 算术 `* / %` → 算术 `+ -` → 移位 → 关系 → 相等 → 位运算 → 逻辑 → 三目 → 赋值。**记不清就加括号**，可读性优先。

### 八、值传递与深浅拷贝

**Java 只有值传递**（pass by value）：

- 基本类型：传递**值的副本**，修改形参不影响实参
- 引用类型：传递**引用的副本（地址值）**，形参和实参指向同一个对象
- 可以通过形参**修改对象属性**（实参能看到），但**不能让实参指向新对象**（形参重新赋值不影响实参）

```java
static void swap(List<Integer> a, List<Integer> b) {
    List<Integer> tmp = a; a = b; b = tmp; // 形参交换不影响实参
}
```

**浅拷贝 vs 深拷贝**：

- **浅拷贝**：只复制对象本身和基本类型字段，引用类型字段共享（`Object.clone()` 默认行为）
- **深拷贝**：递归复制所有引用类型字段，完全独立
- 深拷贝实现方式：递归 clone、序列化反序列化（`Serializable`）、JSON 序列化（Jackson/Gson，注意循环引用）

---

## 代码示例

### 示例 1：Integer 缓存池陷阱

```java
public class IntegerCacheDemo {
    public static void main(String[] args) {
        Integer a = 127, b = 127;
        System.out.println(a == b); // true（命中 IntegerCache）

        Integer c = 128, d = 128;
        System.out.println(c == d); // false（超出缓存范围，新对象）

        Integer e = -129, f = -129;
        System.out.println(e == f); // false（超出下界）

        // 比较值务必用 equals
        System.out.println(c.equals(d)); // true
    }
}
```

### 示例 2：StringBuilder 性能优化

```java
public class StringConcatDemo {
    public static void main(String[] args) {
        final int N = 50000;

        // 反例：循环中 += 拼接，O(n²)
        long t1 = System.nanoTime();
        String s = "";
        for (int i = 0; i < N; i++) s += i;
        System.out.println("String += 耗时: " + (System.nanoTime() - t1) / 1e6 + " ms");

        // 正例：复用 StringBuilder，O(n)
        long t2 = System.nanoTime();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < N; i++) sb.append(i);
        String result = sb.toString();
        System.out.println("StringBuilder 耗时: " + (System.nanoTime() - t2) / 1e6 + " ms");
    }
}
```

### 示例 3：try-with-resources 实战

```java
public class TryWithResourcesDemo {
    public static String readFirstLine(String path) throws IOException {
        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            return br.readLine();
        }
        // 资源自动关闭，无需 finally
    }

    public static void main(String[] args) {
        try {
            String line = readFirstLine("test.txt");
            System.out.println(line);
        } catch (IOException e) {
            System.err.println("读取失败: " + e.getMessage());
        }
    }
}
```

### 示例 4：值传递证明

```java
public class PassByValueDemo {
    static class User { String name; User(String name) { this.name = name; } }

    static void changeRef(User u) { u = new User("New"); }      // 形参指向新对象
    static void changeField(User u) { u.name = "Modified"; }    // 修改共享对象

    public static void main(String[] args) {
        User u = new User("Original");
        changeRef(u);
        System.out.println(u.name); // Original（实参引用未变）

        changeField(u);
        System.out.println(u.name); // Modified（共享对象被修改）
    }
}
```

---

## 易错点 / 最佳实践

1. **包装类比较必须用 `equals`**：Integer 缓存池只覆盖 -128~127，超出范围 `==` 结果不可预期。Lombok 的 `@EqualsAndHashCode` 不要偷懒，要确保 equals 与 hashCode 一致。

2. **循环中字符串拼接用 StringBuilder**：`s += "x"` 每次循环都会创建 StringBuilder + toString()，时间复杂度 O(n²)。但**简单单次拼接**（如 `String s = a + b + c`）编译器会自动优化为 StringBuilder.append，无需手动。

3. **finally 块不要 return / 抛异常**：会吞掉 try 块中的异常或返回值，调试极难定位。若 finally 必须抛异常，用 `original.addSuppressed(e)` 保留原异常。

4. **重写 equals 必须重写 hashCode**：特别是放进 HashMap、HashSet 的 key，不重写会导致内存泄漏 + 数据丢失。`Objects.hash(field1, field2)` 是常用写法（但注意 Objects.hash 会创建数组，性能敏感场景手写）。

5. **异常处理黄金法则**：
   - 不要捕获所有异常后只 `e.printStackTrace()`（生产环境丢失上下文）
   - **不要用异常控制正常业务流程**（异常构造栈极贵）
   - 受检异常能转 RuntimeException 就转，避免污染方法签名（特别是接口设计）
   - 自定义异常保留 cause（`throw new BizException("msg", e)`）

---

## 面试常见问题

**Q1：`Integer a = 127; Integer b = 127; a == b` 为什么是 true？**
A：因为 `Integer.valueOf(int)` 内部使用了 `IntegerCache`，默认缓存范围 -128~127。命中缓存时返回的是缓存数组中的同一个对象引用。超出范围（如 128）会 `new Integer(i)`，地址不同，结果为 false。结论：**包装类比较必须用 equals**。

**Q2：String 为什么要设计成不可变？**
A：四个核心原因：
1. **字符串常量池**：相同字符串只存一份，节省内存
2. **安全性**：作为 HashMap key、URL、文件路径、数据库账号等不会被恶意修改
3. **线程安全**：天然不可变，多线程共享无需同步
4. **哈希缓存**：`hash` 字段懒加载，第一次算完后缓存，后续 O(1) 取用

**Q3：StringBuilder 和 StringBuffer 有什么区别？**
A：StringBuffer 所有公开方法用 `synchronized` 修饰，线程安全但性能低（JDK 1.0+）；StringBuilder 不加锁，性能更高（JDK 1.5+）。**单线程场景永远选 StringBuilder**；多线程共享 StringBuilder 需外部加锁或换 StringBuffer。

**Q4：`==` 和 `equals` 的区别？**
A：`==` 是运算符，比较基本类型是比较值，比较引用类型是比较**内存地址**。`equals` 是 Object 类的方法，默认实现就是 `==`，但 String/Integer 等类重写后比较的是**内容**。重写 equals 必须同时重写 hashCode 以保持契约。

**Q5：Java 是值传递还是引用传递？**
A：**只有值传递**。基本类型传值的副本；引用类型传引用的副本（地址值）。形参和实参指向同一个对象，可以通过形参修改对象属性，但**不能让实参指向新对象**（形参重新赋值不影响实参）。

**Q6：try-catch-finally 中 finally 一定会执行吗？**
A：**不一定**。以下情况 finally 不执行：
1. `System.exit(int)` 终止 JVM
2. 守护线程全部死亡且主线程结束
3. finally 块自身抛异常导致 JVM 崩溃
4. 物理断电 / 操作系统 kill -9

**Q7：Error 和 Exception 的区别？**
A：Error 表示 JVM 自身问题（OOM、StackOverflow、LinkageError），**不应该捕获**，程序通常无法恢复；Exception 表示程序逻辑问题，需要捕获处理。Exception 又分 Checked（编译期强制处理，如 IOException）和 Unchecked（RuntimeException 及其子类，可处理可不处理）。

**Q8：自动装箱 / 拆箱的性能影响？**
A：装箱会创建对象（堆分配 + GC 压力），拆箱调用方法。在循环中频繁装箱可能导致大量 GC（如 LongStream 替换 Stream<Long> 的核心原因之一就是避免装箱）。IntegerCache 只能缓解部分小整数场景，对 Long 同样存在但范围相同。

**Q9：浅拷贝和深拷贝怎么实现？**
A：浅拷贝：`Object.clone()`（需实现 Cloneable）或 `BeanUtils.copyProperties`。深拷贝实现方式：
1. 递归 clone（所有引用字段都实现 Cloneable）
2. 序列化 / 反序列化（要求 Serializable）
3. JSON 序列化（Jackson / Gson 转字符串再转回来，注意循环引用）
4. 第三方库：Apache Commons Lang `SerializationUtils.clone(t)`

**Q10：Java 14+ switch 表达式相比传统 switch 有什么好处？**
A：1. 箭头语法更简洁，`->` 自动 break，**不会 fall-through**
2. 可以**作为表达式**返回值（`String x = switch (...) {...};`）
3. 多值匹配 `case 1, 2, 3 ->`
4. `yield` 关键字从 case 块返回值
5. 编译器做**穷尽性检查**，枚举场景更友好

---

## 延伸阅读

1. **官方教程**：The Java Tutorials - Language Basics
   https://docs.oracle.com/javase/tutorial/java/nutsandbolts/index.html
2. **官方规范**：The Java Language Specification (JLS, Java 21)
   https://docs.oracle.com/javase/specs/jls/se21/html/index.html
3. **推荐书籍**：《Java 核心技术 卷 I》第 3-4 章（Cay S. Horstmann）- 基础语法与面向对象
4. **推荐书籍**：《Effective Java》第 1-2 章（Joshua Bloch）- 静态工厂、Builder 模式等最佳实践
5. **视频教程**：尚硅谷宋红康 Java 基础全套（B 站）
   https://www.bilibili.com/video/BV1Kb411W75N
