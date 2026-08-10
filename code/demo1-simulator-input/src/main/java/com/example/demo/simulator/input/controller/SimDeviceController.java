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

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/simulator")
public class SimDeviceController {

    @Autowired
    private SimDeviceManager deviceManager;

    @GetMapping("/device/info")
    public Result<SimDeviceInfo> getDeviceInfo() {
        return Result.success(deviceManager.getDeviceInfo());
    }

    @GetMapping("/device/status")
    public Result<SimDeviceStatus> getDeviceStatus() {
        return Result.success(deviceManager.getDeviceStatus());
    }

    @GetMapping("/device/capability")
    public Result<SimDeviceCapability> getDeviceCapability() {
        return Result.success(deviceManager.getDeviceCapability());
    }

    @PutMapping("/device/capability")
    public Result<SimDeviceCapability> updateDeviceCapability(@RequestBody SimDeviceCapability capability) {
        return Result.success(deviceManager.updateDeviceCapability(capability));
    }

    @PostMapping("/device/window")
    public Result<SimWindow> createWindow(@RequestBody SimWindow window) {
        if (window.getWindowId() == null || window.getWindowId().isEmpty()) {
            return Result.error("窗口ID不能为空");
        }
        if (window.getChannelName() == null || window.getChannelName().isEmpty()) {
            return Result.error("通道名不能为空");
        }
        if (!deviceManager.isValidInputChannel(window.getChannelName())) {
            return Result.error("通道名无效: " + window.getChannelName());
        }
        SimWindow created = deviceManager.createWindow(window);
        if (created == null) {
            return Result.error("窗口ID已存在: " + window.getWindowId());
        }
        return Result.success(created);
    }

    @GetMapping("/device/windows")
    public Result<List<SimWindow>> getWindows() {
        return Result.success(deviceManager.getWindows());
    }

    @GetMapping("/device/window/{windowId}")
    public Result<SimWindow> getWindow(@PathVariable String windowId) {
        SimWindow window = deviceManager.getWindow(windowId);
        if (window == null) {
            return Result.error("窗口不存在: " + windowId);
        }
        return Result.success(window);
    }

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
        SimWindow updated = deviceManager.updateWindow(windowId, update);
        return Result.success(updated);
    }

    @DeleteMapping("/device/window/{windowId}")
    public Result<Void> closeWindow(@PathVariable String windowId) {
        boolean removed = deviceManager.closeWindow(windowId);
        if (!removed) {
            return Result.error("窗口不存在: " + windowId);
        }
        return Result.success();
    }

    @PutMapping("/channel/{channelName}/url")
    public Result<Void> setChannelUrl(@PathVariable String channelName, @RequestBody java.util.Map<String, String> body) {
        String sourceUrl = body != null ? body.getOrDefault("sourceUrl", "") : "";
        deviceManager.setChannelUrl(channelName, sourceUrl != null ? sourceUrl : "");
        return Result.success();
    }

    @GetMapping("/channel/urls")
    public Result<java.util.Map<String, String>> getChannelUrls() {
        return Result.success(deviceManager.getChannelUrls());
    }

}