package com.example.demo.common;

import lombok.Data;

/**
 * 窗口返回 VO
 */
@Data
public class ScreenWindowVO {
    private String windowId;
    private Long screenId;
    private Long deviceId;
    private String deviceName;
    private String deviceCategory;
    private String channelName;
    private Integer x;
    private Integer y;
    private Integer width;
    private Integer height;
    private String sourceType;
    private String sourceUrl;
    private String syncStatus;
    private Integer degraded;
    private Integer supportMove;       // 设备能力-支持移动
    private Integer supportResize;     // 设备能力-支持缩放
    private Integer supportOverlay;    // 设备能力-支持叠加
    private String  maxResolution;     // 设备能力-最大分辨率
    private Integer maxWindows;        // 设备能力-最大窗口数
    private String createTime;
    private String updateTime;
}