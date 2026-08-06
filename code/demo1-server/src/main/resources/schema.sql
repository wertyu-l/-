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
-- device_category: INPUT=输入设备(拥有输入通道), OUTPUT=输出设备(拥有输出通道)
-- 输入设备：input_channel_1/2 有值，output_channel_1/2 为空
-- 输出设备：output_channel_1/2 有值，input_channel_1/2 为空
-- =============================================
CREATE TABLE IF NOT EXISTS DEVICE (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    device_name      VARCHAR(200)  NOT NULL,
    device_type      VARCHAR(50)   DEFAULT 'REST',
    device_category  VARCHAR(20)   NOT NULL DEFAULT 'INPUT',
    model            VARCHAR(100)  DEFAULT '',
    serial_number    VARCHAR(100)  DEFAULT '',
    input_channel_1  VARCHAR(100)  DEFAULT '',
    input_channel_2  VARCHAR(100)  DEFAULT '',
    output_channel_1 VARCHAR(100)  DEFAULT '',
    output_channel_2 VARCHAR(100)  DEFAULT '',
    output_channel_3 VARCHAR(100)  DEFAULT '',
    max_resolution   VARCHAR(50)   DEFAULT '1920x1080',
    max_windows      INT           DEFAULT 4,
    support_move     INT           DEFAULT 1,
    support_resize   INT           DEFAULT 1,
    support_overlay  INT           DEFAULT 1,
    base_url         VARCHAR(300)  NOT NULL UNIQUE,
    enabled          INT           DEFAULT 1,
    online           INT           DEFAULT 0,
    last_heartbeat   TIMESTAMP     NULL,
    create_time      TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    update_time      TIMESTAMP     DEFAULT CURRENT_TIMESTAMP
);

-- =============================================
-- 大屏配置模块
-- =============================================
CREATE TABLE IF NOT EXISTS SCREEN (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    screen_name     VARCHAR(200)  NOT NULL,
    rows_count      INT           NOT NULL DEFAULT 1,
    cols_count      INT           NOT NULL DEFAULT 1,
    cell_width      INT           NOT NULL DEFAULT 1920,
    cell_height     INT           NOT NULL DEFAULT 1080,
    create_time     TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP     DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS SCREEN_CELL (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    screen_id       BIGINT        NOT NULL,
    row_index       INT           NOT NULL DEFAULT 0,
    col_index       INT           NOT NULL DEFAULT 0,
    device_id       BIGINT        NULL,
    channel_name    VARCHAR(100)  NOT NULL DEFAULT '',
    CONSTRAINT fk_cell_screen FOREIGN KEY (screen_id) REFERENCES SCREEN(id) ON DELETE CASCADE
);

-- =============================================
-- 前台模块 — 窗口表
-- =============================================
CREATE TABLE IF NOT EXISTS SCREEN_WINDOW (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    window_id       VARCHAR(100)  NOT NULL,
    screen_id       BIGINT        NOT NULL,
    device_id       BIGINT        NOT NULL,
    channel_name    VARCHAR(100)  NOT NULL,
    x               INT           NOT NULL DEFAULT 0,
    y               INT           NOT NULL DEFAULT 0,
    width           INT           NOT NULL DEFAULT 1920,
    height          INT           NOT NULL DEFAULT 1080,
    source_type     VARCHAR(50)   DEFAULT '',
    source_url      VARCHAR(500)  DEFAULT '',
    sync_status     VARCHAR(20)   NOT NULL DEFAULT 'synced',
    degraded        INT           NOT NULL DEFAULT 0,
    create_time     TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_window_screen FOREIGN KEY (screen_id) REFERENCES SCREEN(id) ON DELETE CASCADE
);