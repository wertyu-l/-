package com.example.demo.simulator3.core;

import com.example.demo.model.SimDeviceCapability;
import com.example.demo.model.SimDeviceInfo;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 模拟设备3 数据访问层（输出设备，2个输出通道）
 */
@Repository
public class DeviceRepository {

    private final JdbcTemplate jdbc;

    public DeviceRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public SimDeviceInfo loadDeviceInfo() {
        return jdbc.queryForObject(
                "SELECT device_name, device_type, device_category, model, serial_number, " +
                        "output_channel_1, output_channel_2, max_resolution FROM DEVICE_INFO LIMIT 1",
                (rs, rowNum) -> {
                    SimDeviceInfo info = new SimDeviceInfo();
                    info.setDeviceName(rs.getString("device_name"));
                    info.setDeviceType(rs.getString("device_type"));
                    info.setDeviceCategory(rs.getString("device_category"));
                    info.setModel(rs.getString("model"));
                    info.setSerialNumber(rs.getString("serial_number"));
                    info.setOutputChannel1(rs.getString("output_channel_1"));
                    info.setOutputChannel2(rs.getString("output_channel_2"));
                    info.setMaxResolution(rs.getString("max_resolution"));
                    return info;
                });
    }

    public SimDeviceCapability loadDeviceCapability() {
        return jdbc.queryForObject(
                "SELECT support_move, support_resize, support_overlay, max_resolution, " +
                        "output_channel_1, output_channel_2 FROM DEVICE_CAPABILITY LIMIT 1",
                (rs, rowNum) -> {
                    SimDeviceCapability cap = new SimDeviceCapability();
                    cap.setSupportMove(rs.getBoolean("support_move"));
                    cap.setSupportResize(rs.getBoolean("support_resize"));
                    cap.setSupportOverlay(rs.getBoolean("support_overlay"));
                    cap.setMaxResolution(rs.getString("max_resolution"));
                    cap.setOutputChannel1(rs.getString("output_channel_1"));
                    cap.setOutputChannel2(rs.getString("output_channel_2"));
                    return cap;
                });
    }

    public void updateCapability(SimDeviceCapability cap) {
        jdbc.update("UPDATE DEVICE_CAPABILITY SET support_move=?, support_resize=?, " +
                        "support_overlay=?, max_resolution=?, output_channel_1=?, output_channel_2=? WHERE id=1",
                cap.isSupportMove(), cap.isSupportResize(), cap.isSupportOverlay(),
                cap.getMaxResolution(), cap.getOutputChannel1(), cap.getOutputChannel2());
    }

    public void updateDeviceInfoFromCapability(SimDeviceCapability cap) {
        jdbc.update("UPDATE DEVICE_INFO SET output_channel_1=?, output_channel_2=?, max_resolution=? WHERE id=1",
                cap.getOutputChannel1(), cap.getOutputChannel2(), cap.getMaxResolution());
    }
}