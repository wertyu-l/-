ao-- =============================================
-- 模拟设备数据库（H2）
-- 一个进程 = 一台设备，自启动建表
-- =============================================

CREATE TABLE IF NOT EXISTS DEVICE_INFO (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    device_name     VARCHAR(200)  NOT NULL,
    device_type     VARCHAR(50)   DEFAULT 'REST',
    model           VARCHAR(100)  DEFAULT '',
    serial_number   VARCHAR(100)  DEFAULT '',
    output_channels INT           DEFAULT 1,
    max_resolution  VARCHAR(50)   DEFAULT '1920x1080'
);

CREATE TABLE IF NOT EXISTS DEVICE_CAPABILITY (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    max_windows     INT           DEFAULT 4,
    support_move    BOOLEAN       DEFAULT TRUE,
    support_resize  BOOLEAN       DEFAULT TRUE,
    support_overlay BOOLEAN       DEFAULT TRUE,
    max_resolution  VARCHAR(50)   DEFAULT '1920x1080',
    output_channels INT           DEFAULT 2
);

CREATE TABLE IF NOT EXISTS DEVICE_WINDOW (
    window_id   VARCHAR(100) PRIMARY KEY,
    channel     INT           NOT NULL,
    x           INT           DEFAULT 0,
    y           INT           DEFAULT 0,
    width       INT           DEFAULT 1920,
    height      INT           DEFAULT 1080,
    source_type VARCHAR(50)   DEFAULT '',
    source_url  VARCHAR(500)  DEFAULT '',
    create_time VARCHAR(20)   DEFAULT ''
);