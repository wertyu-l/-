package com.example.demo.driver;

import com.example.demo.common.Result;
import com.example.demo.model.SimDeviceInfo;
import com.example.demo.model.SimDeviceStatus;
import com.example.demo.model.SimWindow;

/**
 * 统一设备驱动接口
 * <p>
 * 管控系统通过此接口调用模拟设备，不直接依赖 REST 协议。
 * 后续接入 TLV 设备时只需新增 TlvDeviceDriver 实现，管控系统代码无需改动。
 * <p>
 * 一个模拟设备进程 = 一台设备，baseUrl（含端口）即设备唯一标识。
 * <p>
 * 当前只开放 V1/V2 阶段需要的四个方法，能力集、窗口查询/更新等在后续版本中按需加入。
 */
public interface DeviceDriver {

    /** 获取设备基本信息 */
    SimDeviceInfo getInfo(DeviceEndpoint endpoint);

    /** 获取设备运行状态 */
    SimDeviceStatus getStatus(DeviceEndpoint endpoint);

    /** 创建窗口 */
    Result<SimWindow> createWindow(DeviceEndpoint endpoint, SimWindow window);

    /** 关闭窗口 */
    Result<Void> closeWindow(DeviceEndpoint endpoint, String windowId);

}