# 02 - 面向对象

## 概述

**面向对象（OOP, Object-Oriented Programming）** 是 Java 的根基——Java 的设计哲学是"一切皆对象"（almost everything is an object）。本模块围绕"三大特性 + 关键字 + 抽象机制 + 内部类 + Object 根类 + 泛型"展开，是面试中出现频率最高的章节之一。

关键点速览：

| 维度 | 内容 |
| --- | --- |
| 三大特性 | **封装**（Encapsulation）、**继承**（Inheritance）、**多态**（Polymorphism） |
| 核心关键字 | `this`、`super`、`static`、`final`、`abstract`、`interface` |
| 抽象机制 | 抽象类（`abstract class`） vs 接口（`interface`） |
| 内部类 | 成员内部类、静态内部类、局部内部类、**匿名内部类**（Lambda 前身） |
| 根类 | `java.lang.Object`：所有类的超类 |
| 泛型 | JDK 5 引入，编译期类型安全，**运行期类型擦除** |
| 面试热度 | ⭐⭐⭐⭐⭐（多态原理、equals/hashCode 契约必问） |

---

## 核心概念

### 1. 类与对象：实例化与构造器链

**类（class）** 是对象的蓝图，**对象（object）** 是类的运行时实例。`new` 关键字在堆上分配内存并返回引用，引用本身存在栈上。

构造器是类初始化的入口，三条铁律：

1. 名字必须与类名**完全一致**，无返回值（连 `void` 都不能写）。
2. 编译器在没有任何显式构造器时自动生成**默认构造器**（无参、可见性同类）；一旦显式声明任意构造器，默认构造器就**不再自动生成**。
3. 构造器链：`this(...)` 调用本类其他构造器，`super(...)` 调用父类构造器，且**两者必须出现在构造器第一行**，所以同一构造器中只能出现一个。

```java
public class User {
    private Long id;
    private String name;

    public User() {                          // 默认构造器
        this(0L, "anonymous");               // 链式调用有参构造器
    }

    public User(Long id, String name) {
        this.id = id;
        this.name = name;
    }
}
```

### 2. 三大特性

#### 封装（Encapsulation）

把数据（field）和操作数据的方法（method）打包到类内，对外**隐藏细节、暴露受控接口**。落地手段是 `private` 字段 + `public` 的 getter/setter，配合访问修饰符控制可见性。

#### 继承（Inheritance）

`extends` 关键字建立父子关系。Java 是**单继承**（一个类只能有一个直接父类），但可以多层继承（如 `C extends B extends A`）。子类继承父类的 `public` / `protected` 成员，但不能继承 `private` 构造器。`Object` 是所有类的**直接或间接父类**。

#### 多态（Polymorphism）

包括**编译期多态**（方法重载 `overload`）和**运行期多态**（方法重写 `override`）。面试默认问的是后者：

- **三个必要条件**：继承关系、子类重写父类方法、父类引用指向子类对象（`Parent p = new Child();`）。
- **运行期绑定原理：虚方法表（vtable）**。JVM 在方法区为每个类维护一张**虚方法表**，记录该类所有可被继承和重写的方法的实际入口地址。调用虚方法时，JVM 先取对象实际类型（`obj.getClass()`）的 vtable，按偏移量查表得到真实方法入口并跳转。这就是经典的"**动态分派（Dynamic Dispatch）**"。
- 调用的真实方法是**最子类优先**，从下往上沿继承链查找。
- `final`、`static`、`private` 方法**不参与**虚方法表（`final` 在编译期绑定，`static` 属于类、`private` 不可继承）。

```java
Animal a = new Dog();
a.shout();        // 编译期类型 Animal，运行期类型 Dog，调用 Dog.shout()
```

### 3. this 和 super

| 关键字 | 含义 | 常见用途 |
| --- | --- | --- |
| `this` | 当前对象的引用 | 区分成员变量与局部变量、构造器链 `this(...)`、作为参数传递 |
| `super` | 直接父类对象的引用 | 访问被遮蔽的父类成员、构造器链 `super(...)` |

注意：`this` 和 `super` 都不能用在 `static` 方法中（静态上下文没有"当前对象"）。

### 4. 访问修饰符

| 修饰符 | 同类 | 同包 | 子类 | 其他包 |
| --- | --- | --- | --- | --- |
| `public` | ✅ | ✅ | ✅ | ✅ |
| `protected` | ✅ | ✅ | ✅ | ❌ |
| `default`（无修饰符） | ✅ | ✅ | ❌ | ❌ |
| `private` | ✅ | ❌ | ❌ | ❌ |

设计原则：**字段私有化，方法按需开放**。能用 `private` 就别用 `default`，能用 `protected` 就别用 `public`，能少暴露就少暴露。

