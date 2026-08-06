package com.example.demo.common;

import lombok.Data;

/**
 * 创建窗口请求 DTO
 */
@Data
public class WindowCreateRequest {
    private String windowId;
    private Long deviceId;
    private String channelName;
    private Integer x = 0;
    private Integer y = 0;
    private Integer width = 960;
    private Integer height = 540;
}
