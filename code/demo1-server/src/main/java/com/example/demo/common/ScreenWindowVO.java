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
    private String createTime;
    private String updateTime;
}