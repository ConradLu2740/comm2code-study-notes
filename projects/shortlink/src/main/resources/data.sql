-- 测试数据：H2 内存模式启动时执行
-- 生产 MySQL 环境不会执行这段（profile 区分）
INSERT INTO short_link (id, short_code, long_url, created_at, expire_at, click_count) VALUES (1, 'demo01', 'https://www.baidu.com', CURRENT_TIMESTAMP, NULL, 0);
INSERT INTO short_link (id, short_code, long_url, created_at, expire_at, click_count) VALUES (2, 'demo02', 'https://github.com', CURRENT_TIMESTAMP, NULL, 0);
INSERT INTO short_link (id, short_code, long_url, created_at, expire_at, click_count) VALUES (3, 'demo03', 'https://www.spring.io', CURRENT_TIMESTAMP, NULL, 0);
