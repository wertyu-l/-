package com.example.demo.common;

import lombok.Data;

/**
 * 大屏分页查询 DTO
 */
@Data
public class ScreenPageDTO {
    private Integer page = 1;
    private Integer pageSize = 10;
    private String keyword;
}
