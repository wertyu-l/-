package com.example.demo.common;

import lombok.Data;

/**
 * 屏幕单元返回 VO
 */
@Data
public class CellVO {
    private Long id;
    private Long screenId;
    private Integer rowIndex;
    private Integer colIndex;
    private Long deviceId;
    private String channelName;
    private String deviceName;
    private String deviceType;
    private String deviceCategory;
    private Integer online;
    private String baseUrl;
    private Integer maxWindows;
    private Integer supportMove;
    private Integer supportResize;
    private Integer supportOverlay;
    private String maxResolution;
}