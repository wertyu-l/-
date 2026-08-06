-- 模拟设备3 数据库 — 输出设备（2个输出通道 OUT-1、OUT-2）
CREATE TABLE IF NOT EXISTS DEVICE_INFO (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    device_name      VARCHAR(200)  NOT NULL,
    device_type      VARCHAR(50)   DEFAULT 'REST',
    device_category  VARCHAR(20)   DEFAULT 'OUTPUT',
    model            VARCHAR(100)  DEFAULT '',
    serial_number    VARCHAR(100)  DEFAULT '',
    output_channel_1 VARCHAR(100)  DEFAULT '',
    output_channel_2 VARCHAR(100)  DEFAULT '',
    max_resolution   VARCHAR(50)   DEFAULT '1920x1080'
);

CREATE TABLE IF NOT EXISTS DEVICE_CAPABILITY (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    support_move     BOOLEAN       DEFAULT TRUE,
    support_resize   BOOLEAN       DEFAULT TRUE,
    support_overlay  BOOLEAN       DEFAULT TRUE,
    max_resolution   VARCHAR(50)   DEFAULT '1920x1080',
    output_channel_1 VARCHAR(100)  DEFAULT '',
    output_channel_2 VARCHAR(100)  DEFAULT ''
);
