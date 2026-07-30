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

-- =============================================
-- 设备管理模块
-- =============================================
CREATE TABLE IF NOT EXISTS DEVICE (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    device_name     VARCHAR(200)  NOT NULL,
    device_type     VARCHAR(50)   DEFAULT 'REST',
    model           VARCHAR(100)  DEFAULT '',
    serial_number   VARCHAR(100)  DEFAULT '',
    output_channels INT           DEFAULT 1,
    max_resolution  VARCHAR(50)   DEFAULT '1920x1080',
    base_url        VARCHAR(300)  NOT NULL UNIQUE,
    enabled         INT           DEFAULT 1,
    online          INT           DEFAULT 0,
    last_heartbeat  TIMESTAMP     NULL,
    create_time     TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP     DEFAULT CURRENT_TIMESTAMP
);