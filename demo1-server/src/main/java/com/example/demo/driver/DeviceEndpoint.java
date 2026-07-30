package com.example.demo.driver;

import lombok.Data;

/**
 * 设备连接信息
 * <p>
 * baseUrl（含端口）即设备唯一标识，如 http://localhost:8086。
 * 一个模拟设备进程 = 一台设备，通过 baseUrl 区分。
 */
@Data
public class DeviceEndpoint {

    private String deviceType;     // 设备类型：REST / TLV
    private String baseUrl;        // REST 设备基地址，如 http://localhost:8086
    private String ip;             // TLV 设备 IP（预留）
    private int port;              // TLV 设备端口（预留）

}