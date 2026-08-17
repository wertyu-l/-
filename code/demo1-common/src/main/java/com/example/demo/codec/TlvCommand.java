package com.example.demo.codec;

/**
 * TLV 命令类型常量。
 */
public class TlvCommand {

    // ===== 查询命令 (0x0001-0x000F) =====
    public static final int CMD_GET_INFO        = 0x0001;  // 查询设备基本信息
    public static final int CMD_GET_STATUS      = 0x0002;  // 查询设备运行状态
    public static final int CMD_GET_CAPABILITY  = 0x0003;  // 查询设备能力

    // ===== 窗口操作命令 (0x0010-0x001F) =====
    public static final int CMD_CREATE_WINDOW   = 0x0010;  // 创建窗口
    public static final int CMD_GET_WINDOWS     = 0x0011;  // 查询窗口列表
    public static final int CMD_CLOSE_WINDOW    = 0x0012;  // 关闭窗口
    public static final int CMD_UPDATE_WINDOW   = 0x0013;  // 更新窗口

    // ===== 通道地址命令 (0x0020-0x002F) =====
    public static final int CMD_SET_CHANNEL_URL   = 0x0020;  // 设置通道播放地址
    public static final int CMD_GET_CHANNEL_URLS  = 0x0021;  // 查询所有通道播放地址

    // ===== 窗口通知命令 (0x0030-0x003F) =====
    public static final int CMD_NOTIFY_WINDOW   = 0x0030;  // 窗口信息反馈（输入设备用）

    // ===== 响应命令 (0x8001-0xFFFF) =====
    public static final int RESP_INFO           = 0x8001;  // 设备信息响应
    public static final int RESP_STATUS         = 0x8002;  // 设备状态响应
    public static final int RESP_CAPABILITY     = 0x8003;  // 设备能力响应
    public static final int RESP_WINDOW         = 0x8010;  // 通用成功响应
    public static final int RESP_WINDOWS        = 0x8011;  // 窗口列表响应
    public static final int RESP_CHANNEL_URLS   = 0x8020;  // 通道地址响应
    public static final int RESP_ERROR          = 0xFFFF;  // 错误响应

    private TlvCommand() {
    }

}
