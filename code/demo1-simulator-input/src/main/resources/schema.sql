-- 输入设备模拟器 数据库
CREATE TABLE IF NOT EXISTS DEVICE_INFO (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    device_name      VARCHAR(200)  NOT NULL,
    device_type      VARCHAR(50)   DEFAULT 'REST',
    device_category  VARCHAR(20)   DEFAULT 'INPUT',
    model            VARCHAR(100)  DEFAULT '',
    serial_number    VARCHAR(100)  DEFAULT '',
    channel_count    INT           DEFAULT 1,
    input_channel_1  VARCHAR(100)  DEFAULT '',
    input_channel_2  VARCHAR(100)  DEFAULT '',
    input_channel_3  VARCHAR(100)  DEFAULT '',
    input_channel_4  VARCHAR(100)  DEFAULT '',
    input_channel_5  VARCHAR(100)  DEFAULT '',
    max_resolution   VARCHAR(50)   DEFAULT '1920x1080'
);

CREATE TABLE IF NOT EXISTS DEVICE_CAPABILITY (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    max_resolution   VARCHAR(50)   DEFAULT '1920x1080',
    channel_count    INT           DEFAULT 1,
    input_channel_1  VARCHAR(100)  DEFAULT '',
    input_channel_2  VARCHAR(100)  DEFAULT '',
    input_channel_3  VARCHAR(100)  DEFAULT '',
    input_channel_4  VARCHAR(100)  DEFAULT '',
    input_channel_5  VARCHAR(100)  DEFAULT ''
);