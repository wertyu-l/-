-- 输入设备实例1 (8086)
INSERT INTO DEVICE_INFO (device_name, device_type, device_category, model, serial_number,
                          channel_count, input_channel_1, max_resolution)
SELECT '输入设备-1', 'REST', 'INPUT', 'DS-D2055NH-A', 'SN-INPUT-001',
        1, 'HDMI-1', '1920x1080'
WHERE NOT EXISTS (SELECT 1 FROM DEVICE_INFO);

INSERT INTO DEVICE_CAPABILITY (support_move, support_resize, support_overlay, max_resolution,
                                channel_count, input_channel_1)
SELECT TRUE, TRUE, TRUE, '1920x1080', 1, 'HDMI-1'
WHERE NOT EXISTS (SELECT 1 FROM DEVICE_CAPABILITY);