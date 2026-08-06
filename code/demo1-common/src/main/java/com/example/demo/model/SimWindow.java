package com.example.demo.model;

import lombok.Data;

/**
 * 模拟设备窗口信息
 * <p>
 * 窗口是管控系统下发到模拟设备的内容展示单元，每个窗口绑定到设备的某个输入通道。
 * 窗口相关操作仅对输入设备（deviceCategory = "INPUT"）有意义。
 */
@Data
public class SimWindow {

    private String windowId;       // 窗口唯一标识，由管控系统生成，全局唯一
    private String channelName;    // 绑定的输入通道名称，必须是该设备已定义的输入通道名之一
    private Integer x;             // 窗口左上角 X 坐标，null 表示未设置，默认 0
    private Integer y;             // 窗口左上角 Y 坐标，null 表示未设置，默认 0
    private Integer width;         // 窗口宽度（像素），null 表示未设置，默认 1920
    private Integer height;        // 窗口高度（像素），null 表示未设置，默认 1080
    private String sourceType;     // 信号源类型，由设备根据通道配置返回，如 HDMI、VGA、Stream
    private String sourceUrl;      // 信号源地址，由设备根据通道配置返回，如流媒体 URL
    private String createTime;     // 窗口创建时间，格式 yyyy-MM-dd HH:mm:ss，自动生成

}
