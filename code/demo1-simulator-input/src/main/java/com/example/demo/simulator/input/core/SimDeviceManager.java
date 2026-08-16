package com.example.demo.simulator.input.core;

import com.example.demo.model.SimDeviceCapability;
import com.example.demo.model.SimDeviceInfo;
import com.example.demo.model.SimDeviceStatus;
import com.example.demo.model.SimWindow;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 输入设备核心管理器
 * <p>
 * 管理设备信息、能力、窗口的内存缓存，并与 H2 数据库同步。
 * 输入设备作为被动信号源，仅接收管控系统推送的完整窗口快照并整体替换本地列表，
 * 不提供窗口的增删改查；窗口列表仅用于前端展示。
 * 输入设备不涉及 supportMove/supportResize/supportOverlay 能力。
 */
@Component
public class SimDeviceManager {

    private final SimDeviceInfo deviceInfo;
    private final SimDeviceCapability deviceCapability;
    private final DeviceRepository repo;
    private final LocalDateTime startTime = LocalDateTime.now();

    /**
     * 构造时从数据库加载设备信息和能力到内存
     */
    public SimDeviceManager(DeviceRepository repo) {
        this.repo = repo;
        this.deviceInfo = repo.loadDeviceInfo();
        this.deviceCapability = repo.loadDeviceCapability();
    }

    /**
     * 获取设备基本信息（内存缓存）
     */
    public SimDeviceInfo getDeviceInfo() {
        return deviceInfo;
    }

    /**
     * 从数据库重新加载设备信息
     */
    public SimDeviceInfo getDeviceInfoFromDb() {
        return repo.loadDeviceInfo();
    }

    /**
     * 获取设备运行状态
     * <p>
     * 返回在线状态、当前窗口数量、设备启动时间。
     */
    public SimDeviceStatus getDeviceStatus() {
        SimDeviceStatus status = new SimDeviceStatus();
        status.setOnline(true);
        status.setWindowCount(repo.countWindows());
        status.setUptime(startTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        return status;
    }

    /**
     * 获取设备能力（内存缓存）
     */
    public SimDeviceCapability getDeviceCapability() {
        return deviceCapability;
    }

    /**
     * 从数据库重新加载设备能力
     */
    public SimDeviceCapability getDeviceCapabilityFromDb() {
        return repo.loadDeviceCapability();
    }

    /**
     * 更新设备能力（运行时动态变更）
     * <p>
     * 更新内存缓存和数据库，同时同步 deviceInfo 中的通道信息。
     *
     * @param newCapability 新的能力配置
     * @return 更新后的能力对象
     */
    public SimDeviceCapability updateDeviceCapability(SimDeviceCapability newCapability) {
        deviceCapability.setMaxResolution(newCapability.getMaxResolution());
        deviceCapability.setChannelCount(newCapability.getChannelCount());
        deviceCapability.setInputChannel1(newCapability.getInputChannel1());
        deviceCapability.setInputChannel2(newCapability.getInputChannel2());
        deviceCapability.setInputChannel3(newCapability.getInputChannel3());
        deviceCapability.setInputChannel4(newCapability.getInputChannel4());
        deviceCapability.setInputChannel5(newCapability.getInputChannel5());
        repo.updateCapability(deviceCapability);
        repo.updateDeviceInfoFromCapability(deviceCapability);
        deviceInfo.setChannelCount(newCapability.getChannelCount());
        deviceInfo.setInputChannel1(newCapability.getInputChannel1());
        deviceInfo.setInputChannel2(newCapability.getInputChannel2());
        deviceInfo.setInputChannel3(newCapability.getInputChannel3());
        deviceInfo.setInputChannel4(newCapability.getInputChannel4());
        deviceInfo.setInputChannel5(newCapability.getInputChannel5());
        deviceInfo.setMaxResolution(newCapability.getMaxResolution());
        return deviceCapability;
    }

    /**
     * 校验输入通道名是否有效
     *
     * @param channelName 通道名称
     * @return true 表示该通道已定义
     */
    public boolean isValidInputChannel(String channelName) {
        if (channelName == null || channelName.isEmpty()) return false;
        return channelName.equals(deviceInfo.getInputChannel1())
                || channelName.equals(deviceInfo.getInputChannel2())
                || channelName.equals(deviceInfo.getInputChannel3())
                || channelName.equals(deviceInfo.getInputChannel4())
                || channelName.equals(deviceInfo.getInputChannel5());
    }

    /**
     * 窗口快照反馈：用管控系统推送的完整窗口列表整体替换本地窗口列表。
     * <p>
     * 快照中缺失的窗口即视为已关闭；新增或位置/大小变化的窗口直接覆盖。
     * 对每个窗口，若 sourceType/sourceUrl 为空，则根据通道配置推断/补全。
     *
     * @param windows 当前完整窗口列表（可为空列表表示全部关闭）
     */
    public void notifyWindows(List<SimWindow> windows) {
        List<SimWindow> snapshot = windows != null ? windows : List.of();
        for (SimWindow w : snapshot) {
            if (w.getSourceUrl() == null || w.getSourceUrl().isEmpty()) {
                String channelUrl = repo.getChannelUrl(w.getChannelName());
                w.setSourceUrl(channelUrl != null ? channelUrl : "");
            }
            if (w.getSourceType() == null || w.getSourceType().isEmpty()) {
                w.setSourceType(inferSourceType(w.getChannelName()));
            }
        }
        repo.replaceAllWindows(snapshot);
    }

    /**
     * 根据通道名前缀推断信号源类型
     * <p>
     * HDMI → HDMI, VGA → VGA, DP → DP, SDI → SDI, 其他 → Stream
     */
    private String inferSourceType(String channelName) {
        if (channelName == null) return "";
        String upper = channelName.toUpperCase();
        if (upper.startsWith("HDMI")) return "HDMI";
        if (upper.startsWith("VGA")) return "VGA";
        if (upper.startsWith("DP")) return "DP";
        if (upper.startsWith("SDI")) return "SDI";
        return "Stream";
    }

    /**
     * 查询所有窗口
     *
     * @return 当前设备内存中的全部窗口列表（由 notify 快照维护）
     */
    public List<SimWindow> getWindows() {
        return repo.findAllWindows();
    }

    /**
     * 设置通道播放地址
     *
     * @param channelName 输入通道名称
     * @param sourceUrl   播放地址（流媒体 URL）
     */
    public void setChannelUrl(String channelName, String sourceUrl) {
        repo.setChannelUrl(channelName, sourceUrl);
    }

    /**
     * 获取所有通道的播放地址
     *
     * @return 通道名到播放地址的映射
     */
    public java.util.Map<String, String> getChannelUrls() {
        return repo.getAllChannelUrls();
    }

}