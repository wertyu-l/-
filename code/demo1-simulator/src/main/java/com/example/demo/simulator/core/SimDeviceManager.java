package com.example.demo.simulator.core;

import com.example.demo.model.SimDeviceCapability;
import com.example.demo.model.SimDeviceInfo;
import com.example.demo.model.SimDeviceStatus;
import com.example.demo.model.SimWindow;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 模拟设备管理器（输入设备，1个输入通道）
 * <p>
 * 一个进程 = 一台设备。设备信息和能力从 H2 数据库加载（自启动自动初始化），
 * 窗口数据也持久化到数据库，进程重启后自动恢复。
 * <p>
 * 本设备为输入设备（deviceCategory = "INPUT"），窗口绑定到输入通道。
 * 创建窗口时校验 channelName 是否为该设备有效的输入通道名。
 */
@Component
public class SimDeviceManager {

    /** 本进程唯一的设备信息（从 DB 加载） */
    private final SimDeviceInfo deviceInfo;

    /** 本进程唯一的设备能力（从 DB 加载） */
    private final SimDeviceCapability deviceCapability;

    /** 数据访问层 */
    private final DeviceRepository repo;

    /** 模拟设备启动时间，用于状态上报 */
    private final LocalDateTime startTime = LocalDateTime.now();

    /**
     * 构造时从数据库加载设备信息和能力。
     * schema.sql + data.sql 已保证表和数据在启动时自动初始化。
     */
    public SimDeviceManager(DeviceRepository repo) {
        this.repo = repo;
        this.deviceInfo = repo.loadDeviceInfo();
        this.deviceCapability = repo.loadDeviceCapability();
    }

    /** 获取设备基本信息 */
    public SimDeviceInfo getDeviceInfo() {
        return deviceInfo;
    }

    /** 获取设备运行状态（在线状态、窗口数、启动时间） */
    public SimDeviceStatus getDeviceStatus() {
        SimDeviceStatus status = new SimDeviceStatus();
        status.setOnline(true);
        status.setWindowCount(repo.countWindows());
        status.setUptime(startTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        return status;
    }

    /** 获取设备能力 */
    public SimDeviceCapability getDeviceCapability() {
        return deviceCapability;
    }

    /**
     * 更新设备能力（内存 + 数据库同步更新）
     */
    public SimDeviceCapability updateDeviceCapability(SimDeviceCapability newCapability) {
        deviceCapability.setSupportMove(newCapability.isSupportMove());
        deviceCapability.setSupportResize(newCapability.isSupportResize());
        deviceCapability.setSupportOverlay(newCapability.isSupportOverlay());
        deviceCapability.setMaxResolution(newCapability.getMaxResolution());
        deviceCapability.setInputChannel1(newCapability.getInputChannel1());
        repo.updateCapability(deviceCapability);
        repo.updateDeviceInfoFromCapability(deviceCapability);
        deviceInfo.setInputChannel1(newCapability.getInputChannel1());
        deviceInfo.setMaxResolution(newCapability.getMaxResolution());
        return deviceCapability;
    }

    /**
    /**
     * 判断 channelName 是否为该设备的有效输入通道名
     */
    public boolean isValidInputChannel(String channelName) {
        if (channelName == null || channelName.isEmpty()) return false;
        return channelName.equals(deviceInfo.getInputChannel1());
    }

    /**
     * 创建窗口
     * <p>
     * 校验规则：windowId 不重复 → channelName 是有效输入通道。
     * 输入通道不限制窗口数量，同一通道可以创建多个窗口，无窗口总数上限。
     * 创建时自动填充默认值、sourceType（根据通道名推断）和 createTime，写入数据库。
     *
     * @param window 窗口信息（windowId、channelName 必填）
     * @return 创建后的窗口（含自动生成的 createTime），失败返回 null
     */
    public SimWindow createWindow(SimWindow window) {
        // windowId 重复校验
        if (repo.findWindowById(window.getWindowId()) != null) {
            return null;
        }
        // channelName 有效性校验
        if (!isValidInputChannel(window.getChannelName())) {
            return null;
        }
        // 默认值
        if (window.getX() == null) window.setX(0);
        if (window.getY() == null) window.setY(0);
        if (window.getWidth() == null) window.setWidth(1920);
        if (window.getHeight() == null) window.setHeight(1080);
        if (window.getSourceUrl() == null) window.setSourceUrl("");
        // 自动生成 sourceType（根据通道名推断）
        if (window.getSourceType() == null || window.getSourceType().isEmpty()) {
            window.setSourceType(inferSourceType(window.getChannelName()));
        }
        // 自动生成创建时间
        window.setCreateTime(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        repo.insertWindow(window);
        return window;
    }

    /**
     * 根据通道名推断信号源类型
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

    /** 查询单个窗口 */
    public SimWindow getWindow(String windowId) {
        return repo.findWindowById(windowId);
    }

    /** 查询所有窗口 */
    public List<SimWindow> getWindows() {
        return repo.findAllWindows();
    }

    /** 关闭（删除）窗口 */
    public boolean closeWindow(String windowId) {
        return repo.deleteWindow(windowId);
    }

    /**
     * 更新窗口位置和/或大小
     * <p>
     * 移动前校验 supportMove，缩放前校验 supportResize。
     * 可单独移动（只传 x/y）、单独缩放（只传 width/height）、或同时移动+缩放。
     *
     * @param windowId 窗口唯一标识
     * @param update   更新参数（x、y、width、height 为 null 表示不修改该项）
     * @return 更新后的窗口，窗口不存在或能力不支持返回 null
     */
    public SimWindow updateWindow(String windowId, SimWindow update) {
        SimWindow existing = repo.findWindowById(windowId);
        if (existing == null) {
            return null;
        }
        // 移动：x 或 y 非 null 表示需要移动，校验 supportMove 能力
        boolean moveRequested = update.getX() != null || update.getY() != null;
        if (moveRequested) {
            if (!deviceCapability.isSupportMove()) {
                return null;
            }
            if (update.getX() != null) existing.setX(update.getX());
            if (update.getY() != null) existing.setY(update.getY());
        }
        // 缩放：width 或 height 非 null 表示需要缩放，校验 supportResize 能力
        boolean resizeRequested = update.getWidth() != null || update.getHeight() != null;
        if (resizeRequested) {
            if (!deviceCapability.isSupportResize()) {
                return null;
            }
            if (update.getWidth() != null) existing.setWidth(update.getWidth());
            if (update.getHeight() != null) existing.setHeight(update.getHeight());
        }
        repo.updateWindow(existing);
        return existing;
    }

}