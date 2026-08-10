package com.example.demo.common;

import com.example.demo.model.SimDeviceInfo;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 设备分页返回 VO
 * <p>
 * 继承 {@link SimDeviceInfo}（设备描述字段），新增 id、baseUrl、online、enabled 和心跳时间。
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

    // ===== 设备能力字段（从 DEVICE 表冗余存储，用于 getDeviceCapability） =====

    /** 是否支持窗口移动（1=支持，0=不支持） */
    private Integer supportMove;

    /** 是否支持窗口缩放（1=支持，0=不支持） */
    private Integer supportResize;

    /** 是否支持窗口叠加（1=支持，0=不支持） */
    private Integer supportOverlay;

}