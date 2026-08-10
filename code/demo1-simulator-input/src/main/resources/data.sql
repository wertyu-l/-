-- 输入设备模拟器 初始数据
INSERT INTO DEVICE_INFO (device_name, device_type, device_category, model, serial_number,
                          channel_count, input_channel_1, max_resolution)
SELECT 'REST-Node-01', 'REST', 'INPUT', 'DS-D2055NH-A', 'SN-REST-2024-0001',
       1, 'HDMI-1', '1920x1080'
WHERE NOT EXISTS (SELECT 1 FROM DEVICE_INFO);

INSERT INTO DEVICE_CAPABILITY (support_move, support_resize, support_overlay, max_resolution,
                                channel_count, input_channel_1)
SELECT TRUE, TRUE, TRUE, '1920x1080', 1, 'HDMI-1'
WHERE NOT EXISTS (SELECT 1 FROM DEVICE_CAPABILITY);