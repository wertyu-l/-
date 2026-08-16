package com.example.demo.model;

import lombok.Data;

/**
 * 模拟设备基本信息
 * <p>
 * 一个进程 = 一台设备，不再需要 deviceId 字段。
 * baseUrl（含端口）即设备唯一标识，由管控系统的 DeviceEndpoint 管理。
 * <p>
 * 设备分为两类，由 deviceCategory 字段标识（INPUT/OUTPUT）：
 * - 输入设备（INPUT）：拥有输入通道，作为被动信号源，仅接收管控系统推送的窗口快照，不做窗口增删改查。
 * - 输出设备（OUTPUT）：拥有输出通道，用于大屏绑定显示，窗口绑定到输出通道，拥有窗口的完整增删改查。
 */
@Data
public class SimDeviceInfo {

    private String deviceName;      // 设备名称，如 REST-Node-01
    private String deviceType;      // 设备类型，当前为 REST
    private String deviceCategory;  // 设备类别：INPUT=输入设备，OUTPUT=输出设备
    private String model;           // 设备型号，如 DS-D2055NH-A
    private String serialNumber;    // 序列号
    private int channelCount;       // 实际通道数，控制前端渲染和校验
    private String inputChannel1;   // 输入通道1名称，为空表示无该通道
    private String inputChannel2;
    private String inputChannel3;
    private String inputChannel4;
    private String inputChannel5;
    private String outputChannel1;  // 输出通道1名称，为空表示无该通道
    private String outputChannel2;
    private String outputChannel3;
    private String outputChannel4;
    private String outputChannel5;
    private String maxResolution;   // 最大分辨率，如 1920x1080
    private int maxWindows;         // 最大窗口数（仅输出设备有意义）

}