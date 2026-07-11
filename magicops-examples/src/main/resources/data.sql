-- 示例数据
INSERT INTO users (name, email, age) VALUES ('张三', 'zhangsan@example.com', 28);
INSERT INTO users (name, email, age) VALUES ('李四', 'lisi@example.com', 35);
INSERT INTO users (name, email, age) VALUES ('王五', 'wangwu@example.com', 42);
INSERT INTO users (name, email, age) VALUES ('赵六', 'zhaoliu@example.com', 31);
INSERT INTO users (name, email, age) VALUES ('钱七', 'qianqi@example.com', 26);

INSERT INTO orders (user_id, product, amount, status) VALUES (1, '笔记本电脑', 6999.00, 'COMPLETED');
INSERT INTO orders (user_id, product, amount, status) VALUES (1, '无线鼠标', 129.00, 'COMPLETED');
INSERT INTO orders (user_id, product, amount, status) VALUES (2, '机械键盘', 599.00, 'PENDING');
INSERT INTO orders (user_id, product, amount, status) VALUES (3, '显示器', 2499.00, 'COMPLETED');
INSERT INTO orders (user_id, product, amount, status) VALUES (4, '耳机', 399.00, 'SHIPPED');
