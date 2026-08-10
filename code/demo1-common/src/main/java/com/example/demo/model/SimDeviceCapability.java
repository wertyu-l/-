package com.example.demo.model;

import lombok.Data;

/**
 * 模拟设备能力
 * <p>
 * 描述设备的功能限制，控制窗口创建时的校验规则。
 * 能力可以在运行时动态变更，用于模拟设备能力变化场景。
 * <p>
 * maxWindows 仅对输入设备有意义（输入设备有窗口操作），输出设备忽略此字段。
 */
@Data
public class SimDeviceCapability {

    private int maxWindows;          // 最大窗口数量（仅输出设备有意义）
    private boolean supportMove;     // 是否支持窗口移动
    private boolean supportResize;   // 是否支持窗口缩放
    private boolean supportOverlay;  // 是否支持窗口叠加
    private String maxResolution;    // 最大分辨率，如 1920x1080
    private int channelCount;        // 实际通道数
    private String inputChannel1;    // 输入通道1名称
    private String inputChannel2;
    private String inputChannel3;
    private String inputChannel4;
    private String inputChannel5;
    private String outputChannel1;   // 输出通道1名称
    private String outputChannel2;
    private String outputChannel3;
    private String outputChannel4;
    private String outputChannel5;

}