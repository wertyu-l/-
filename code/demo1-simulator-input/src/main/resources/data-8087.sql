-- 输入设备实例2 (8087)
DELETE FROM DEVICE_CAPABILITY;
DELETE FROM DEVICE_INFO;

INSERT INTO DEVICE_INFO (device_name, device_type, device_category, model, serial_number,
                          channel_count, input_channel_1, input_channel_2, max_resolution)
VALUES ('输入设备-2', 'REST', 'INPUT', 'DS-D2055NH-B', 'SN-INPUT-002',
        2, 'HDMI-1', 'HDMI-2', '1920x1080');

INSERT INTO DEVICE_CAPABILITY (support_move, support_resize, support_overlay, max_resolution,
                                channel_count, input_channel_1, input_channel_2)
VALUES (TRUE, TRUE, TRUE, '1920x1080', 2, 'HDMI-1', 'HDMI-2');