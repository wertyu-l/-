package com.example.demo.simulator.controller;

import com.example.demo.common.Result;
import com.example.demo.model.SimDeviceCapability;
import com.example.demo.model.SimDeviceInfo;
import com.example.demo.model.SimDeviceStatus;
import com.example.demo.model.SimWindow;
import com.example.demo.simulator.core.SimDeviceManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 模拟设备 REST 接口
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
        SimDeviceInfo info = deviceManager.getDeviceInfo();
        return Result.success(info);
    }

    /** 获取设备运行状态（在线状态、窗口数、启动时间） */
    @GetMapping("/device/status")
    public Result<SimDeviceStatus> getDeviceStatus() {
        SimDeviceStatus status = deviceManager.getDeviceStatus();
        return Result.success(status);
    }


    /** 获取设备能力（最大窗口数、是否支持移动/缩放/叠加等） */
    @GetMapping("/device/capability")
    public Result<SimDeviceCapability> getDeviceCapability() {
        SimDeviceCapability capability = deviceManager.getDeviceCapability();
        return Result.success(capability);
    }

    /** 更新设备能力 */
    @PutMapping("/device/capability")
    public Result<SimDeviceCapability> updateDeviceCapability(@RequestBody SimDeviceCapability capability) {
        SimDeviceCapability updated = deviceManager.updateDeviceCapability(capability);
        return Result.success(updated);
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
        // 校验必填字段
        if (window.getWindowId() == null || window.getWindowId().isEmpty()) {
            return Result.error("窗口ID不能为空");
        }
        if (window.getChannel() <= 0) {
            return Result.error("通道编号必须大于0");
        }
        // 先检查 windowId 是否重复
        if (deviceManager.getWindow(window.getWindowId()) != null) {
            return Result.error("窗口已存在: " + window.getWindowId());
        }
        // 检查 channel 是否已被占用
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
        List<SimWindow> windows = deviceManager.getWindows();
        return Result.success(windows);
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
        // 校验窗口是否存在
        SimWindow existing = deviceManager.getWindow(windowId);
        if (existing == null) {
            return Result.error("窗口不存在: " + windowId);
        }
        SimDeviceCapability capability = deviceManager.getDeviceCapability();
        // 移动操作：x 或 y 非 null 表示需要移动
        boolean moveRequested = update.getX() != null || update.getY() != null;
        if (moveRequested && !capability.isSupportMove()) {
            return Result.error("设备不支持窗口移动");
        }
        // 缩放操作：width 或 height 非 null 表示需要缩放
        boolean resizeRequested = update.getWidth() != null || update.getHeight() != null;
        if (resizeRequested && !capability.isSupportResize()) {
            return Result.error("设备不支持窗口缩放");
        }
        SimWindow updated = deviceManager.updateWindow(windowId, update);
        return Result.success(updated);
    }

    /** 关闭（删除）窗口 */
    @DeleteMapping("/device/window/{windowId}")
    public Result<Void> closeWindow(@PathVariable String windowId) {
        boolean removed = deviceManager.closeWindow(windowId);
        if (!removed) {
            return Result.error("窗口不存在: " + windowId);
        }
        return Result.success();
    }

}