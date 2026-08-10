package com.example.demo.common;

import lombok.Data;

/**
 * 子窗口 VO — 输出设备上渲染的单个子窗口信息
 */
@Data
public class SubWindowVO {
    private String windowId;
    private String sourceDeviceName;   // 信号源设备名
    private String sourceChannelName;  // 输入通道名
    private Integer x;                 // 子窗口在此输出设备上的 X 坐标
    private Integer y;                 // 子窗口在此输出设备上的 Y 坐标
    private Integer width;             // 子窗口宽度
    private Integer height;            // 子窗口高度
    private String sourceType;         // 信号类型
    private String sourceUrl;          // 信号地址
    private String syncStatus;         // 同步状态
    private Integer degraded;          // 是否降级
}