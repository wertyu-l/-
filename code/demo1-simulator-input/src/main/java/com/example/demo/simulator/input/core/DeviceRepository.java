package com.example.demo.simulator.input.core;

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
                        "channel_count, input_channel_1, input_channel_2, input_channel_3, " +
                        "input_channel_4, input_channel_5, max_resolution FROM DEVICE_INFO LIMIT 1",
                (rs, rowNum) -> {
                    SimDeviceInfo info = new SimDeviceInfo();
                    info.setDeviceName(rs.getString("device_name"));
                    info.setDeviceType(rs.getString("device_type"));
                    info.setDeviceCategory(rs.getString("device_category"));
                    info.setModel(rs.getString("model"));
                    info.setSerialNumber(rs.getString("serial_number"));
                    info.setChannelCount(rs.getInt("channel_count"));
                    info.setInputChannel1(rs.getString("input_channel_1"));
                    info.setInputChannel2(rs.getString("input_channel_2"));
                    info.setInputChannel3(rs.getString("input_channel_3"));
                    info.setInputChannel4(rs.getString("input_channel_4"));
                    info.setInputChannel5(rs.getString("input_channel_5"));
                    info.setMaxResolution(rs.getString("max_resolution"));
                    return info;
                });
    }

    public SimDeviceCapability loadDeviceCapability() {
        return jdbc.queryForObject(
                "SELECT support_move, support_resize, support_overlay, max_resolution, " +
                        "channel_count, input_channel_1, input_channel_2, input_channel_3, " +
                        "input_channel_4, input_channel_5 FROM DEVICE_CAPABILITY LIMIT 1",
                (rs, rowNum) -> {
                    SimDeviceCapability cap = new SimDeviceCapability();
                    cap.setSupportMove(rs.getBoolean("support_move"));
                    cap.setSupportResize(rs.getBoolean("support_resize"));
                    cap.setSupportOverlay(rs.getBoolean("support_overlay"));
                    cap.setMaxResolution(rs.getString("max_resolution"));
                    cap.setChannelCount(rs.getInt("channel_count"));
                    cap.setInputChannel1(rs.getString("input_channel_1"));
                    cap.setInputChannel2(rs.getString("input_channel_2"));
                    cap.setInputChannel3(rs.getString("input_channel_3"));
                    cap.setInputChannel4(rs.getString("input_channel_4"));
                    cap.setInputChannel5(rs.getString("input_channel_5"));
                    return cap;
                });
    }

    public void updateCapability(SimDeviceCapability cap) {
        jdbc.update("UPDATE DEVICE_CAPABILITY SET support_move=?, support_resize=?, " +
                        "support_overlay=?, max_resolution=?, channel_count=?, " +
                        "input_channel_1=?, input_channel_2=?, input_channel_3=?, " +
                        "input_channel_4=?, input_channel_5=? WHERE id=1",
                cap.isSupportMove(), cap.isSupportResize(), cap.isSupportOverlay(),
                cap.getMaxResolution(), cap.getChannelCount(),
                cap.getInputChannel1(), cap.getInputChannel2(), cap.getInputChannel3(),
                cap.getInputChannel4(), cap.getInputChannel5());
    }

    public void updateDeviceInfoFromCapability(SimDeviceCapability cap) {
        jdbc.update("UPDATE DEVICE_INFO SET channel_count=?, " +
                        "input_channel_1=?, input_channel_2=?, input_channel_3=?, " +
                        "input_channel_4=?, input_channel_5=?, max_resolution=? WHERE id=1",
                cap.getChannelCount(),
                cap.getInputChannel1(), cap.getInputChannel2(), cap.getInputChannel3(),
                cap.getInputChannel4(), cap.getInputChannel5(), cap.getMaxResolution());
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