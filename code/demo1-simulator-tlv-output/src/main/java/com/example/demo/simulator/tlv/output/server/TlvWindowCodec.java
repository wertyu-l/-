package com.example.demo.simulator.tlv.output.server;

import com.example.demo.codec.TlvFieldCodec;
import com.example.demo.codec.TlvTag;
import com.example.demo.model.SimWindow;

import java.util.ArrayList;
import java.util.List;

/**
 * 窗口字段编码工具。
 * <p>
 * 将 {@link SimWindow} 编码为窗口字段 TLV 条目序列，供 RESP_WINDOW（单个窗口）
 * 与 RESP_WINDOWS（窗口列表）复用。字段顺序与设计文档 §5.2.4 / §5.2.8 一致。
 */
public final class TlvWindowCodec {

    private TlvWindowCodec() {
    }

    /**
     * 编码单个窗口的完整字段：windowId、channelName、x、y、width、height、
     * sourceType、sourceUrl、createTime。
     */
    public static List<byte[]> encodeWindow(SimWindow w) {
        List<byte[]> entries = new ArrayList<>();
        entries.add(TlvFieldCodec.encodeString(TlvTag.TAG_WINDOW_ID, w.getWindowId()));
        entries.add(TlvFieldCodec.encodeString(TlvTag.TAG_CHANNEL_NAME, w.getChannelName()));
        entries.add(TlvFieldCodec.encodeInt32(TlvTag.TAG_X, w.getX() == null ? 0 : w.getX()));
        entries.add(TlvFieldCodec.encodeInt32(TlvTag.TAG_Y, w.getY() == null ? 0 : w.getY()));
        entries.add(TlvFieldCodec.encodeInt32(TlvTag.TAG_WIDTH, w.getWidth() == null ? 1920 : w.getWidth()));
        entries.add(TlvFieldCodec.encodeInt32(TlvTag.TAG_HEIGHT, w.getHeight() == null ? 1080 : w.getHeight()));
        entries.add(TlvFieldCodec.encodeString(TlvTag.TAG_SOURCE_TYPE, w.getSourceType()));
        entries.add(TlvFieldCodec.encodeString(TlvTag.TAG_SOURCE_URL, w.getSourceUrl()));
        entries.add(TlvFieldCodec.encodeString(TlvTag.TAG_CREATE_TIME, w.getCreateTime()));
        return entries;
    }

}
