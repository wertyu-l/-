package com.example.demo.common;

import lombok.Data;
import java.util.List;

/**
 * 输出设备窗口信息 VO — 每个大屏单元绑定的输出设备及其渲染的子窗口
 */
@Data
public class OutputDeviceWindowsVO {
    private Long cellId;
    private Integer rowIndex;
    private Integer colIndex;
    private Long deviceId;
    private String deviceName;
    private String channelName;         // 输出通道名
    private Integer online;             // 设备在线状态
    private String baseUrl;             // 设备地址
    private Integer maxWindows;         // 最大窗口数
    private Integer supportMove;        // 支持窗口移动
    private Integer supportResize;      // 支持窗口缩放
    private Integer supportOverlay;     // 支持窗口叠加
    private String maxResolution;       // 最大分辨率
    private List<SubWindowVO> windows;  // 此输出设备渲染的子窗口列表
}