### 5. static 关键字

`static` 修饰的成员**属于类而非对象**，所有实例共享同一份，类加载阶段就被初始化。

- **静态变量（类变量）**：存放于方法区，类加载时初始化，**先于对象**产生。可通过 `类名.变量名` 访问。
- **静态方法**：无 `this`，不能直接访问非静态成员。可被继承但**不能被重写**（多态不生效）。
- **静态代码块**：`static { ... }`，类加载时执行一次，用于加载配置文件、初始化静态资源。**优先于**普通代码块和构造器执行。
- **静态内部类**：不持有外部类引用，因此**不会导致外部类内存泄漏**（这是它在 Android 中被偏爱的原因）。

### 6. final 关键字

- **修饰类**：类不可被继承（如 `String`、`Math` 都是 `final`）。
- **修饰方法**：方法不可被重写（`private` 方法隐式 `final`）。`final` 方法在编译期绑定，JIT 可内联优化。
- **修饰变量**：变量只能赋值一次。**基本类型**值不可变；**引用类型**引用不可变（指向的对象内部状态仍可改）。
- **空白 final（blank final）**：字段声明时未赋值，必须在构造器结束前赋值，用于保证不可变对象的字段完整性。

### 7. 抽象类 vs 接口

| 维度 | 抽象类（`abstract class`） | 接口（`interface`） |
| --- | --- | --- |
| 实例化 | 不能 | 不能（Java 8 默认方法后仍不可） |
| 方法 | 抽象方法 + 具体方法 | 抽象方法 + 默认方法 + 静态方法（Java 8+） + 私有方法（Java 9+） |
| 字段 | 任意修饰符、任意值 | 隐式 `public static final`（只能是常量） |
| 构造器 | 有 | 无 |
| 继承数量 | 单继承 | 多实现（`implements A, B`） |
| 设计意图 | "is-a"，提取共性 | "can-do"，约定能力 |
| 典型例子 | `AbstractQueuedSynchronizer` | `Comparable`、`Runnable` |

**Java 8 默认方法**：`default` 修饰的方法可以有实现，用于接口演化（binary compatibility）。`Collection.stream()` 就是默认方法。

**函数式接口**：只有一个抽象方法的接口，可用 `@FunctionalInterface` 标注（如 `Runnable`、`Comparator`、`Function<T,R>`），是 Lambda 表达式和方法引用的目标类型。

```java
@FunctionalInterface
interface Converter<F, T> {
    T convert(F from);

    default Converter<F, T> andThen(Function<T, T> after) {  // 默认方法
        return f -> after.apply(this.convert(f));
    }
}
```

### 8. 内部类

Java 允许把一个类定义在另一个类的内部，本质是**语法糖**——编译后生成独立的 `.class` 文件（如 `Outer$Inner.class`）。

- **成员内部类**：定义在类内部、方法和代码块外。**持有外部类引用**（`OuterClassName.this`），可访问外部类所有成员。
- **静态内部类**：`static` 修饰。**不持有**外部类引用，**不依赖**外部类实例，可独立创建。
- **局部内部类**：定义在方法或代码块内，作用域仅限于所在块，**不能用访问修饰符**。
- **匿名内部类**：没有名字的局部内部类，**继承一个类或实现一个接口**后立即创建实例。是 JDK 8 之前实现"函数式编程"的唯一方式，Lambda 表达式在能用的地方几乎都取代了它。

```java
// 匿名内部类 -> 等价 Lambda：Comparator.comparingInt(String::length)
List<String> list = new ArrayList<>();
list.sort(new Comparator<String>() {
    @Override
    public int compare(String a, String b) {
        return Integer.compare(a.length(), b.length());
    }
});
```

### 9. Object 类核心方法

`Object` 是所有类的祖先，其方法几乎都被重写过。

| 方法 | 签名要点 | 重写注意事项 |
| --- | --- | --- |
| `equals` | `boolean equals(Object obj)` | **自反、对称、传递、一致、非空**；重写时必须重写 `hashCode` |
| `hashCode` | `int hashCode()` | **`equals` 相等则 `hashCode` 必须相等**，反之不要求；用于 `HashMap` / `HashSet` 桶定位 |
| `toString` | `String toString()` | 调试必备，IDE / Lombok `@Data` 可自动生成 |
| `clone` | `protected Object clone()` | 实现对象**浅拷贝**，需类实现 `Cloneable`（标记接口），深拷贝要自己写 |
| `getClass` | `final Class<?> getClass()` | 获取**运行期实际类型**，常用于反射 |
| `wait/notify/notifyAll` | 与 `synchronized` 配合 | 必须在持有该对象监视器锁的代码块内调用，否则抛 `IllegalMonitorStateException` |

