-- 模拟设备1 初始数据 — 输入设备（1个输入通道 HDMI-1）
INSERT INTO DEVICE_INFO (device_name, device_type, device_category, model, serial_number,
                          input_channel_1, max_resolution)
SELECT 'REST-Node-01', 'REST', 'INPUT', 'DS-D2055NH-A', 'SN-REST-2024-0001',
       'HDMI-1', '1920x1080'
WHERE NOT EXISTS (SELECT 1 FROM DEVICE_INFO);

INSERT INTO DEVICE_CAPABILITY (support_move, support_resize, support_overlay, max_resolution,
                                input_channel_1)
SELECT TRUE, TRUE, TRUE, '1920x1080', 'HDMI-1'
WHERE NOT EXISTS (SELECT 1 FROM DEVICE_CAPABILITY);