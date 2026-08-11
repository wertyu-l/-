-- 输出设备实例1 (8088)
INSERT INTO DEVICE_INFO (device_name, device_type, device_category, model, serial_number,
                          channel_count, max_windows, output_channel_1, output_channel_2, max_resolution)
SELECT '输出设备-1', 'REST', 'OUTPUT', 'DS-D2055NH-C', 'SN-OUTPUT-001',
        2, 4, 'OUT-1', 'OUT-2', '1920x1080'
WHERE NOT EXISTS (SELECT 1 FROM DEVICE_INFO);

INSERT INTO DEVICE_CAPABILITY (max_windows, support_move, support_resize, support_overlay, max_resolution,
                                channel_count, output_channel_1, output_channel_2)
SELECT 4, TRUE, TRUE, TRUE, '1920x1080', 2, 'OUT-1', 'OUT-2'
WHERE NOT EXISTS (SELECT 1 FROM DEVICE_CAPABILITY);