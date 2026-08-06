-- 模拟设备2 初始数据 — 输入设备（2个输入通道 HDMI-1、HDMI-2）
INSERT INTO DEVICE_INFO (device_name, device_type, device_category, model, serial_number,
                          input_channel_1, input_channel_2, max_resolution)
SELECT 'REST-Node-02', 'REST', 'INPUT', 'DS-D2055NH-B', 'SN-REST-2024-0002',
       'HDMI-1', 'HDMI-2', '1920x1080'
WHERE NOT EXISTS (SELECT 1 FROM DEVICE_INFO);

INSERT INTO DEVICE_CAPABILITY (support_move, support_resize, support_overlay, max_resolution,
                                input_channel_1, input_channel_2)
SELECT TRUE, TRUE, TRUE, '1920x1080', 'HDMI-1', 'HDMI-2'
WHERE NOT EXISTS (SELECT 1 FROM DEVICE_CAPABILITY);