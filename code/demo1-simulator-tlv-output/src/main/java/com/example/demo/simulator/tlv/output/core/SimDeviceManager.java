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

    /** windowId → 窗口详情（不持久化） */
    private final ConcurrentHashMap<String, SimWindow> windows = new ConcurrentHashMap<>();

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
                                  Integer x, Integer y, Integer width, Integer height) {
        if (windowId == null || windowId.isEmpty()) {
            throw new IllegalArgumentException("窗口ID不能为空");
        }
        if (!isValidOutputChannel(channelName)) {
            throw new IllegalArgumentException("通道名无效: " + channelName);
        }
        if (windows.containsKey(windowId)) {
            throw new IllegalArgumentException("窗口已存在: " + windowId);
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
        w.setSourceType(inferSourceType(channelName));
        w.setSourceUrl("");
        w.setCreateTime(LocalDateTime.now().format(FMT));
        windows.put(windowId, w);
        return w;
    }

    /**
     * 更新窗口（仅更新传入字段，未传字段保持原值不变）。
     * <p>
     * 窗口不存在时抛出 {@link IllegalArgumentException}。
     */
    public SimWindow updateWindow(String windowId, String channelName,
                                  Integer x, Integer y, Integer width, Integer height) {
        SimWindow w = windows.get(windowId);
        if (w == null) {
            throw new IllegalArgumentException("窗口不存在: " + windowId);
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
        windows.put(windowId, w);
        return w;
    }

    /**
     * 关闭窗口。窗口不存在时抛出 {@link IllegalArgumentException}。
     */
    public SimWindow closeWindow(String windowId) {
        SimWindow w = windows.remove(windowId);
        if (w == null) {
            throw new IllegalArgumentException("窗口不存在: " + windowId);
        }
        return w;
    }

    /** 查询单个窗口 */
    public SimWindow findWindow(String windowId) {
        return windows.get(windowId);
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