package com.example.demo.controller;

import com.example.demo.common.*;
import com.example.demo.model.SimDeviceCapability;
import com.example.demo.model.SimDeviceInfo;
import com.example.demo.model.SimDeviceStatus;
import com.example.demo.service.DeviceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 设备管理控制器
 * <p>
 * 基路径 /device，所有接口均需 JWT 认证。
 */
@RestController
@RequestMapping("/device")
public class DeviceController {

    @Autowired
    private DeviceService deviceService;

    /**
     * 手动添加设备
     *
     * @param request 仅含 baseUrl
     * @return 添加后的设备完整信息
     */
    @PostMapping
    public Result<DevicePageVO> addDevice(@RequestBody AddDeviceRequest request) {
        try {
            DevicePageVO vo = deviceService.addDevice(request.getBaseUrl());
            return Result.success(vo);
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 搜索发现设备（UDP 广播）
     *
     * @return 发现的设备列表
     */
    @PostMapping("/discover")
    public Result<List<DiscoveredNode>> discover() {
        try {
            List<DiscoveredNode> nodes = deviceService.discover();
            return Result.success(nodes);
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 删除设备
     *
     * @param id 设备主键
     */
    @DeleteMapping("/{id}")
    public Result<Void> deleteDevice(@PathVariable Long id) {
        try {
            deviceService.deleteDevice(id);
            return Result.success();
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 分页查询设备列表
     */
    @GetMapping("/page")
    public Result<PageResult<DevicePageVO>> getPage(DevicePageDTO pageDTO) {
        try {
            PageResult<DevicePageVO> pageResult = deviceService.getPage(pageDTO);
            return Result.success(pageResult);
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 获取设备基本信息（实时查询模拟设备）
     *
     * @param id 设备主键
     */
    @GetMapping("/{id}/info")
    public Result<SimDeviceInfo> getDeviceInfo(@PathVariable Long id) {
        try {
            SimDeviceInfo info = deviceService.getDeviceInfo(id);
            return Result.success(info);
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 获取设备运行状态（实时查询模拟设备）
     *
     * @param id 设备主键
     */
    @GetMapping("/{id}/status")
    public Result<SimDeviceStatus> getDeviceStatus(@PathVariable Long id) {
        try {
            SimDeviceStatus status = deviceService.getDeviceStatus(id);
            return Result.success(status);
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 启用/禁用设备
     *
     * @param id      设备主键
     * @param request enabled 字段（1=启用，0=禁用）
     */
    @PutMapping("/{id}/enabled")
    public Result<Void> setEnabled(@PathVariable Long id, @RequestBody EnabledRequest request) {
        try {
            deviceService.setEnabled(id, request.getEnabled());
            return Result.success();
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 刷新设备信息（从模拟设备重新拉取信息与能力并更新数据库）
     *
     * @param id 设备主键
     */
    @PutMapping("/{id}/refresh")
    public Result<DevicePageVO> refreshDevice(@PathVariable Long id) {
        try {
            DevicePageVO vo = deviceService.refreshDevice(id);
            return Result.success(vo);
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 获取设备能力
     *
     * @param id 设备主键
     */
    @GetMapping("/{id}/capability")
    public Result<SimDeviceCapability> getDeviceCapability(@PathVariable Long id) {
        try {
            SimDeviceCapability capability = deviceService.getDeviceCapability(id);
            return Result.success(capability);
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

}
