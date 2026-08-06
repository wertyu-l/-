package com.example.demo.common;

import lombok.Data;
import java.util.List;

/**
 * 创建大屏请求 DTO
 */
@Data
public class ScreenCreateRequest {
    private String screenName;
    private Integer rowsCount = 1;
    private Integer colsCount = 1;
    private Integer cellWidth = 1920;
    private Integer cellHeight = 1080;
    private List<CellBindRequest> cells;
}
