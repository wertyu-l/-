package com.example.demo.simulator.output.controller;

import com.example.demo.common.Result;
import com.example.demo.model.SimDeviceCapability;
import com.example.demo.model.SimDeviceInfo;
import com.example.demo.model.SimDeviceStatus;
import com.example.demo.model.SimWindow;
import com.example.demo.simulator.output.core.SimDeviceManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 输出设备模拟器 REST 控制器
 * <p>
 * 提供设备信息、状态、能力查询，以及子窗口的创建、查询、更新、关闭操作。
 * 输出设备通过 supportMove/supportResize/supportOverlay 声明设备能力，
 * 管控系统据此做布局校验，设备端仅校验移动/缩放能力与窗口数上限。
 * 被管控系统的 {@code RestDeviceDriver} 通过 HTTP 调用。
 */
@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/simulator")
public class SimDeviceController {

    @Autowired
    private SimDeviceManager deviceManager;

    /**
     * 获取设备基本信息
     * <p>
     * 返回设备名称、类型、类别、型号、序列号、输出通道列表、最大窗口数等信息。
     */
    @GetMapping("/device/info")
    public Result<SimDeviceInfo> getDeviceInfo() {
        return Result.success(deviceManager.getDeviceInfo());
    }

    /**
     * 获取设备运行状态
     * <p>
     * 返回在线状态、当前子窗口数量、设备启动时间。
     */
    @GetMapping("/device/status")
    public Result<SimDeviceStatus> getDeviceStatus() {
        return Result.success(deviceManager.getDeviceStatus());
    }

    /**
     * 获取设备能力
     * <p>
     * 返回 maxWindows、supportMove、supportResize、supportOverlay、
     * 最大分辨率、输出通道列表等能力声明。
     */
    @GetMapping("/device/capability")
    public Result<SimDeviceCapability> getDeviceCapability() {
        return Result.success(deviceManager.getDeviceCapability());
    }

    /**
     * 更新设备能力（运行时动态变更）
     * <p>
     * 支持修改能力开关、最大窗口数、通道配置等，同时更新内存缓存和数据库。
     */
    @PutMapping("/device/capability")
    public Result<SimDeviceCapability> updateDeviceCapability(@RequestBody SimDeviceCapability capability) {
        return Result.success(deviceManager.updateDeviceCapability(capability));
    }

    /**
     * 创建子窗口（管控系统下发）
     * <p>
     * 管控系统将大屏窗口按单元拆分后，推送到各输出设备。
     * 校验输出通道名有效性，窗口 ID 唯一性，存入内存后返回创建结果。
     */
    @PostMapping("/device/window")
    public Result<SimWindow> createWindow(@RequestBody SimWindow window) {
        if (window.getWindowId() == null || window.getWindowId().isEmpty()) {
            return Result.error("窗口ID不能为空");
        }
        if (window.getChannelName() == null || window.getChannelName().isEmpty()) {
            return Result.error("通道名不能为空");
        }
        if (!deviceManager.isValidOutputChannel(window.getChannelName())) {
            return Result.error("通道名无效: " + window.getChannelName());
        }
        if (deviceManager.countWindowsByChannel(window.getChannelName()) >= deviceManager.getDeviceCapability().getMaxWindows()) {
            return Result.error("窗口数已达上限: " + deviceManager.getDeviceCapability().getMaxWindows());
        }
        SimWindow created = deviceManager.createWindow(window);
        if (created == null) {
            return Result.error("窗口ID已存在: " + window.getWindowId());
        }
        return Result.success(created);
    }

    /**
     * 查询设备上所有子窗口
     * <p>
     * 返回当前设备内存中维护的全部子窗口列表。
     */
    @GetMapping("/device/windows")
    public Result<List<SimWindow>> getWindows() {
        return Result.success(deviceManager.getWindows());
    }

    /**
     * 查询单个子窗口
     *
     * @param windowId 窗口唯一标识
     */
    @GetMapping("/device/window/{windowId}")
    public Result<SimWindow> getWindow(@PathVariable String windowId) {
        SimWindow window = deviceManager.getWindow(windowId);
        if (window == null) {
            return Result.error("窗口不存在: " + windowId);
        }
        return Result.success(window);
    }

    /**
     * 更新子窗口位置/大小（管控系统下发）
     * <p>
     * 校验设备能力：坐标变化需 supportMove，尺寸变化需 supportResize。
     *
     * @param windowId 窗口唯一标识
     * @param update   更新参数（x、y、width、height）
     */
    @PutMapping("/device/window/{windowId}")
    public Result<SimWindow> updateWindow(@PathVariable String windowId, @RequestBody SimWindow update) {
        SimWindow existing = deviceManager.getWindow(windowId);
        if (existing == null) {
            return Result.error("窗口不存在: " + windowId);
        }
        SimWindow updated = deviceManager.updateWindow(windowId, update);
        if (updated == null) {
            return Result.error("设备不支持该窗口操作（移动需 supportMove，缩放需 supportResize）");
        }
        return Result.success(updated);
    }

    /**
     * 关闭子窗口（管控系统下发）
     * <p>
     * 从内存中移除指定子窗口。
     *
     * @param windowId 窗口唯一标识
     */
    @DeleteMapping("/device/window/{windowId}")
    public Result<Void> closeWindow(@PathVariable String windowId) {
        boolean removed = deviceManager.closeWindow(windowId);
        if (!removed) {
            return Result.error("窗口不存在: " + windowId);
        }
        return Result.success();
    }

}