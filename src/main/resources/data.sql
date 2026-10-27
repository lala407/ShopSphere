-- ============================================================
-- AI 辅助说明：本文件由 AI 辅助生成/修改
-- 生成内容：种子数据——预置 1 个管理员账号（计划书 3.2）
-- 人工修改：无
-- 审查状态：待人工审查
-- ⚠️ 管理员账号不开放注册，只能从这里预置
-- ============================================================

-- INSERT IGNORE：账号已存在时跳过（sql.init.mode=always 每次启动都会执行本文件，
-- 用 IGNORE 保证幂等——重复启动不报错、不产生重复数据）
-- password 是 BCrypt 哈希（jshell + spring-security-crypto 真实计算，对应明文 admin123）
INSERT IGNORE INTO t_user (username, password, email, role, status, created_at)
VALUES ('admin',
        '$2a$10$/pKQwiGDlyJW3TeUijAIp.cULJFuhJ.GS0zO/c8Zv/sWEYpLtfOCS',
        'admin@shopsphere.com',
        'ADMIN',
        1,
        NOW());
