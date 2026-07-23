-- 仅在 SYS_USER 表为空时插入初始管理员
INSERT INTO SYS_USER (username, password, role, user_type, user_level, department, position, phone, email, is_enabled, valid_until)
SELECT 'admin', 'admin123', 'admin', '系统用户', 99, '技术部', '系统管理员', '13800000000', 'admin@example.com', 1, DATEADD('MONTH', 6, CURRENT_TIMESTAMP)
WHERE NOT EXISTS (SELECT 1 FROM SYS_USER WHERE username = 'admin');