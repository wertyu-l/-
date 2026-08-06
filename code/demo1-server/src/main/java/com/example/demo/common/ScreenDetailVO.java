package com.example.demo.common;

import lombok.Data;
import java.util.List;

/**
 * 大屏详情返回 VO
 */
@Data
public class ScreenDetailVO {
    private Long id;
    private String screenName;
    private Integer rowsCount;
    private Integer colsCount;
    private Integer cellWidth;
    private Integer cellHeight;
    private List<CellVO> cells;
    private String createTime;
}
