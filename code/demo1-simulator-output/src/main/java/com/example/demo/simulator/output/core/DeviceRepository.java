package com.example.demo.simulator.output.core;

import com.example.demo.model.SimDeviceCapability;
import com.example.demo.model.SimDeviceInfo;
import com.example.demo.model.SimWindow;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class DeviceRepository {

    private final JdbcTemplate jdbc;

    public DeviceRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public SimDeviceInfo loadDeviceInfo() {
        return jdbc.queryForObject(
                "SELECT device_name, device_type, device_category, model, serial_number, " +
                        "channel_count, max_windows, output_channel_1, output_channel_2, " +
                        "output_channel_3, output_channel_4, output_channel_5, max_resolution FROM DEVICE_INFO LIMIT 1",
                (rs, rowNum) -> {
                    SimDeviceInfo info = new SimDeviceInfo();
                    info.setDeviceName(rs.getString("device_name"));
                    info.setDeviceType(rs.getString("device_type"));
                    info.setDeviceCategory(rs.getString("device_category"));
                    info.setModel(rs.getString("model"));
                    info.setSerialNumber(rs.getString("serial_number"));
                    info.setChannelCount(rs.getInt("channel_count"));
                    info.setMaxWindows(rs.getInt("max_windows"));
                    info.setOutputChannel1(rs.getString("output_channel_1"));
                    info.setOutputChannel2(rs.getString("output_channel_2"));
                    info.setOutputChannel3(rs.getString("output_channel_3"));
                    info.setOutputChannel4(rs.getString("output_channel_4"));
                    info.setOutputChannel5(rs.getString("output_channel_5"));
                    info.setMaxResolution(rs.getString("max_resolution"));
                    return info;
                });
    }

    public SimDeviceCapability loadDeviceCapability() {
        return jdbc.queryForObject(
                "SELECT max_windows, support_move, support_resize, support_overlay, max_resolution, " +
                        "channel_count, output_channel_1, output_channel_2, output_channel_3, " +
                        "output_channel_4, output_channel_5 FROM DEVICE_CAPABILITY LIMIT 1",
                (rs, rowNum) -> {
                    SimDeviceCapability cap = new SimDeviceCapability();
                    cap.setMaxWindows(rs.getInt("max_windows"));
                    cap.setSupportMove(rs.getBoolean("support_move"));
                    cap.setSupportResize(rs.getBoolean("support_resize"));
                    cap.setSupportOverlay(rs.getBoolean("support_overlay"));
                    cap.setMaxResolution(rs.getString("max_resolution"));
                    cap.setChannelCount(rs.getInt("channel_count"));
                    cap.setOutputChannel1(rs.getString("output_channel_1"));
                    cap.setOutputChannel2(rs.getString("output_channel_2"));
                    cap.setOutputChannel3(rs.getString("output_channel_3"));
                    cap.setOutputChannel4(rs.getString("output_channel_4"));
                    cap.setOutputChannel5(rs.getString("output_channel_5"));
                    return cap;
                });
    }

    public void updateCapability(SimDeviceCapability cap) {
        jdbc.update("UPDATE DEVICE_CAPABILITY SET max_windows=?, support_move=?, support_resize=?, " +
                        "support_overlay=?, max_resolution=?, channel_count=?, " +
                        "output_channel_1=?, output_channel_2=?, output_channel_3=?, " +
                        "output_channel_4=?, output_channel_5=? WHERE id=1",
                cap.getMaxWindows(),
                cap.isSupportMove(), cap.isSupportResize(), cap.isSupportOverlay(),
                cap.getMaxResolution(), cap.getChannelCount(),
                cap.getOutputChannel1(), cap.getOutputChannel2(), cap.getOutputChannel3(),
                cap.getOutputChannel4(), cap.getOutputChannel5());
    }

    public void updateDeviceInfoFromCapability(SimDeviceCapability cap) {
        jdbc.update("UPDATE DEVICE_INFO SET channel_count=?, max_windows=?, " +
                        "output_channel_1=?, output_channel_2=?, output_channel_3=?, " +
                        "output_channel_4=?, output_channel_5=?, max_resolution=? WHERE id=1",
                cap.getChannelCount(), cap.getMaxWindows(),
                cap.getOutputChannel1(), cap.getOutputChannel2(), cap.getOutputChannel3(),
                cap.getOutputChannel4(), cap.getOutputChannel5(), cap.getMaxResolution());
    }

    private final ConcurrentHashMap<String, SimWindow> windowMap = new ConcurrentHashMap<>();

    public List<SimWindow> findAllWindows() {
        return new ArrayList<>(windowMap.values());
    }

    public SimWindow findWindowById(String windowId) {
        for (SimWindow w : windowMap.values()) {
            if (w.getWindowId().equals(windowId)) {
                return w;
            }
        }
        return null;
    }

    public SimWindow findByWindowIdAndChannel(String windowId, String channelName) {
        return windowMap.get(windowId + "|" + channelName);
    }

    public int countWindows() {
        return windowMap.size();
    }

    public void insertWindow(SimWindow w) {
        windowMap.put(w.getWindowId() + "|" + w.getChannelName(), w);
    }

    public void updateWindow(SimWindow w) {
        windowMap.replace(w.getWindowId() + "|" + w.getChannelName(), w);
    }

    public boolean deleteWindow(String windowId) {
        boolean removed = false;
        Iterator<Map.Entry<String, SimWindow>> it = windowMap.entrySet().iterator();
        while (it.hasNext()) {
            if (it.next().getValue().getWindowId().equals(windowId)) {
                it.remove();
                removed = true;
            }
        }
        return removed;
    }

    private final ConcurrentHashMap<String, String> channelUrlMap = new ConcurrentHashMap<>();

    public void setChannelUrl(String channelName, String sourceUrl) {
        channelUrlMap.put(channelName, sourceUrl);
    }

    public String getChannelUrl(String channelName) {
        return channelUrlMap.getOrDefault(channelName, "");
    }

    public Map<String, String> getAllChannelUrls() {
        return new java.util.LinkedHashMap<>(channelUrlMap);
    }
}