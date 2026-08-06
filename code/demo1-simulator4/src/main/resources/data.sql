-- 模拟设备4 初始数据 — 输出设备（3个输出通道 OUT-1、OUT-2、OUT-3）
INSERT INTO DEVICE_INFO (device_name, device_type, device_category, model, serial_number,
                          output_channel_1, output_channel_2, output_channel_3, max_resolution)
SELECT 'REST-Node-04', 'REST', 'OUTPUT', 'DS-D2055NH-D', 'SN-REST-2024-0004',
       'OUT-1', 'OUT-2', 'OUT-3', '1920x1080'
WHERE NOT EXISTS (SELECT 1 FROM DEVICE_INFO);

INSERT INTO DEVICE_CAPABILITY (support_move, support_resize, support_overlay, max_resolution,
                                output_channel_1, output_channel_2, output_channel_3)
SELECT TRUE, TRUE, TRUE, '1920x1080', 'OUT-1', 'OUT-2', 'OUT-3'
WHERE NOT EXISTS (SELECT 1 FROM DEVICE_CAPABILITY);
