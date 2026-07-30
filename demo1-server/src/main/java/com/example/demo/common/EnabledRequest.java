package com.example.demo.common;

import lombok.Data;

import java.io.Serializable;

/**
 * 启用/禁用设备请求 DTO
 */
@Data
public class EnabledRequest implements Serializable {

    /** 1=启用，0=禁用 */
    private Integer enabled;

}
