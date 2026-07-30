package com.example.demo.common;

import lombok.Data;

import java.io.Serializable;

/**
 * 设备分页查询入参
 */
@Data
public class DevicePageDTO implements Serializable {

    /** 设备名称（模糊搜索） */
    private String deviceName;

    /** 设备类型（精确匹配） */
    private String deviceType;

    /** 页码 */
    private Integer page;

    /** 每页数量 */
    private Integer pageSize;

}
