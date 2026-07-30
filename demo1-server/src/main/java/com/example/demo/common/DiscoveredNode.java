package com.example.demo.common;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;

/**
 * 搜索发现的设备节点
 */
@Data
@AllArgsConstructor
public class DiscoveredNode implements Serializable {

    /** 设备 REST API 基地址，如 http://192.168.1.100:8086 */
    private String baseUrl;

    /** 是否已添加到管控系统 */
    private boolean added;

}
