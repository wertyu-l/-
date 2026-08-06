-- 模拟设备2 数据库 — 输入设备（2个输入通道 HDMI-1、HDMI-2）
CREATE TABLE IF NOT EXISTS DEVICE_INFO (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    device_name      VARCHAR(200)  NOT NULL,
    device_type      VARCHAR(50)   DEFAULT 'REST',
    device_category  VARCHAR(20)   DEFAULT 'INPUT',
    model            VARCHAR(100)  DEFAULT '',
    serial_number    VARCHAR(100)  DEFAULT '',
    input_channel_1  VARCHAR(100)  DEFAULT '',
    input_channel_2  VARCHAR(100)  DEFAULT '',
    max_resolution   VARCHAR(50)   DEFAULT '1920x1080'
);

CREATE TABLE IF NOT EXISTS DEVICE_CAPABILITY (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    support_move     BOOLEAN       DEFAULT TRUE,
    support_resize   BOOLEAN       DEFAULT TRUE,
    support_overlay  BOOLEAN       DEFAULT TRUE,
    max_resolution   VARCHAR(50)   DEFAULT '1920x1080',
    input_channel_1  VARCHAR(100)  DEFAULT '',
    input_channel_2  VARCHAR(100)  DEFAULT ''

);

CREATE TABLE IF NOT EXISTS DEVICE_WINDOW (
    window_id    VARCHAR(100) PRIMARY KEY,
    channel_name VARCHAR(100) NOT NULL,
    x            INT           DEFAULT 0,
    y            INT           DEFAULT 0,
    width        INT           DEFAULT 1920,
    height       INT           DEFAULT 1080,
    source_type  VARCHAR(50)   DEFAULT '',
    source_url   VARCHAR(500)  DEFAULT '',
    create_time  VARCHAR(20)   DEFAULT ''
);
