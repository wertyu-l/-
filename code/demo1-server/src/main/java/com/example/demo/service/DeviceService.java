package com.example.demo.service;

import com.example.demo.common.*;
import com.example.demo.model.SimDeviceCapability;
import com.example.demo.model.SimDeviceInfo;
import com.example.demo.model.SimDeviceStatus;

import java.util.List;

/**
 * 设备管理 Service 接口
 */
public interface DeviceService {

    /**
     * 手动添加设备
     *
     * @param baseUrl 设备 REST API 基地址
     * @return 添加后的设备信息（含自增 id）
     */
    DevicePageVO addDevice(String baseUrl);

    /**
     * 删除设备（需先禁用）
     *
     * @param id 设备主键
     */
    void deleteDevice(Long id);

    /**
     * 启用/禁用设备
     *
     * @param id      设备主键
     * @param enabled 1=启用，0=禁用
     */
    void setEnabled(Long id, int enabled);

    /**
     * 刷新设备信息（从模拟设备重新拉取信息与能力并更新数据库）
     *
     * @param id 设备主键
     * @return 更新后的设备完整信息
     */
    DevicePageVO refreshDevice(Long id);

    /**
     * 更新所有设备在线状态（心跳检测）
     */
    void updateOnlineStatus();

    /**
     * 分页查询设备列表
     */
    PageResult<DevicePageVO> getPage(DevicePageDTO pageDTO);

    /**
     * 获取设备基本信息（实时查询模拟设备）
     */
    SimDeviceInfo getDeviceInfo(Long id);

    /**
     * 获取设备运行状态（实时查询模拟设备）
     */
    SimDeviceStatus getDeviceStatus(Long id);

    /**
     * 获取设备能力（从数据库 DEVICE 表返回能力字段）
     */
    SimDeviceCapability getDeviceCapability(Long id);

    /**
     * UDP 广播搜索设备
     *
     * @return 发现的设备列表，含是否已添加标记
     */
    List<DiscoveredNode> discover();

}
