package com.example.demo.simulator.core;

import com.example.demo.model.SimDeviceCapability;
import com.example.demo.model.SimDeviceInfo;
import com.example.demo.model.SimWindow;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 模拟设备1 数据访问层（输入设备，1个输入通道 HDMI-1）
 */
@Repository
public class DeviceRepository {

    private final JdbcTemplate jdbc;

    public DeviceRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public SimDeviceInfo loadDeviceInfo() {
        return jdbc.queryForObject(
                "SELECT device_name, device_type, device_category, model, serial_number, " +
                        "input_channel_1, max_resolution FROM DEVICE_INFO LIMIT 1",
                (rs, rowNum) -> {
                    SimDeviceInfo info = new SimDeviceInfo();
                    info.setDeviceName(rs.getString("device_name"));
                    info.setDeviceType(rs.getString("device_type"));
                    info.setDeviceCategory(rs.getString("device_category"));
                    info.setModel(rs.getString("model"));
                    info.setSerialNumber(rs.getString("serial_number"));
                    info.setInputChannel1(rs.getString("input_channel_1"));
                    info.setMaxResolution(rs.getString("max_resolution"));
                    return info;
                });
    }

    public SimDeviceCapability loadDeviceCapability() {
        return jdbc.queryForObject(
                "SELECT support_move, support_resize, support_overlay, max_resolution, " +
                        "input_channel_1 FROM DEVICE_CAPABILITY LIMIT 1",
                (rs, rowNum) -> {
                    SimDeviceCapability cap = new SimDeviceCapability();
                    cap.setSupportMove(rs.getBoolean("support_move"));
                    cap.setSupportResize(rs.getBoolean("support_resize"));
                    cap.setSupportOverlay(rs.getBoolean("support_overlay"));
                    cap.setMaxResolution(rs.getString("max_resolution"));
                    cap.setInputChannel1(rs.getString("input_channel_1"));
                    return cap;
                });
    }

    public void updateCapability(SimDeviceCapability cap) {
        jdbc.update("UPDATE DEVICE_CAPABILITY SET support_move=?, support_resize=?, " +
                        "support_overlay=?, max_resolution=?, input_channel_1=? WHERE id=1",
                cap.isSupportMove(), cap.isSupportResize(), cap.isSupportOverlay(),
                cap.getMaxResolution(), cap.getInputChannel1());
    }

    public void updateDeviceInfoFromCapability(SimDeviceCapability cap) {
        jdbc.update("UPDATE DEVICE_INFO SET input_channel_1=?, max_resolution=? WHERE id=1",
                cap.getInputChannel1(), cap.getMaxResolution());
    }

    // ---- 窗口 ----
    public List<SimWindow> findAllWindows() {
        return jdbc.query("SELECT window_id, channel_name, x, y, width, height, " +
                        "source_type, source_url, create_time FROM DEVICE_WINDOW",
                (rs, rowNum) -> {
                    SimWindow w = new SimWindow();
                    w.setWindowId(rs.getString("window_id"));
                    w.setChannelName(rs.getString("channel_name"));
                    w.setX(rs.getInt("x")); w.setY(rs.getInt("y"));
                    w.setWidth(rs.getInt("width")); w.setHeight(rs.getInt("height"));
                    w.setSourceType(rs.getString("source_type"));
                    w.setSourceUrl(rs.getString("source_url"));
                    w.setCreateTime(rs.getString("create_time"));
                    return w;
                });
    }

    public SimWindow findWindowById(String windowId) {
        List<SimWindow> list = jdbc.query(
                "SELECT window_id, channel_name, x, y, width, height, " +
                        "source_type, source_url, create_time FROM DEVICE_WINDOW WHERE window_id=?",
                (rs, rowNum) -> {
                    SimWindow w = new SimWindow();
                    w.setWindowId(rs.getString("window_id"));
                    w.setChannelName(rs.getString("channel_name"));
                    w.setX(rs.getInt("x")); w.setY(rs.getInt("y"));
                    w.setWidth(rs.getInt("width")); w.setHeight(rs.getInt("height"));
                    w.setSourceType(rs.getString("source_type"));
                    w.setSourceUrl(rs.getString("source_url"));
                    w.setCreateTime(rs.getString("create_time"));
                    return w;
                }, windowId);
        return list.isEmpty() ? null : list.get(0);
    }

    public int countWindows() {
        Integer c = jdbc.queryForObject("SELECT COUNT(*) FROM DEVICE_WINDOW", Integer.class);
        return c != null ? c : 0;
    }

    public void insertWindow(SimWindow w) {
        jdbc.update("INSERT INTO DEVICE_WINDOW (window_id, channel_name, x, y, width, height, " +
                        "source_type, source_url, create_time) VALUES (?,?,?,?,?,?,?,?,?)",
                w.getWindowId(), w.getChannelName(),
                w.getX() != null ? w.getX() : 0, w.getY() != null ? w.getY() : 0,
                w.getWidth() != null ? w.getWidth() : 1920, w.getHeight() != null ? w.getHeight() : 1080,
                w.getSourceType() != null ? w.getSourceType() : "",
                w.getSourceUrl() != null ? w.getSourceUrl() : "", w.getCreateTime());
    }

    public void updateWindow(SimWindow w) {
        jdbc.update("UPDATE DEVICE_WINDOW SET x=?, y=?, width=?, height=? WHERE window_id=?",
                w.getX(), w.getY(), w.getWidth(), w.getHeight(), w.getWindowId());
    }

    public boolean deleteWindow(String windowId) {
        return jdbc.update("DELETE FROM DEVICE_WINDOW WHERE window_id=?", windowId) > 0;
    }
}