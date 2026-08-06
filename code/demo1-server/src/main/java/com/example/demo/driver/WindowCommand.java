package com.example.demo.driver;

import lombok.Data;

/**
 * 窗口命令参数
 * <p>
 * 用于管控系统向模拟设备下发窗口操作命令，
 * 字段对齐 SimWindow 模型。
 */
@Data
public class WindowCommand {

    private String windowId;       // 窗口唯一标识
    private String channelName;    // 绑定的输入通道名称，必须是设备已定义的输入通道名之一
    private Integer x;             // 窗口左上角 X 坐标
    private Integer y;             // 窗口左上角 Y 坐标
    private Integer width;         // 窗口宽度（像素）
    private Integer height;        // 窗口高度（像素）
    private String sourceType;     // 信号源类型，如 HDMI、VGA、Stream
    private String sourceUrl;      // 信号源地址，如流媒体 URL

}