package com.example.demo.common;

import lombok.Data;

/**
 * 大屏分页返回 VO
 */
@Data
public class ScreenPageVO {
    private Long id;
    private String screenName;
    private Integer rowsCount;
    private Integer colsCount;
    private Integer cellWidth;
    private Integer cellHeight;
    private Integer cellCount;
    private Integer windowCount;
    private String createTime;
}
