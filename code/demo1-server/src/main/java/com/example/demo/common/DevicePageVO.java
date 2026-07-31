package com.example.demo.common;

import com.example.demo.model.SimDeviceInfo;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 设备分页返回 VO
 * <p>
 * 继承 {@link SimDeviceInfo}（设备描述字段），新增 id、baseUrl 和 online。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class DevicePageVO extends SimDeviceInfo {

    /** 数据库主键，用于 URL 标识 */
    private Long id;

    /** 设备 REST API 基地址 */
    private String baseUrl;

    /** 在线状态（1=在线，0=离线） */
    private Integer online;

    /** 启用状态（1=启用，0=禁用） */
    private Integer enabled;

    /** 最后心跳时间 */
    private LocalDateTime lastHeartbeat;

}