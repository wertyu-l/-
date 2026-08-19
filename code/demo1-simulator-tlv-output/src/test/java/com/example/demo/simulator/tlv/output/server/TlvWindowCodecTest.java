package com.example.demo.simulator.tlv.output.server;

import com.example.demo.codec.TlvFieldCodec;
import com.example.demo.codec.TlvTag;
import com.example.demo.model.SimWindow;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TlvWindowCodec 单元测试
 * <p>
 * 验证窗口字段编码的正确性，包括字段完整性、默认值处理、null 安全。
 */
class TlvWindowCodecTest {

    @Test
    void encodeWindow_shouldContainAllFields() {
        SimWindow w = new SimWindow();
        w.setWindowId("win-001");
        w.setChannelName("OUT-1");
        w.setX(10);
        w.setY(20);
        w.setWidth(800);
        w.setHeight(600);
        w.setSourceType("HDMI");
        w.setSourceUrl("rtsp://cam1/stream");
        w.setCreateTime("2026-01-01 12:00:00");

        List<byte[]> entries = TlvWindowCodec.encodeWindow(w);
        byte[] value = TlvFieldCodec.encodeFields(entries);
        Map<Byte, List<byte[]>> fields = TlvFieldCodec.decodeFields(value);

        assertEquals("win-001", TlvFieldCodec.getString(fields, TlvTag.TAG_WINDOW_ID));
        assertEquals("OUT-1", TlvFieldCodec.getString(fields, TlvTag.TAG_CHANNEL_NAME));
        assertEquals(Integer.valueOf(10), TlvFieldCodec.getInt32(fields, TlvTag.TAG_X));
        assertEquals(Integer.valueOf(20), TlvFieldCodec.getInt32(fields, TlvTag.TAG_Y));
        assertEquals(Integer.valueOf(800), TlvFieldCodec.getInt32(fields, TlvTag.TAG_WIDTH));
        assertEquals(Integer.valueOf(600), TlvFieldCodec.getInt32(fields, TlvTag.TAG_HEIGHT));
        assertEquals("HDMI", TlvFieldCodec.getString(fields, TlvTag.TAG_SOURCE_TYPE));
        assertEquals("rtsp://cam1/stream", TlvFieldCodec.getString(fields, TlvTag.TAG_SOURCE_URL));
        assertEquals("2026-01-01 12:00:00", TlvFieldCodec.getString(fields, TlvTag.TAG_CREATE_TIME));
    }

    @Test
    void encodeWindow_nullCoordinates_shouldUseDefaults() {
        SimWindow w = new SimWindow();
        w.setWindowId("win-002");
        w.setChannelName("OUT-2");

        List<byte[]> entries = TlvWindowCodec.encodeWindow(w);
        byte[] value = TlvFieldCodec.encodeFields(entries);
        Map<Byte, List<byte[]>> fields = TlvFieldCodec.decodeFields(value);

        assertEquals(Integer.valueOf(0), TlvFieldCodec.getInt32(fields, TlvTag.TAG_X));
        assertEquals(Integer.valueOf(0), TlvFieldCodec.getInt32(fields, TlvTag.TAG_Y));
        assertEquals(Integer.valueOf(1920), TlvFieldCodec.getInt32(fields, TlvTag.TAG_WIDTH));
        assertEquals(Integer.valueOf(1080), TlvFieldCodec.getInt32(fields, TlvTag.TAG_HEIGHT));
    }

    @Test
    void encodeWindow_nullSourceTypeAndUrl_shouldEncodeEmpty() {
        SimWindow w = new SimWindow();
        w.setWindowId("win-003");
        w.setChannelName("OUT-3");

        List<byte[]> entries = TlvWindowCodec.encodeWindow(w);
        byte[] value = TlvFieldCodec.encodeFields(entries);
        Map<Byte, List<byte[]>> fields = TlvFieldCodec.decodeFields(value);

        assertEquals("", TlvFieldCodec.getString(fields, TlvTag.TAG_SOURCE_TYPE));
        assertEquals("", TlvFieldCodec.getString(fields, TlvTag.TAG_SOURCE_URL));
    }

    @Test
    void encodeWindow_nullCreateTime_shouldEncodeEmpty() {
        SimWindow w = new SimWindow();
        w.setWindowId("win-004");
        w.setChannelName("OUT-4");

        List<byte[]> entries = TlvWindowCodec.encodeWindow(w);
        byte[] value = TlvFieldCodec.encodeFields(entries);
        Map<Byte, List<byte[]>> fields = TlvFieldCodec.decodeFields(value);

        assertEquals("", TlvFieldCodec.getString(fields, TlvTag.TAG_CREATE_TIME));
    }
}