**equals 与 hashCode 契约**是面试必问：两个对象 `equals` 返回 `true`，`hashCode` 必须相等；反之不等。这是 `HashMap`、`HashSet` 能正确去重的根基。

### 10. 泛型基础

**泛型（Generics）** 是 JDK 5 引入的参数化类型机制，**只在编译期生效**，运行期会被**类型擦除（type erasure）**——所有泛型信息擦成边界（无边界则擦成 `Object`），并自动插入强制转型。

- **泛型类**：`class Box<T> { ... }`，`T` 在类内作为类型占位符。
- **泛型方法**：`<T> T identity(T t)`，方法级泛型独立于类泛型。
- **通配符 `?`**：
  - `? extends T`：上界通配符，**只读不写**（生产者 `Producer Extends`）。
  - `? super T`：下界通配符，**只写不读**（消费者 `Consumer Super`）。
  - **PECS 原则（Producer Extends, Consumer Super）**，是 `Collections.copy(dest, src)` 等 API 的设计依据。
- **类型擦除的副作用**：`List<Integer>` 与 `List<String>` 运行期是**同一个类**（`List.class`），所以**不能用 `instanceof` 检查泛型参数**；数组不支持泛型（`new List<String>[10]` 编译报错）；泛型不能用于 `catch`、`instanceof`、基本类型（`List<int>` 不行）。

---

## 代码示例

### 示例 1：完整的多态 + 虚方法表示意

```java
public class PolymorphismDemo {
    public static void main(String[] args) {
        Animal[] arr = { new Dog(), new Cat(), new Animal() };
        for (Animal a : arr) {
            a.shout();         // 运行期分派：Dog.shout / Cat.shout / Animal.shout
        }
    }
}

class Animal {
    public void shout() { System.out.println("..."); }
}

class Dog extends Animal {
    @Override
    public void shout() { System.out.println("汪"); }
}

class Cat extends Animal {
    @Override
    public void shout() { System.out.println("喵"); }
}
```

### 示例 2：基于 `AbstractQueuedSynchronizer` 思路的模板方法（抽象类 vs 接口的实战选择）

```java
abstract class AbstractTemplate {
    // 模板方法，final 防止子类破坏流程
    public final void process() {
        check();
        doWork();
        cleanup();
    }
    protected abstract void doWork();

    protected void check()    { System.out.println("校验"); }
    protected void cleanup()  { System.out.println("清理"); }
}

class OrderProcessor extends AbstractTemplate {
    @Override
    protected void doWork() { System.out.println("处理订单"); }
}
```

### 示例 3：手写 `equals` / `hashCode`（契约落地）

```java
public class User {
    private final Long id;
    private String name;

    public User(Long id, String name) { this.id = id; this.name = name; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof User u)) return false;     // Java 16 模式匹配
        return Objects.equals(id, u.id) && Objects.equals(name, u.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name);                 // 31 倍经典公式
    }
}
```

### 示例 4：PECS 原则实战

```java
// src 是生产者：只读取，? extends T
public static <T> void copy(List<? extends T> src, List<? super T> dest) {
    for (T t : src) dest.add(t);
}

List<Integer> src = Arrays.asList(1, 2, 3);
List<Number> dest = new ArrayList<>();
copy(src, dest);                                       // 编译通过
```

---

## 易错点 / 最佳实践

1. **重写 `equals` 必须重写 `hashCode`，反之亦然**。仅重写 `equals` 会导致对象放入 `HashMap` 后**找不到**（`get` 返回 `null`），这是面试手撕代码最高频的坑。Lombok 的 `@Data` / `@EqualsAndHashCode` 能避免手写出错。

2. **构造器链必须放第一行**。`this()` 和 `super()` 不能同时出现，且调用之后**不能**再有其他语句。如果父类**没有**无参构造器（自己写了带参构造器而忘了显式声明 `super()`），子类构造器**必须**显式调用 `super(参数)`，否则编译报错。

3. **`static` 方法不参与多态**。`Parent p = new Child(); p.staticMethod();` 调的是 `Parent` 的方法，因为 `static` 属于类而非对象。同理 `private` / `final` 方法也不参与。

4. **接口字段默认 `public static final`**。在接口里写 `int A = 1;` 等价于 `public static final int A = 1;`，常用于枚举替代品（接口常量反模式，慎用）。

5. **泛型不能用于基本类型**：`List<int>` 编译报错，要用 `List<Integer>`。`? extends Number` 可以接 `List<Integer>`，但**不能往里 `add` 任何元素**（除 `null`），因为编译器无法确定实际类型——PECS 原则的精髓。

6. **匿名内部类持有外部引用**。匿名内部类会捕获外部方法的局部变量，**该变量必须 `final` 或事实 `final`**（Java 8 放宽），否则编译失败。

