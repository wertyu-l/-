package com.example.demo.simulator.tlv.input.core;

import com.example.demo.model.SimDeviceCapability;
import com.example.demo.model.SimDeviceInfo;
import com.example.demo.model.SimDeviceStatus;
import com.example.demo.model.SimWindow;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 输入设备核心管理器。
 * <p>
 * 输入设备作为被动信号源，不支持窗口的增删改查，仅：
 * <ul>
 *   <li>维护「通道-窗口占用映射」{@code Map<channelName, Set<windowId>>}，
 *       由管控系统通过 CMD_NOTIFY_WINDOW 推送维护；</li>
 *   <li>维护各通道的播放地址（CMD_SET_CHANNEL_URL 设置）。</li>
 * </ul>
 * 窗口数据不持久化，进程重启后丢失。
 */
@Component
public class SimDeviceManager {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final DeviceConfig config;
    private final LocalDateTime startTime = LocalDateTime.now();

    /** 通道 → 占用该通道的 windowId 集合 */
    private final ConcurrentHashMap<String, Set<String>> channelWindows = new ConcurrentHashMap<>();
    /** windowId → 窗口详情（用于前端展示坐标等） */
    private final ConcurrentHashMap<String, SimWindow> windows = new ConcurrentHashMap<>();
    /** 通道名 → 播放地址 */
    private final ConcurrentHashMap<String, String> channelUrls = new ConcurrentHashMap<>();

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

    /** 收集设备已定义的输入通道名（非空） */
    public List<String> getInputChannels() {
        SimDeviceInfo info = getDeviceInfo();
        List<String> channels = new ArrayList<>();
        addIfNotEmpty(channels, info.getInputChannel1());
        addIfNotEmpty(channels, info.getInputChannel2());
        addIfNotEmpty(channels, info.getInputChannel3());
        addIfNotEmpty(channels, info.getInputChannel4());
        addIfNotEmpty(channels, info.getInputChannel5());
        return channels;
    }

    /** 校验输入通道名是否有效 */
    public boolean isValidInputChannel(String channelName) {
        if (channelName == null || channelName.isEmpty()) {
            return false;
        }
        return channelName.equals(getDeviceInfo().getInputChannel1())
                || channelName.equals(getDeviceInfo().getInputChannel2())
                || channelName.equals(getDeviceInfo().getInputChannel3())
                || channelName.equals(getDeviceInfo().getInputChannel4())
                || channelName.equals(getDeviceInfo().getInputChannel5());
    }

    /**
     * 窗口信息反馈：更新通道-窗口占用映射与窗口详情。
     * <p>
     * 创建（windowId 不存在）→ 加入占用集合；更新（已存在）→ 更新坐标信息。
     * 坐标字段为 null 时保持原值或取默认值。
     */
    public void notifyWindow(String windowId, String channelName,
                             Integer x, Integer y, Integer width, Integer height) {
        if (windowId == null || windowId.isEmpty() || channelName == null || channelName.isEmpty()) {
            return;
        }
        channelWindows.computeIfAbsent(channelName, k -> ConcurrentHashMap.newKeySet()).add(windowId);

        SimWindow w = windows.computeIfAbsent(windowId, k -> new SimWindow());
        w.setWindowId(windowId);
        w.setChannelName(channelName);
        if (x != null) w.setX(x);
        else if (w.getX() == null) w.setX(0);
        if (y != null) w.setY(y);
        else if (w.getY() == null) w.setY(0);
        if (width != null) w.setWidth(width);
        else if (w.getWidth() == null) w.setWidth(1920);
        if (height != null) w.setHeight(height);
        else if (w.getHeight() == null) w.setHeight(1080);
        if (w.getSourceType() == null || w.getSourceType().isEmpty()) {
            w.setSourceType(inferSourceType(channelName));
        }
        if (w.getSourceUrl() == null || w.getSourceUrl().isEmpty()) {
            w.setSourceUrl(channelUrls.getOrDefault(channelName, ""));
        }
        if (w.getCreateTime() == null) {
            w.setCreateTime(LocalDateTime.now().format(FMT));
        }
        windows.put(windowId, w);
    }

    /** 通道-窗口占用映射 */
    public Map<String, Set<String>> getChannelWindows() {
        return channelWindows;
    }

    /** 查询所有窗口（由 notify 维护） */
    public List<SimWindow> getWindows() {
        return new ArrayList<>(windows.values());
    }

    /** 替换全部窗口：清空现有映射，用传入列表重建。 */
    public void replaceWindows(List<SimWindow> newWindows) {
        windows.clear();
        channelWindows.clear();
        if (newWindows != null) {
            for (SimWindow w : newWindows) {
                notifyWindow(w.getWindowId(), w.getChannelName(),
                        w.getX(), w.getY(), w.getWidth(), w.getHeight());
            }
        }
    }

    /** 设置通道播放地址 */
    public void setChannelUrl(String channelName, String sourceUrl) {
        channelUrls.put(channelName, sourceUrl == null ? "" : sourceUrl);
    }

    /** 获取所有通道播放地址 */
    public Map<String, String> getChannelUrls() {
        return new LinkedHashMap<>(channelUrls);
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