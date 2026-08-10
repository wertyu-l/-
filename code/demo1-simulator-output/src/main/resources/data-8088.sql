-- 输出设备实例1 (8088)
DELETE FROM DEVICE_CAPABILITY;
DELETE FROM DEVICE_INFO;

INSERT INTO DEVICE_INFO (device_name, device_type, device_category, model, serial_number,
                          channel_count, max_windows, output_channel_1, output_channel_2, max_resolution)
VALUES ('输出设备-1', 'REST', 'OUTPUT', 'DS-D2055NH-C', 'SN-OUTPUT-001',
        2, 4, 'OUT-1', 'OUT-2', '1920x1080');

INSERT INTO DEVICE_CAPABILITY (max_windows, support_move, support_resize, support_overlay, max_resolution,
                                channel_count, output_channel_1, output_channel_2)
VALUES (4, TRUE, TRUE, TRUE, '1920x1080', 2, 'OUT-1', 'OUT-2');