package com.example.demo.common;

import lombok.Data;

import java.io.Serializable;

/**
 * 添加设备请求 DTO
 * <p>
 * 仅需 baseUrl，其余信息由管控系统连接模拟设备后自动补全。
 */
@Data
public class AddDeviceRequest implements Serializable {

    private String baseUrl;

}
