package com.example.demo.simulator2.controller;

import com.example.demo.common.Result;
import com.example.demo.model.SimDeviceCapability;
import com.example.demo.model.SimDeviceInfo;
import com.example.demo.model.SimDeviceStatus;
import com.example.demo.model.SimWindow;
import com.example.demo.simulator2.core.SimDeviceManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 模拟设备2 REST 接口
 * <p>
 * 路径前缀 /simulator，模拟真实硬件设备暴露的 HTTP API。
 * 一个进程 = 一台设备，接口路径不再携带 deviceId。
 * 管控系统通过 RestDeviceDriver 调用这些接口来操作模拟设备。
 */
@RestController
@RequestMapping("/simulator")
public class SimDeviceController {

    @Autowired
    private SimDeviceManager deviceManager;

    /** 获取设备基本信息（名称、型号、序列号、通道数、分辨率等） */
    @GetMapping("/device/info")
    public Result<SimDeviceInfo> getDeviceInfo() {
        return Result.success(deviceManager.getDeviceInfo());
    }

    /** 获取设备运行状态（在线状态、窗口数、启动时间） */
    @GetMapping("/device/status")
    public Result<SimDeviceStatus> getDeviceStatus() {
        return Result.success(deviceManager.getDeviceStatus());
    }

    /** 获取设备能力（最大窗口数、是否支持移动/缩放/叠加等） */
    @GetMapping("/device/capability")
    public Result<SimDeviceCapability> getDeviceCapability() {
        return Result.success(deviceManager.getDeviceCapability());
    }

    /** 更新设备能力 */
    @PutMapping("/device/capability")
    public Result<SimDeviceCapability> updateDeviceCapability(@RequestBody SimDeviceCapability capability) {
        return Result.success(deviceManager.updateDeviceCapability(capability));
    }

    /**
     * 创建窗口
     * <p>
     * 必填字段：windowId、channel。
     * 可选字段：x、y、width、height、sourceType、sourceUrl（未填使用默认值）。
     * 失败场景：windowId 重复、channel 重复、已达最大窗口数。
     */
    @PostMapping("/device/window")
    public Result<SimWindow> createWindow(@RequestBody SimWindow window) {
        if (window.getWindowId() == null || window.getWindowId().isEmpty()) {
            return Result.error("窗口ID不能为空");
        }
        if (window.getChannel() <= 0) {
            return Result.error("通道编号必须大于0");
        }
        if (deviceManager.getWindow(window.getWindowId()) != null) {
            return Result.error("窗口已存在: " + window.getWindowId());
        }
        for (SimWindow w : deviceManager.getWindows()) {
            if (w.getChannel() == window.getChannel()) {
                return Result.error("通道已被占用: " + window.getChannel());
            }
        }
        SimWindow created = deviceManager.createWindow(window);
        if (created == null) {
            return Result.error("窗口数量已达上限: " + deviceManager.getDeviceCapability().getMaxWindows());
        }
        return Result.success(created);
    }

    /** 获取所有窗口列表（状态回读） */
    @GetMapping("/device/windows")
    public Result<List<SimWindow>> getWindows() {
        return Result.success(deviceManager.getWindows());
    }

    /** 获取单个窗口信息 */
    @GetMapping("/device/window/{windowId}")
    public Result<SimWindow> getWindow(@PathVariable String windowId) {
        SimWindow window = deviceManager.getWindow(windowId);
        if (window == null) {
            return Result.error("窗口不存在: " + windowId);
        }
        return Result.success(window);
    }

    /**
     * 更新窗口位置和或大小
     * <p>
     * 可单独移动（只传 x/y）、单独缩放（只传 width/height）、或同时移动+缩放。
     * 移动前校验 supportMove 能力，缩放前校验 supportResize 能力。
     */
    @PutMapping("/device/window/{windowId}")
    public Result<SimWindow> updateWindow(@PathVariable String windowId, @RequestBody SimWindow update) {
        SimWindow existing = deviceManager.getWindow(windowId);
        if (existing == null) {
            return Result.error("窗口不存在: " + windowId);
        }
        SimDeviceCapability capability = deviceManager.getDeviceCapability();
        boolean moveRequested = update.getX() != null || update.getY() != null;
        if (moveRequested && !capability.isSupportMove()) {
            return Result.error("设备不支持窗口移动");
        }
        boolean resizeRequested = update.getWidth() != null || update.getHeight() != null;
        if (resizeRequested && !capability.isSupportResize()) {
            return Result.error("设备不支持窗口缩放");
        }
        return Result.success(deviceManager.updateWindow(windowId, update));
    }

    /** 关闭（删除）窗口 */
    @DeleteMapping("/device/window/{windowId}")
    public Result<Void> closeWindow(@PathVariable String windowId) {
        if (!deviceManager.closeWindow(windowId)) {
            return Result.error("窗口不存在: " + windowId);
        }
        return Result.success();
    }

}