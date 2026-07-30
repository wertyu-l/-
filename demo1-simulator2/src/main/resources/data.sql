-- =============================================
-- 模拟设备2 初始数据（仅当表为空时插入）
-- =============================================

-- 设备信息初始数据
INSERT INTO DEVICE_INFO (device_name, device_type, model, serial_number, output_channels, max_resolution)
SELECT 'REST-Node-02', 'REST', 'DS-D2055NH-B', 'SN-REST-2024-0002', 2, '1920x1080'
WHERE NOT EXISTS (SELECT 1 FROM DEVICE_INFO);

-- 设备能力初始数据
INSERT INTO DEVICE_CAPABILITY (max_windows, support_move, support_resize, support_overlay, max_resolution, output_channels)
SELECT 4, TRUE, TRUE, TRUE, '1920x1080', 2
WHERE NOT EXISTS (SELECT 1 FROM DEVICE_CAPABILITY);