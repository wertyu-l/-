-- 模拟设备3 初始数据 — 输出设备（2个输出通道 OUT-1、OUT-2）
INSERT INTO DEVICE_INFO (device_name, device_type, device_category, model, serial_number,
                          output_channel_1, output_channel_2, max_resolution)
SELECT 'REST-Node-03', 'REST', 'OUTPUT', 'DS-D2055NH-C', 'SN-REST-2024-0003',
       'OUT-1', 'OUT-2', '1920x1080'
WHERE NOT EXISTS (SELECT 1 FROM DEVICE_INFO);

INSERT INTO DEVICE_CAPABILITY (support_move, support_resize, support_overlay, max_resolution,
                                output_channel_1, output_channel_2)
SELECT TRUE, TRUE, TRUE, '1920x1080', 'OUT-1', 'OUT-2'
WHERE NOT EXISTS (SELECT 1 FROM DEVICE_CAPABILITY);