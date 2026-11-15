-- ============================================================
-- AI 辅助说明：本文件由 AI 辅助生成/修改
-- 生成内容：种子数据——管理员账号 + 演示普通账号 demo + 4 个分类 + 20 条商品（计划书 3.2 / 3.3）
-- 人工修改：无
-- 审查状态：待人工审查
-- 幂等设计：全部 INSERT IGNORE，应用每次重启都会执行本文件，已存在的行自动跳过
-- ============================================================

-- ========== 管理员账号（Day 2） ==========
-- password 是 BCrypt 哈希（jshell + spring-security-crypto 真实计算，对应明文 admin123）
INSERT IGNORE INTO t_user (id, username, password, email, role, status, created_at)
VALUES (1,
        'admin',
        '$2a$10$/pKQwiGDlyJW3TeUijAIp.cULJFuhJ.GS0zO/c8Zv/sWEYpLtfOCS',
        'admin@shopsphere.com',
        'ADMIN',
        1,
        NOW());

-- ========== 演示用普通用户（2026-11-15 补） ==========
-- 课程验收时老师若不想走注册流程，可直接用 demo / demo123 登录下单（README「测试账号」已写明）。
-- 不显式指定 id：交给 AUTO_INCREMENT，避免与已有库里的主键冲突；
-- 幂等靠 username 唯一索引，重复执行时 INSERT IGNORE 自动跳过。
-- 哈希对应明文 demo123（BCrypt，来自项目运行期真实注册的账号，未手写）。
INSERT IGNORE INTO t_user (username, password, email, role, status, created_at)
VALUES ('demo',
        '$2a$10$U6sB9JjWIfW112ChqfFz/usY.E.xuIdIDZJ02eR27kZbDlXALWTTS',
        '3101362993@qq.com',
        'CUSTOMER',
        1,
        NOW());

-- ========== 4 个分类（Day 3） ==========
-- 显式指定 id：商品行要靠 category_id 引用，固定 id 才能保证种子数据可重复执行
INSERT IGNORE INTO category (id, name, sort_order) VALUES
(1, '手机数码', 1),
(2, '电脑办公', 2),
(3, '家用电器', 3),
(4, '图书音像', 4);

-- ========== 20 条商品（Day 3） ==========
-- status 存枚举名字符串（@Enumerated(EnumType.STRING)）
-- image_url 用 picsum 占位图，seed 固定则图片固定
INSERT IGNORE INTO product (id, name, description, price, stock, category_id, image_url, status, sales, created_at, updated_at) VALUES
(1,  '华为 Mate 60 Pro', '麒麟芯片，卫星通话，超可靠玄武架构。', 6999.00, 50, 1, 'https://picsum.photos/seed/p1/400/300', 'ON_SALE', 328, NOW(), NOW()),
(2,  'iPhone 15 Pro Max', 'A17 Pro 芯片，钛金属机身，5 倍光学变焦。', 9999.00, 30, 1, 'https://picsum.photos/seed/p2/400/300', 'ON_SALE', 512, NOW(), NOW()),
(3,  '小米 14', '徕卡光学镜头，第三代骁龙 8 移动平台。', 3999.00, 80, 1, 'https://picsum.photos/seed/p3/400/300', 'ON_SALE', 456, NOW(), NOW()),
(4,  'Redmi Note 13', '一亿像素，高刷护眼屏，千元机首选。', 1099.00, 200, 1, 'https://picsum.photos/seed/p4/400/300', 'ON_SALE', 892, NOW(), NOW()),
(5,  'Apple Watch SE', '健康监测，运动追踪，与 iPhone 无缝联动。', 1899.00, 60, 1, 'https://picsum.photos/seed/p5/400/300', 'ON_SALE', 156, NOW(), NOW()),
(6,  '联想小新 Pro 14', '2.8K 高刷屏，锐龙 7 标压处理器，轻薄全能本。', 5499.00, 40, 2, 'https://picsum.photos/seed/p6/400/300', 'ON_SALE', 234, NOW(), NOW()),
(7,  'MacBook Air M3', 'M3 芯片，18 小时续航，轻至 1.24kg。', 9499.00, 25, 2, 'https://picsum.photos/seed/p7/400/300', 'ON_SALE', 178, NOW(), NOW()),
(8,  '戴尔 U2723QE 27寸 4K 显示器', 'IPS Black 面板，90W Type-C 反向供电。', 3499.00, 35, 2, 'https://picsum.photos/seed/p8/400/300', 'ON_SALE', 98, NOW(), NOW()),
(9,  '罗技 MX Master 3S 鼠标', '8000 DPI 静音点击，跨设备 Flow 切换。', 699.00, 120, 2, 'https://picsum.photos/seed/p9/400/300', 'ON_SALE', 567, NOW(), NOW()),
(10, '机械革命极光 X 游戏本', 'RTX 4060 满血显卡，165Hz 电竞屏。', 7499.00, 20, 2, 'https://picsum.photos/seed/p10/400/300', 'ON_SALE', 89, NOW(), NOW()),
(11, '美的变频空调 1.5 匹', '新一级能效，智能控温，静音除湿。', 2699.00, 45, 3, 'https://picsum.photos/seed/p11/400/300', 'ON_SALE', 321, NOW(), NOW()),
(12, '海尔滚筒洗衣机 10kg', '直驱变频电机，除菌除螨洗。', 2499.00, 30, 3, 'https://picsum.photos/seed/p12/400/300', 'ON_SALE', 145, NOW(), NOW()),
(13, '小米电视 EA65 65英寸', '4K 全面屏，远场语音，金属机身。', 2299.00, 55, 3, 'https://picsum.photos/seed/p13/400/300', 'ON_SALE', 267, NOW(), NOW()),
(14, '戴森 V12 吸尘器', '激光探测微尘，轻量主机，60 分钟续航。', 3990.00, 18, 3, 'https://picsum.photos/seed/p14/400/300', 'ON_SALE', 98, NOW(), NOW()),
(15, '苏泊尔电饭煲 4L', '球釜内胆，24 小时智能预约。', 299.00, 150, 3, 'https://picsum.photos/seed/p15/400/300', 'ON_SALE', 723, NOW(), NOW()),
(16, 'Java 核心技术 卷I（原书第 12 版）', 'Java 程序员案头经典，覆盖 Java 17 新特性。', 139.00, 200, 4, 'https://picsum.photos/seed/p16/400/300', 'ON_SALE', 445, NOW(), NOW()),
(17, '深入理解 Java 虚拟机（第 3 版）', '周志明著，JVM 进阶必读。', 129.00, 180, 4, 'https://picsum.photos/seed/p17/400/300', 'ON_SALE', 689, NOW(), NOW()),
(18, 'Spring Boot 实战', '从入门到微服务，案例驱动讲解。', 89.00, 160, 4, 'https://picsum.photos/seed/p18/400/300', 'ON_SALE', 234, NOW(), NOW()),
(19, '三体（全集三册）', '刘慈欣科幻代表作，雨果奖获奖作品。', 93.00, 300, 4, 'https://picsum.photos/seed/p19/400/300', 'ON_SALE', 1102, NOW(), NOW()),
(20, '算法导论（原书第 3 版）', '计算机算法圣经，MIT 教材。', 128.00, 90, 4, 'https://picsum.photos/seed/p20/400/300', 'ON_SALE', 167, NOW(), NOW());
