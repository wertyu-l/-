package com.example.demo.simulator.tlv.output.core;

import com.example.demo.model.SimDeviceCapability;
import com.example.demo.model.SimDeviceInfo;
import com.example.demo.model.SimDeviceStatus;
import com.example.demo.model.SimWindow;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 输出设备核心管理器。
 * <p>
 * 输出设备用于大屏绑定显示，拥有窗口的完整增删改查，窗口绑定到其输出通道。
 * 窗口数据不持久化，进程重启后丢失，由管控系统重新推送。
 */
@Component
public class SimDeviceManager {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final DeviceConfig config;
    private final LocalDateTime startTime = LocalDateTime.now();

    /** windowId@channelName → 窗口详情（不持久化，同一 windowId 可跨多通道） */
    private final ConcurrentHashMap<String, SimWindow> windows = new ConcurrentHashMap<>();

    private static String key(String windowId, String channelName) {
        return windowId + "@" + (channelName != null ? channelName : "");
    }

    public SimDeviceManager(DeviceConfig config) {
        this.config = config;
    }

    public SimDeviceInfo getDeviceInfo() {
        return config.getDeviceInfo();
    }

    public SimDeviceCapability getDeviceCapability() {
        return config.getDeviceCapability();
    }

    /** 设备运行状态：在线、当前窗口数、启动时间 */
    public SimDeviceStatus getDeviceStatus() {
        SimDeviceStatus status = new SimDeviceStatus();
        status.setOnline(true);
        status.setWindowCount(windows.size());
        status.setUptime(startTime.format(FMT));
        return status;
    }

    /** 收集设备已定义的输出通道名（非空） */
    public List<String> getOutputChannels() {
        SimDeviceInfo info = getDeviceInfo();
        List<String> channels = new ArrayList<>();
        addIfNotEmpty(channels, info.getOutputChannel1());
        addIfNotEmpty(channels, info.getOutputChannel2());
        addIfNotEmpty(channels, info.getOutputChannel3());
        addIfNotEmpty(channels, info.getOutputChannel4());
        addIfNotEmpty(channels, info.getOutputChannel5());
        return channels;
    }

    /** 校验输出通道名是否有效 */
    public boolean isValidOutputChannel(String channelName) {
        if (channelName == null || channelName.isEmpty()) {
            return false;
        }
        return channelName.equals(getDeviceInfo().getOutputChannel1())
                || channelName.equals(getDeviceInfo().getOutputChannel2())
                || channelName.equals(getDeviceInfo().getOutputChannel3())
                || channelName.equals(getDeviceInfo().getOutputChannel4())
                || channelName.equals(getDeviceInfo().getOutputChannel5());
    }

    /**
     * 创建窗口。
     * <p>
     * 校验 windowId 唯一、通道有效、窗口总数未超过 maxWindows；
     * 校验失败抛出 {@link IllegalArgumentException}（消息为错误描述）。
     */
    public SimWindow createWindow(String windowId, String channelName,
                                  Integer x, Integer y, Integer width, Integer height,
                                  String sourceType, String sourceUrl) {
        if (windowId == null || windowId.isEmpty()) {
            throw new IllegalArgumentException("窗口ID不能为空");
        }
        if (!isValidOutputChannel(channelName)) {
            throw new IllegalArgumentException("通道名无效: " + channelName);
        }
        String k = key(windowId, channelName);
        if (windows.containsKey(k)) {
            throw new IllegalArgumentException("窗口已存在: " + windowId + " / " + channelName);
        }
        int max = getDeviceCapability().getMaxWindows();
        if (max > 0 && windows.size() >= max) {
            throw new IllegalArgumentException("窗口数量已达上限");
        }

        SimWindow w = new SimWindow();
        w.setWindowId(windowId);
        w.setChannelName(channelName);
        w.setX(x == null ? 0 : x);
        w.setY(y == null ? 0 : y);
        w.setWidth(width == null ? 1920 : width);
        w.setHeight(height == null ? 1080 : height);
        w.setSourceType(sourceType != null ? sourceType : inferSourceType(channelName));
        w.setSourceUrl(sourceUrl != null ? sourceUrl : "");
        w.setCreateTime(LocalDateTime.now().format(FMT));
        windows.put(k, w);
        return w;
    }

    /**
     * 更新窗口（仅更新传入字段，未传字段保持原值不变）。
     * <p>
     * 窗口不存在时抛出 {@link IllegalArgumentException}。
     */
    public SimWindow updateWindow(String windowId, String channelName,
                                  Integer x, Integer y, Integer width, Integer height) {
        String k = key(windowId, channelName);
        SimWindow w = windows.get(k);
        if (w == null) {
            // 未指定通道名时，找第一个匹配的窗口
            if (channelName == null || channelName.isEmpty()) {
                for (java.util.Map.Entry<String, SimWindow> e : windows.entrySet()) {
                    if (e.getKey().startsWith(windowId + "@")) {
                        w = e.getValue();
                        break;
                    }
                }
            }
            if (w == null) {
                throw new IllegalArgumentException("窗口不存在: " + windowId);
            }
        }
        if (channelName != null && !channelName.isEmpty()) {
            if (!isValidOutputChannel(channelName)) {
                throw new IllegalArgumentException("通道名无效: " + channelName);
            }
            w.setChannelName(channelName);
            w.setSourceType(inferSourceType(channelName));
        }
        if (x != null) w.setX(x);
        if (y != null) w.setY(y);
        if (width != null) w.setWidth(width);
        if (height != null) w.setHeight(height);
        windows.put(k, w);
        return w;
    }

    /**
     * 关闭窗口（删除该 windowId 下所有通道的子窗口）。
     * 窗口不存在时抛出 {@link IllegalArgumentException}。
     */
    public SimWindow closeWindow(String windowId) {
        String prefix = windowId + "@";
        SimWindow removed = null;
        java.util.Iterator<java.util.Map.Entry<String, SimWindow>> it = windows.entrySet().iterator();
        while (it.hasNext()) {
            java.util.Map.Entry<String, SimWindow> e = it.next();
            if (e.getKey().startsWith(prefix)) {
                if (removed == null) {
                    removed = e.getValue();
                }
                it.remove();
            }
        }
        if (removed == null) {
            throw new IllegalArgumentException("窗口不存在: " + windowId);
        }
        return removed;
    }

    /** 查询单个窗口（按 windowId 查找第一个匹配） */
    public SimWindow findWindow(String windowId) {
        String prefix = windowId + "@";
        for (java.util.Map.Entry<String, SimWindow> e : windows.entrySet()) {
            if (e.getKey().startsWith(prefix)) {
                return e.getValue();
            }
        }
        return null;
    }

    /** 查询所有窗口 */
    public List<SimWindow> getWindows() {
        return new ArrayList<>(windows.values());
    }

    private void addIfNotEmpty(List<String> list, String s) {
        if (s != null && !s.isEmpty()) {
            list.add(s);
        }
    }

    private String inferSourceType(String channelName) {
        if (channelName == null) return "";
        String upper = channelName.toUpperCase();
        if (upper.startsWith("HDMI")) return "HDMI";
        if (upper.startsWith("VGA")) return "VGA";
        if (upper.startsWith("DP")) return "DP";
        if (upper.startsWith("SDI")) return "SDI";
        return "Stream";
    }

}