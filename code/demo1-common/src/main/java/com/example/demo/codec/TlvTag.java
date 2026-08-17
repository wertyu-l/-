package com.example.demo.codec;

/**
 * TLV 内层字段 Tag 常量。
 * <p>
 * Tag 编码规则：
 * <ul>
 *   <li>0x01-0x1F 字符串（UTF-8 字节）</li>
 *   <li>0x20-0x3F 整数 int32（4 字节大端序）</li>
 *   <li>0x40-0x5F 布尔（1 字节 0x00/0x01）</li>
 *   <li>0x60-0x7F 整数 int16（2 字节大端序）</li>
 *   <li>0x80-0xFF 保留</li>
 * </ul>
 */
public class TlvTag {

    // ===== 字符串类型 (0x01-0x1F) =====
    public static final byte TAG_WINDOW_ID      = 0x01;  // 窗口唯一标识
    public static final byte TAG_CHANNEL_NAME   = 0x02;  // 通道名称
    public static final byte TAG_SOURCE_TYPE    = 0x03;  // 信号源类型
    public static final byte TAG_SOURCE_URL     = 0x04;  // 信号源地址
    public static final byte TAG_DEVICE_ID      = 0x05;  // 设备唯一标识
    public static final byte TAG_DEVICE_NAME    = 0x06;  // 设备名称
    public static final byte TAG_DEVICE_MODEL   = 0x07;  // 设备型号
    public static final byte TAG_FIRMWARE_VER   = 0x08;  // 固件版本
    public static final byte TAG_UPTIME         = 0x09;  // 运行时长
    public static final byte TAG_MAX_RESOLUTION = 0x0A;  // 最大分辨率
    public static final byte TAG_CREATE_TIME    = 0x0B;  // 创建时间
    public static final byte TAG_ERROR_MSG      = 0x0C;  // 错误信息
    public static final byte TAG_DEVICE_CATEGORY = 0x0D; // 设备类别（INPUT/OUTPUT）
    public static final byte TAG_CHANNEL_URL    = 0x0E;  // 通道播放地址

    // ===== 整数类型 int32 (0x20-0x3F) =====
    public static final byte TAG_X              = 0x20;  // 窗口X坐标
    public static final byte TAG_Y              = 0x21;  // 窗口Y坐标
    public static final byte TAG_WIDTH          = 0x22;  // 窗口宽度
    public static final byte TAG_HEIGHT         = 0x23;  // 窗口高度
    public static final byte TAG_MAX_WINDOWS    = 0x24;  // 最大窗口数
    public static final byte TAG_CHANNEL_COUNT  = 0x25;  // 通道数量
    public static final byte TAG_WINDOW_COUNT   = 0x26;  // 当前窗口数
    public static final byte TAG_RESULT_CODE    = 0x27;  // 操作结果码（1=成功, 0=失败）

    // ===== 布尔类型 (0x40-0x5F) =====
    public static final byte TAG_ONLINE         = 0x40;  // 是否在线
    public static final byte TAG_SUPPORT_MOVE   = 0x41;  // 是否支持移动
    public static final byte TAG_SUPPORT_RESIZE = 0x42;  // 是否支持缩放
    public static final byte TAG_SUPPORT_OVERLAY = 0x43; // 是否支持叠加

    private TlvTag() {
    }

}
