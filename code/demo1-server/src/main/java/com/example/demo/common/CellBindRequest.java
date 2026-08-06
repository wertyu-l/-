package com.example.demo.common;

import lombok.Data;

/**
 * 单元绑定设备请求 DTO
 */
@Data
public class CellBindRequest {
    private Integer rowIndex;
    private Integer colIndex;
    private Long deviceId;
    private String channelName;
}
