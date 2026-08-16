package com.example.demo.simulator.input.controller;

import com.example.demo.common.Result;
import com.example.demo.model.SimDeviceCapability;
import com.example.demo.model.SimDeviceInfo;
import com.example.demo.model.SimDeviceStatus;
import com.example.demo.model.SimWindow;
import com.example.demo.simulator.input.core.SimDeviceManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 输入设备模拟器 REST 控制器
 * <p>
 * 提供设备信息、状态、能力查询，以及窗口快照反馈（notify）与窗口列表查询。
 * 输入设备作为被动信号源，仅接收管控系统推送的完整窗口快照并整体替换本地列表。
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
     * 返回设备名称、类型、类别、型号、序列号、通道列表等信息。
     */
    @GetMapping("/device/info")
    public Result<SimDeviceInfo> getDeviceInfo() {
        return Result.success(deviceManager.getDeviceInfo());
    }

    /**
     * 获取设备运行状态
     * <p>
     * 返回在线状态、当前窗口数量、设备启动时间。
     */
    @GetMapping("/device/status")
    public Result<SimDeviceStatus> getDeviceStatus() {
        return Result.success(deviceManager.getDeviceStatus());
    }

    /**
     * 获取设备能力
     * <p>
     * 返回通道数、最大分辨率等能力信息。
     * 输入设备不涉及 supportMove/supportResize/supportOverlay 能力。
     */
    @GetMapping("/device/capability")
    public Result<SimDeviceCapability> getDeviceCapability() {
        return Result.success(deviceManager.getDeviceCapability());
    }

    /**
     * 更新设备能力（运行时动态变更）
     * <p>
     * 支持修改通道数、通道名称、最大分辨率等，同时更新内存缓存和数据库。
     */
    @PutMapping("/device/capability")
    public Result<SimDeviceCapability> updateDeviceCapability(@RequestBody SimDeviceCapability capability) {
        return Result.success(deviceManager.updateDeviceCapability(capability));
    }

    /**
     * 窗口快照反馈（管控系统下发）
     * <p>
     * 管控系统在输入设备的窗口集合发生任何变化（新增/更新/关闭）时，
     * 推送该输入设备当前的完整窗口列表。输入设备用该快照整体替换本地窗口列表，
     * 快照中缺失的窗口即视为已关闭。
     */
    @PostMapping("/device/window/notify")
    public Result<Void> notifyWindow(@RequestBody List<SimWindow> windows) {
        deviceManager.notifyWindows(windows);
        return Result.success();
    }

    /**
     * 查询设备上所有窗口
     * <p>
     * 返回当前设备内存中维护的全部窗口列表（由 notify 快照维护）。
     */
    @GetMapping("/device/windows")
    public Result<List<SimWindow>> getWindows() {
        return Result.success(deviceManager.getWindows());
    }

    /**
     * 设置通道播放地址
     * <p>
     * 为指定输入通道配置信号源 URL，供前端预览使用。
     *
     * @param channelName 输入通道名称（如 HDMI-1）
     * @param body        请求体，包含 sourceUrl 字段
     */
    @PutMapping("/channel/{channelName}/url")
    public Result<Void> setChannelUrl(@PathVariable String channelName, @RequestBody java.util.Map<String, String> body) {
        String sourceUrl = body != null ? body.getOrDefault("sourceUrl", "") : "";
        deviceManager.setChannelUrl(channelName, sourceUrl != null ? sourceUrl : "");
        return Result.success();
    }

    /**
     * 获取所有通道的播放地址
     * <p>
     * 返回通道名到播放地址的映射。
     */
    @GetMapping("/channel/urls")
    public Result<java.util.Map<String, String>> getChannelUrls() {
        return Result.success(deviceManager.getChannelUrls());
    }

}