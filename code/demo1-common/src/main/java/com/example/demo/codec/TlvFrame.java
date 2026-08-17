package com.example.demo.codec;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * TLV 外层帧结构。
 * <p>
 * 外层帧负责命令路由，Value 是嵌套的 TLV 字段序列。
 * <p>
 * 注意：字段映射采用 {@code Map<Byte, List<byte[]>>} 以保留重复出现的 Tag
 * （如多个通道名、窗口列表的整组字段），这是对设计文档 {@code Map<Byte,byte[]>}
 * 草图的必要修正——单值 Map 会丢失同 Tag 的重复字段。
 */
@Data
public class TlvFrame {

    /** 序列号（2 字节大端序），请求-响应匹配 */
    private int seq;

    /** 命令类型（2 字节大端序） */
    private int type;

    /** Value 字节长度（2 字节大端序） */
    private int length;

    /** 嵌套 TLV 字段的原始字节序列 */
    private byte[] value;

    /** 解析后的字段映射 Tag → Value bytes 列表（decode 时填充，保留重复 Tag） */
    private Map<Byte, List<byte[]>> fields = new LinkedHashMap<>();

    /** 列表场景：多个 TLV 字段组（decode 时填充） */
    private List<Map<Byte, List<byte[]>>> listItems = new ArrayList<>();

    public TlvFrame() {
    }

    public TlvFrame(int type, int length, byte[] value) {
        this.type = type;
        this.length = length;
        this.value = value;
    }

}
