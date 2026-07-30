package com.example.demo.model;

import lombok.Data;

/**
 * 模拟设备窗口信息
 */
@Data
public class SimWindow {

    private String windowId;       // 窗口唯一标识，由管控系统生成
    private int channel;           // 绑定的输出通道编号，从 1 开始
    private Integer x;             // 窗口左上角 X 坐标，null 表示未设置，默认 0
    private Integer y;             // 窗口左上角 Y 坐标，null 表示未设置，默认 0
    private Integer width;         // 窗口宽度（像素），null 表示未设置，默认 1920
    private Integer height;        // 窗口高度（像素），null 表示未设置，默认 1080
    private String sourceType;     // 信号源类型，如 HDMI、VGA、Stream
    private String sourceUrl;      // 信号源地址，如流媒体 URL
    private String createTime;     // 窗口创建时间

}