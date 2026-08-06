package com.example.demo.simulator4.core;

import com.example.demo.model.SimDeviceCapability;
import com.example.demo.model.SimDeviceInfo;
import com.example.demo.model.SimDeviceStatus;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 模拟设备4 管理器（输出设备，2个输出通道：OUT-1、OUT-2）
 * <p>
 * 一个进程 = 一台设备。设备信息和能力从 H2 数据库加载（自启动自动初始化）。
 * <p>
 * 本设备为输出设备（deviceCategory = "OUTPUT"），用于大屏绑定显示，
 * 不存在窗口概念，不涉及窗口操作。
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

    /** 获取设备运行状态（输出设备无窗口，windowCount 始终为 0） */
    public SimDeviceStatus getDeviceStatus() {
        SimDeviceStatus status = new SimDeviceStatus();
        status.setOnline(true);
        status.setWindowCount(0);
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
        deviceCapability.setOutputChannel1(newCapability.getOutputChannel1());
        deviceCapability.setOutputChannel2(newCapability.getOutputChannel2());
        deviceCapability.setOutputChannel3(newCapability.getOutputChannel3());
        repo.updateCapability(deviceCapability);
        repo.updateDeviceInfoFromCapability(deviceCapability);
        deviceInfo.setOutputChannel1(newCapability.getOutputChannel1());
        deviceInfo.setOutputChannel2(newCapability.getOutputChannel2());
        deviceInfo.setOutputChannel3(newCapability.getOutputChannel3());
        deviceInfo.setMaxResolution(newCapability.getMaxResolution());
        return deviceCapability;
    }

}