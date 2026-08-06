package com.example.demo.common;

import lombok.Data;

/**
 * 更新窗口请求 DTO
 */
@Data
public class WindowUpdateRequest {
    private Integer x;
    private Integer y;
    private Integer width;
    private Integer height;
}
