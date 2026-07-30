package com.example.demo.simulator.core;

import com.example.demo.model.SimDeviceCapability;
import com.example.demo.model.SimDeviceInfo;
import com.example.demo.model.SimWindow;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 模拟设备数据访问层
 * <p>
 * 使用 JdbcTemplate 操作 H2 数据库，管理设备信息、能力和窗口的持久化。
 * 一个进程 = 一台设备，DEVICE_INFO 和 DEVICE_CAPABILITY 表中只有一条记录。
 */
@Repository
public class DeviceRepository {

    private final JdbcTemplate jdbc;

    public DeviceRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 加载设备基本信息（表中唯一一条记录）
     */
    public SimDeviceInfo loadDeviceInfo() {
        return jdbc.queryForObject(
                "SELECT device_name, device_type, model, serial_number, " +
                        "output_channels, max_resolution FROM DEVICE_INFO LIMIT 1",
                (rs, rowNum) -> {
                    SimDeviceInfo info = new SimDeviceInfo();
                    info.setDeviceName(rs.getString("device_name"));
                    info.setDeviceType(rs.getString("device_type"));
                    info.setModel(rs.getString("model"));
                    info.setSerialNumber(rs.getString("serial_number"));
                    info.setOutputChannels(rs.getInt("output_channels"));
                    info.setMaxResolution(rs.getString("max_resolution"));
                    return info;
                });
    }


    /**
     * 加载设备能力（表中唯一一条记录）
     */
    public SimDeviceCapability loadDeviceCapability() {
        return jdbc.queryForObject(
                "SELECT max_windows, support_move, support_resize, support_overlay, " +
                        "max_resolution, output_channels FROM DEVICE_CAPABILITY LIMIT 1",
                (rs, rowNum) -> {
                    SimDeviceCapability cap = new SimDeviceCapability();
                    cap.setMaxWindows(rs.getInt("max_windows"));
                    cap.setSupportMove(rs.getBoolean("support_move"));
                    cap.setSupportResize(rs.getBoolean("support_resize"));
                    cap.setSupportOverlay(rs.getBoolean("support_overlay"));
                    cap.setMaxResolution(rs.getString("max_resolution"));
                    cap.setOutputChannels(rs.getInt("output_channels"));
                    return cap;
                });
    }

    /**
     * 更新设备能力（只有一条记录，按 id=1 更新）
     */
    public void updateCapability(SimDeviceCapability cap) {
        jdbc.update(
                "UPDATE DEVICE_CAPABILITY SET max_windows=?, support_move=?, support_resize=?, " +
                        "support_overlay=?, max_resolution=?, output_channels=? WHERE id=1",
                cap.getMaxWindows(), cap.isSupportMove(), cap.isSupportResize(),
                cap.isSupportOverlay(), cap.getMaxResolution(), cap.getOutputChannels());
    }

    /**
     * 查询所有窗口
     */
    public List<SimWindow> findAllWindows() {
        return jdbc.query(
                "SELECT window_id, channel, x, y, width, height, " +
                        "source_type, source_url, create_time FROM DEVICE_WINDOW",
                (rs, rowNum) -> {
                    SimWindow w = new SimWindow();
                    w.setWindowId(rs.getString("window_id"));
                    w.setChannel(rs.getInt("channel"));
                    w.setX(rs.getInt("x"));
                    w.setY(rs.getInt("y"));
                    w.setWidth(rs.getInt("width"));
                    w.setHeight(rs.getInt("height"));
                    w.setSourceType(rs.getString("source_type"));
                    w.setSourceUrl(rs.getString("source_url"));
                    w.setCreateTime(rs.getString("create_time"));
                    return w;
                });
    }

    /**
     * 按 windowId 查询单个窗口
     */
    public SimWindow findWindowById(String windowId) {
        List<SimWindow> list = jdbc.query(
                "SELECT window_id, channel, x, y, width, height, " +
                        "source_type, source_url, create_time FROM DEVICE_WINDOW WHERE window_id=?",
                (rs, rowNum) -> {
                    SimWindow w = new SimWindow();
                    w.setWindowId(rs.getString("window_id"));
                    w.setChannel(rs.getInt("channel"));
                    w.setX(rs.getInt("x"));
                    w.setY(rs.getInt("y"));
                    w.setWidth(rs.getInt("width"));
                    w.setHeight(rs.getInt("height"));
                    w.setSourceType(rs.getString("source_type"));
                    w.setSourceUrl(rs.getString("source_url"));
                    w.setCreateTime(rs.getString("create_time"));
                    return w;
                },
                windowId);
        return list.isEmpty() ? null : list.get(0);
    }

    /**
     * 检查 channel 是否已被占用
     */
    public boolean isChannelUsed(int channel) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM DEVICE_WINDOW WHERE channel=?", Integer.class, channel);
        return count != null && count > 0;
    }

    /**
     * 查询窗口总数
     */
    public int countWindows() {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM DEVICE_WINDOW", Integer.class);
        return count != null ? count : 0;
    }

    /**
     * 新增窗口
     */
    public void insertWindow(SimWindow w) {
        jdbc.update(
                "INSERT INTO DEVICE_WINDOW (window_id, channel, x, y, width, height, " +
                        "source_type, source_url, create_time) VALUES (?,?,?,?,?,?,?,?,?)",
                w.getWindowId(), w.getChannel(),
                w.getX() != null ? w.getX() : 0,
                w.getY() != null ? w.getY() : 0,
                w.getWidth() != null ? w.getWidth() : 1920,
                w.getHeight() != null ? w.getHeight() : 1080,
                w.getSourceType() != null ? w.getSourceType() : "",
                w.getSourceUrl() != null ? w.getSourceUrl() : "",
                w.getCreateTime());
    }

    /**
     * 更新窗口位置/大小
     */
    public void updateWindow(SimWindow w) {
        jdbc.update(
                "UPDATE DEVICE_WINDOW SET x=?, y=?, width=?, height=? WHERE window_id=?",
                w.getX(), w.getY(), w.getWidth(), w.getHeight(), w.getWindowId());
    }

    /**
     * 删除窗口
     */
    public boolean deleteWindow(String windowId) {
        return jdbc.update("DELETE FROM DEVICE_WINDOW WHERE window_id=?", windowId) > 0;
    }

}