---

## 面试常见问题

**Q1：说一下 Java 运行时多态的实现原理？**
A：通过 **JVM 的虚方法表（vtable）** 实现。每个类在方法区维护一张 vtable，记录所有虚方法的实际入口。调用虚方法时，JVM 取对象运行期类型的 vtable，按偏移量查表并跳转——这就是动态分派（Dynamic Dispatch）。`final` / `static` / `private` 方法不进入 vtable，编译期即可确定调用目标。

**Q2：`==` 和 `equals` 有什么区别？**
A：`==` 比较**引用（地址）**，基本类型比较值；`equals` 是 `Object` 的方法，默认实现就是 `==`。`String`、`Integer` 等重写了 `equals` 用于比较内容。**重写 `equals` 必须重写 `hashCode`**，否则 `HashMap` / `HashSet` 等哈希容器会失效。

**Q3：抽象类和接口的区别？什么时候用抽象类，什么时候用接口？**
A：抽象类支持构造器、单继承、任意字段，适合"is-a"且需要**共享状态和实现**的场景（如模板方法模式）；接口支持多实现、方法默认 `public abstract`、字段默认 `public static final`，适合"can-do"的能力约定（如 `Serializable`、`Comparable`）。**能用接口就用接口**（更灵活），需要共享实现时再上抽象类。

**Q4：`static` 方法能被重写吗？`final` 方法呢？**
A：`static` 方法**不能**被重写，只能被**隐藏（hide）**——调用时看编译期类型而非运行期类型。`final` 方法**不能被重写**，且 `final` 私有方法会被编译器内联优化，性能更好。

**Q5：泛型的类型擦除是什么？有什么影响？**
A：泛型信息**只在编译期存在**，编译后擦除为边界类型（无边界则擦成 `Object`），并自动插入强制转型。影响包括：`List<Integer>` 和 `List<String>` 是**同一个类**（`List.class`）；不能用 `instanceof` 检查泛型参数；不能创建泛型数组（`new T[10]`）；不能用于 `catch` / `instanceof`；不能用基本类型做类型参数（`List<int>` 不行，要用 `List<Integer>`）。

**Q6：`String` 为什么是 `final` 的？**
A：三大原因：① **安全**——作为 `HashMap` key、网络参数、类加载路径等场景必须可靠，继承会破坏不可变语义；② **性能**——`final` 利于 JIT 内联、常量池优化、消除同步；③ **字符串常量池**——不可变才能共享，否则池化失效。详见 `String` 章节。

**Q7：内部类为什么能访问外部类的 `private` 字段？**
A：编译期**自动生成合成访问方法**（如 `access$000`），内部类通过这些合成桥接方法访问外部私有成员，因此合法但**有性能开销**。静态内部类**不持有**外部类引用，更轻量。

**Q8：构造器能不能被重写？能不能被 `private` 修饰？**
A：**不能重写**（构造器不属于成员方法），**可以被 `private` 修饰**（单例模式、工具类阻止外部 `new`）。`private` 构造器 + 静态方法返回唯一实例是经典单例实现。

**Q9：`Object` 有哪些方法？哪些是 `final` 的？**
A：常见方法：`getClass`（final）、`hashCode`、`equals`、`toString`、`clone`、`wait`、`notify`、`notifyAll`、`finalize`（JDK 9 已废弃）。只有 `getClass` 是 `final`（运行时类型不能改）。`wait/notify` 用于线程间通信，必须在 `synchronized` 块内调用。

**Q10：什么是 `this()` 和 `super()`？为什么不能同时出现？**
A：`this()` 调用本类其他构造器，`super()` 调用父类构造器，都必须出现在**构造器第一行**。因为每个构造器都必须以"调用父类构造器"结束，两者都要占第一行就冲突。如果都没写，`super()` 会被**隐式插入**（调用父类无参构造器，若父类无则编译报错）。

---

## 延伸阅读

- **《Java 核心技术 卷 I》第 4-5 章**——Cay S. Horstmann，面向对象基础最权威的入门讲解，覆盖封装/继承/多态/接口/内部类。
- **《Effective Java》第 3 章「类和接口」**——Joshua Bloch，告诉你"如何设计好的类与接口"（最小化可访问性、使可变性最小化、接口优于抽象类等），**面试进阶必读**。
- **官方文档：[The Java Tutorials - Object-Oriented Programming Concepts](https://docs.oracle.com/javase/tutorial/java/concepts/)**——Oracle 官方 OOP 概念梳理，英文原版最准确。
- **视频：[黑马程序员 Java 零基础到精通（含 OOP 章节）](https://www.bilibili.com/video/BV1Cv411372m)**——B 站评分最高的 Java 入门课，第 100-150 集对应本模块，适合二刷查漏。
