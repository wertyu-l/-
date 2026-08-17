package com.example.demo.codec;

import com.example.demo.model.SimWindow;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * TLV 字段级编解码器。
 * <p>
 * 内层每个字段编码为 {@code Tag(1B) + Length(1B) + Value(NB)}。
 * <p>
 * 由于通道名（tag 0x02）等字段会在同一帧中重复出现，解码结果采用
 * {@code Map<Byte, List<byte[]>>} 保留所有值；便捷读取方法取首个值。
 * <p>
 * 编码分两类：
 * <ul>
 *   <li>{@link #encodeField}/{@link #encodeString}/{@link #encodeInt32}/{@link #encodeBool}
 *       产出「完整 TLV 条目」字节（含 Tag+Length）。</li>
 *   <li>{@link #encodeFields(List)} 将多个完整条目拼接为 Value 字节序列（支持重复 Tag）；
 *       {@link #encodeFields(Map)} 为单值便捷重载。</li>
 * </ul>
 */
public class TlvFieldCodec {

    // ==================== 原始值字节 ====================

    /** 字符串 → UTF-8 字节（null 视为空串） */
    public static byte[] stringBytes(String s) {
        return s == null ? new byte[0] : s.getBytes(StandardCharsets.UTF_8);
    }

    /** int32 → 4 字节大端序 */
    public static byte[] int32Bytes(int v) {
        return new byte[]{
                (byte) (v >>> 24), (byte) (v >>> 16), (byte) (v >>> 8), (byte) v};
    }

    /** 布尔 → 1 字节（0x00=false, 0x01=true） */
    public static byte[] boolBytes(boolean b) {
        return new byte[]{(byte) (b ? 1 : 0)};
    }

    // ==================== 编码：完整 TLV 条目 ====================

    /** 单字段 → 完整 TLV 条目字节（Tag + Length + Value） */
    public static byte[] encodeField(byte tag, byte[] value) {
        byte[] v = value == null ? new byte[0] : value;
        if (v.length > 255) {
            throw new IllegalArgumentException("字段长度超限: " + v.length + " (最大 255)");
        }
        byte[] out = new byte[2 + v.length];
        out[0] = tag;
        out[1] = (byte) v.length;
        System.arraycopy(v, 0, out, 2, v.length);
        return out;
    }

    public static byte[] encodeString(byte tag, String value) {
        return encodeField(tag, stringBytes(value));
    }

    public static byte[] encodeInt32(byte tag, int value) {
        return encodeField(tag, int32Bytes(value));
    }

    public static byte[] encodeBool(byte tag, boolean value) {
        return encodeField(tag, boolBytes(value));
    }

    // ==================== 编码：多字段拼接 ====================

    /**
     * 将多个完整 TLV 条目拼接为 Value 字节序列（支持重复 Tag）。
     */
    public static byte[] encodeFields(List<byte[]> entries) {
        int total = 0;
        for (byte[] e : entries) {
            total += e.length;
        }
        byte[] out = new byte[total];
        int pos = 0;
        for (byte[] e : entries) {
            System.arraycopy(e, 0, out, pos, e.length);
            pos += e.length;
        }
        return out;
    }

    /**
     * 单值便捷编码：Map 键为 Tag，值为「原始字段值字节」（如 {@link #stringBytes} 结果），
     * 按 Map 插入顺序拼接为 Value 字节序列。
     */
    public static byte[] encodeFields(Map<Byte, byte[]> fields) {
        List<byte[]> entries = new ArrayList<>(fields.size());
        for (Map.Entry<Byte, byte[]> e : fields.entrySet()) {
            entries.add(encodeField(e.getKey(), e.getValue()));
        }
        return encodeFields(entries);
    }

    // ==================== 解码 ====================

    /** Value 字节序列 → 字段映射（保留重复 Tag，值为原始字节） */
    public static Map<Byte, List<byte[]>> decodeFields(byte[] value) {
        return decodeFields(value, 0, value == null ? 0 : value.length);
    }

    /** 指定区间解码 */
    public static Map<Byte, List<byte[]>> decodeFields(byte[] value, int offset, int length) {
        Map<Byte, List<byte[]>> fields = new LinkedHashMap<>();
        int pos = offset;
        int end = offset + length;
        while (pos + 2 <= end) {
            byte tag = value[pos];
            int len = value[pos + 1] & 0xFF;
            pos += 2;
            if (pos + len > end) {
                break;
            }
            byte[] v = new byte[len];
            System.arraycopy(value, pos, v, 0, len);
            pos += len;
            fields.computeIfAbsent(tag, k -> new ArrayList<>()).add(v);
        }
        return fields;
    }

    /**
     * 解码「列表」Value（如窗口列表）：先由 TAG_WINDOW_COUNT 声明元素数量，
     * 每组窗口字段以 TAG_WINDOW_ID 起始，切分为多个字段组。
     *
     * @param value 完整 Value 字节（含 code、windowCount 及 N 组窗口字段）
     * @return N 个字段组
     */
    public static List<Map<Byte, List<byte[]>>> decodeList(byte[] value) {
        List<Map<Byte, List<byte[]>>> items = new ArrayList<>();
        if (value == null || value.length == 0) {
            return items;
        }
        List<byte[]> flat = parseEntries(value);
        Map<Byte, List<byte[]>> cur = null;
        for (byte[] entry : flat) {
            byte tag = entry[0];
            byte[] v = new byte[entry.length - 2];
            System.arraycopy(entry, 2, v, 0, v.length);
            if (tag == TlvTag.TAG_WINDOW_ID) {
                cur = new LinkedHashMap<>();
                items.add(cur);
            }
            if (cur != null) {
                cur.computeIfAbsent(tag, k -> new ArrayList<>()).add(v);
            }
        }
        return items;
    }

    // ==================== 便捷读取 ====================

    /**
     * 将窗口列表编码为单条 Value 字节序列，与 {@link #decodeList} 互逆。
     * 每个窗口字段以 TAG_WINDOW_ID 起始，供 decodeList 切分。
     */
    public static byte[] encodeWindowList(List<SimWindow> windows) {
        List<byte[]> allEntries = new ArrayList<>();
        for (SimWindow w : windows) {
            allEntries.add(encodeString(TlvTag.TAG_WINDOW_ID, w.getWindowId()));
            allEntries.add(encodeString(TlvTag.TAG_CHANNEL_NAME, w.getChannelName()));
            allEntries.add(encodeInt32(TlvTag.TAG_X, w.getX() == null ? 0 : w.getX()));
            allEntries.add(encodeInt32(TlvTag.TAG_Y, w.getY() == null ? 0 : w.getY()));
            allEntries.add(encodeInt32(TlvTag.TAG_WIDTH, w.getWidth() == null ? 1920 : w.getWidth()));
            allEntries.add(encodeInt32(TlvTag.TAG_HEIGHT, w.getHeight() == null ? 1080 : w.getHeight()));
        }
        return encodeFields(allEntries);
    }

    public static String getString(Map<Byte, List<byte[]>> fields, byte tag) {
        List<byte[]> l = fields.get(tag);
        if (l == null || l.isEmpty()) {
            return null;
        }
        return new String(l.get(0), StandardCharsets.UTF_8);
    }

    public static Integer getInt32(Map<Byte, List<byte[]>> fields, byte tag) {
        List<byte[]> l = fields.get(tag);
        if (l == null || l.isEmpty() || l.get(0).length < 4) {
            return null;
        }
        byte[] b = l.get(0);
        return ((b[0] & 0xFF) << 24) | ((b[1] & 0xFF) << 16) | ((b[2] & 0xFF) << 8) | (b[3] & 0xFF);
    }

    public static Boolean getBool(Map<Byte, List<byte[]>> fields, byte tag) {
        List<byte[]> l = fields.get(tag);
        if (l == null || l.isEmpty() || l.get(0).length < 1) {
            return null;
        }
        return l.get(0)[0] != 0;
    }

    /** 读取某 Tag 的全部字符串值（用于重复出现的通道名等） */
    public static List<String> getStrings(Map<Byte, List<byte[]>> fields, byte tag) {
        List<byte[]> l = fields.get(tag);
        if (l == null) {
            return List.of();
        }
        List<String> out = new ArrayList<>(l.size());
        for (byte[] b : l) {
            out.add(new String(b, StandardCharsets.UTF_8));
        }
        return out;
    }

    // ==================== 内部工具 ====================

    /** 将 Value 解析为「完整 TLV 条目」的扁平列表（每项含 Tag+Length+Value） */
    private static List<byte[]> parseEntries(byte[] value) {
        List<byte[]> entries = new ArrayList<>();
        int pos = 0;
        while (pos + 2 <= value.length) {
            int len = value[pos + 1] & 0xFF;
            int entryLen = 2 + len;
            if (pos + entryLen > value.length) {
                break;
            }
            byte[] e = new byte[entryLen];
            System.arraycopy(value, pos, e, 0, entryLen);
            entries.add(e);
            pos += entryLen;
        }
        return entries;
    }

}