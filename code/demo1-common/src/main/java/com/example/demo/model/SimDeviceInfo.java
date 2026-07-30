package com.example.demo.model;

import lombok.Data;

/**
 * 模拟设备基本信息
 * <p>
 * 一个进程 = 一台设备，不再需要 deviceId 字段。
 * baseUrl（含端口）即设备唯一标识，由管控系统的 DeviceEndpoint 管理。
 */
@Data
public class SimDeviceInfo {

    private String deviceName;     // 设备名称，如 REST-Node-01
    private String deviceType;     // 设备类型，当前为 REST
    private String model;          // 设备型号，如 DS-D2055NH-A
    private String serialNumber;   // 序列号
    private int outputChannels;    // 输出通道数
    private String maxResolution;  // 最大分辨率，如 1920x1080

}