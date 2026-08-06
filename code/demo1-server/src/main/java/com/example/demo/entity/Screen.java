package com.example.demo.entity;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 大屏实体，对应 SCREEN 表
 */
@Data
public class Screen {
    private Long id;
    private String screenName;
    private Integer rowsCount;
    private Integer colsCount;
    private Integer cellWidth;
    private Integer cellHeight;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
