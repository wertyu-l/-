package com.example.demo.entity;

import lombok.Data;

/**
 * 屏幕单元实体，对应 SCREEN_CELL 表
 */
@Data
public class ScreenCell {
    private Long id;
    private Long screenId;
    private Integer rowIndex;
    private Integer colIndex;
    private Long deviceId;
    private String channelName;
}
