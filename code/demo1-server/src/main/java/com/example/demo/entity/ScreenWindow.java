package com.example.demo.entity;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 窗口实体，对应 SCREEN_WINDOW 表（由前台模块管理）
 */
@Data
public class ScreenWindow {
    private Long id;
    private String windowId;
    private Long screenId;
    private Long deviceId;
    private String channelName;
    private Integer x;
    private Integer y;
    private Integer width;
    private Integer height;
    private String sourceType;
    private String sourceUrl;
    private String syncStatus;
    private Integer degraded;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
