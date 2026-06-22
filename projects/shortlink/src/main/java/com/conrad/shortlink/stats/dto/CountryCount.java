package com.conrad.shortlink.stats.dto;

/**
 * 按 country 聚合的统计结果。
 *
 * <p>教学点：这是 interface projection，不是普通 DTO。
 * Spring Data JPA 在运行时会生成动态代理类，
 * 方法名 {@code getCountry()} / {@code getCount()} 对应查询结果里的
 * 别名 {@code country} / {@code count}。
 *
 * <p>使用 interface 而不是 record/class 的好处：
 * <ul>
 *   <li>无需手写实现类，框架自动生成</li>
 *   <li>字段顺序、大小写不敏感，JPQL 改了 SQL 不影响</li>
 *   <li>序列化时 Jackson 会通过 getter 输出 JSON</li>
 * </ul>
 */
public interface CountryCount {
    String getCountry();
    long getCount();
}
