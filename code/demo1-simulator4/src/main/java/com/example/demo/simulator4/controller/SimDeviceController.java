package com.example.demo.simulator4.controller;

import com.example.demo.common.Result;
import com.example.demo.model.SimDeviceCapability;
import com.example.demo.model.SimDeviceInfo;
import com.example.demo.model.SimDeviceStatus;
import com.example.demo.simulator4.core.SimDeviceManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 模拟设备4 REST 接口（输出设备，2个输出通道：OUT-1、OUT-2）
 * <p>
 * 路径前缀 /simulator，模拟真实硬件设备暴露的 HTTP API。
 * 一个进程 = 一台设备，接口路径不再携带 deviceId。
 * <p>
 * 本设备为输出设备（deviceCategory = "OUTPUT"），用于大屏绑定显示，
 * 不涉及窗口操作。仅提供设备信息、状态和能力查询/更新接口。
 */
@RestController
@RequestMapping("/simulator")
public class SimDeviceController {

    @Autowired
    private SimDeviceManager deviceManager;

    /** 获取设备基本信息 */
    @GetMapping("/device/info")
    public Result<SimDeviceInfo> getDeviceInfo() {
        return Result.success(deviceManager.getDeviceInfo());
    }

    /** 获取设备运行状态（输出设备无窗口） */
    @GetMapping("/device/status")
    public Result<SimDeviceStatus> getDeviceStatus() {
        return Result.success(deviceManager.getDeviceStatus());
    }

    /** 获取设备能力 */
    @GetMapping("/device/capability")
    public Result<SimDeviceCapability> getDeviceCapability() {
        return Result.success(deviceManager.getDeviceCapability());
    }

    /** 更新设备能力 */
    @PutMapping("/device/capability")
    public Result<SimDeviceCapability> updateDeviceCapability(@RequestBody SimDeviceCapability capability) {
        return Result.success(deviceManager.updateDeviceCapability(capability));
    }

}