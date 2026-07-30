package com.example.demo.model;

import lombok.Data;

/**
 * 模拟设备能力
 * <p>
 * 描述设备的功能限制，控制窗口创建时的校验规则。
 * 能力可以在运行时动态变更，用于模拟设备能力变化场景。
 */
@Data
public class SimDeviceCapability {

    private int maxWindows;        // 最大窗口数量，超出后拒绝创建
    private boolean supportMove;   // 是否支持窗口移动
    private boolean supportResize; // 是否支持窗口缩放
    private boolean supportOverlay;// 是否支持窗口叠加
    private String maxResolution;  // 最大分辨率，如 1920x1080
    private int outputChannels;    // 输出通道数

}
