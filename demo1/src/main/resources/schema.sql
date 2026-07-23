CREATE TABLE IF NOT EXISTS SYS_USER (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(100) NOT NULL,
    password VARCHAR(200) NOT NULL,
    role VARCHAR(50) DEFAULT 'user',
    user_type VARCHAR(50) DEFAULT '普通用户',
    user_level INT DEFAULT 1,
    department VARCHAR(100) DEFAULT '',
    position VARCHAR(100) DEFAULT '',
    phone VARCHAR(30) DEFAULT '',
    email VARCHAR(100) DEFAULT '',
    is_enabled INT DEFAULT 1,
    valid_until TIMESTAMP NULL
);