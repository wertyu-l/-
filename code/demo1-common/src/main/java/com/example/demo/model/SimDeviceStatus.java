package com.example.demo.model;

import lombok.Data;

/**
 * 模拟设备运行状态
 */
@Data
public class SimDeviceStatus {

    private boolean online;        // 是否在线，当前始终为 true
    private int windowCount;       // 当前窗口数量
    private String uptime;         // 设备启动时间，格式 yyyy-MM-dd HH:mm:ss

}