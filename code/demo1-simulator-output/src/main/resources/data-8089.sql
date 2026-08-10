-- 输出设备实例2 (8089)
DELETE FROM DEVICE_CAPABILITY;
DELETE FROM DEVICE_INFO;

INSERT INTO DEVICE_INFO (device_name, device_type, device_category, model, serial_number,
                          channel_count, max_windows, output_channel_1, output_channel_2, output_channel_3, max_resolution)
VALUES ('输出设备-2', 'REST', 'OUTPUT', 'DS-D2055NH-D', 'SN-OUTPUT-002',
        3, 6, 'OUT-1', 'OUT-2', 'OUT-3', '1920x1080');

INSERT INTO DEVICE_CAPABILITY (max_windows, support_move, support_resize, support_overlay, max_resolution,
                                channel_count, output_channel_1, output_channel_2, output_channel_3)
VALUES (6, TRUE, TRUE, TRUE, '1920x1080', 3, 'OUT-1', 'OUT-2', 'OUT-3');