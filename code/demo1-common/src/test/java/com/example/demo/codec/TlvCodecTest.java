package com.example.demo.codec;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TLV 编解码器单元测试：CRC16、字段编解码往返、外层帧编解码、列表解码。
 */
class TlvCodecTest {

    @Test
    void crc16_verifyRoundTrip() {
        byte[] data = "hello tlv".getBytes(StandardCharsets.UTF_8);
        short crc = Crc16.compute(data);
        byte[] frame = new byte[data.length + 2];
        System.arraycopy(data, 0, frame, 0, data.length);
        frame[data.length] = (byte) (crc >> 8);
        frame[data.length + 1] = (byte) crc;
        assertTrue(Crc16.verify(frame));
        frame[data.length] ^= 0x01; // 破坏一位
        assertFalse(Crc16.verify(frame));
    }

    @Test
    void fieldEncodeDecodeRoundTrip() {
        java.util.List<byte[]> entries = java.util.List.of(
                TlvFieldCodec.encodeInt32(TlvTag.TAG_RESULT_CODE, 1),
                TlvFieldCodec.encodeString(TlvTag.TAG_DEVICE_NAME, "TLV输入设备-1"),
                TlvFieldCodec.encodeBool(TlvTag.TAG_ONLINE, true),
                TlvFieldCodec.encodeInt32(TlvTag.TAG_X, 960)
        );
        byte[] value = TlvFieldCodec.encodeFields(entries);
        Map<Byte, List<byte[]>> fields = TlvFieldCodec.decodeFields(value);

        assertEquals(Integer.valueOf(1), TlvFieldCodec.getInt32(fields, TlvTag.TAG_RESULT_CODE));
        assertEquals("TLV输入设备-1", TlvFieldCodec.getString(fields, TlvTag.TAG_DEVICE_NAME));
        assertEquals(Boolean.TRUE, TlvFieldCodec.getBool(fields, TlvTag.TAG_ONLINE));
        assertEquals(Integer.valueOf(960), TlvFieldCodec.getInt32(fields, TlvTag.TAG_X));
    }

    @Test
    void repeatedTags_arePreserved() {
        java.util.List<byte[]> entries = java.util.List.of(
                TlvFieldCodec.encodeString(TlvTag.TAG_CHANNEL_NAME, "HDMI-1"),
                TlvFieldCodec.encodeString(TlvTag.TAG_CHANNEL_NAME, "HDMI-2"),
                TlvFieldCodec.encodeString(TlvTag.TAG_CHANNEL_NAME, "HDMI-3")
        );
        byte[] value = TlvFieldCodec.encodeFields(entries);
        List<String> names = TlvFieldCodec.getStrings(
                TlvFieldCodec.decodeFields(value), TlvTag.TAG_CHANNEL_NAME);
        assertEquals(List.of("HDMI-1", "HDMI-2", "HDMI-3"), names);
    }

    @Test
    void outerFrameEncodeDecodeRoundTrip() {
        TlvFrame req = new TlvFrame(TlvCommand.CMD_GET_INFO, 0, new byte[0]);
        byte[] encoded = TlvEncoder.encode(100, req);

        assertEquals((byte) 0xEB, encoded[0]);
        assertEquals((byte) 0x90, encoded[1]);

        TlvFrame decoded = TlvDecoder.decode(encoded);
        assertEquals(100, decoded.getSeq());
        assertEquals(TlvCommand.CMD_GET_INFO, decoded.getType());
        assertEquals(0, decoded.getLength());
    }

    @Test
    void decoderRejectsBadMagic() {
        byte[] encoded = TlvEncoder.encode(1, new TlvFrame(TlvCommand.CMD_GET_STATUS, 0, new byte[0]));
        encoded[0] = 0x00;
        assertThrows(IllegalArgumentException.class, () -> TlvDecoder.decode(encoded));
    }

    @Test
    void decoderRejectsBadCrc() {
        byte[] encoded = TlvEncoder.encode(1, new TlvFrame(TlvCommand.CMD_GET_STATUS, 0, new byte[0]));
        encoded[encoded.length - 1] ^= 0x01;
        assertThrows(IllegalArgumentException.class, () -> TlvDecoder.decode(encoded));
    }

    @Test
    void decodeList_splitsWindows() {
        // code=1, windowCount=2, 然后两组窗口（每组以 windowId 起始）
        java.util.List<byte[]> entries = new java.util.ArrayList<>();
        entries.add(TlvFieldCodec.encodeInt32(TlvTag.TAG_RESULT_CODE, 1));
        entries.add(TlvFieldCodec.encodeInt32(TlvTag.TAG_WINDOW_COUNT, 2));
        entries.add(TlvFieldCodec.encodeString(TlvTag.TAG_WINDOW_ID, "win-001"));
        entries.add(TlvFieldCodec.encodeString(TlvTag.TAG_CHANNEL_NAME, "OUT-1"));
        entries.add(TlvFieldCodec.encodeInt32(TlvTag.TAG_X, 0));
        entries.add(TlvFieldCodec.encodeString(TlvTag.TAG_WINDOW_ID, "win-002"));
        entries.add(TlvFieldCodec.encodeString(TlvTag.TAG_CHANNEL_NAME, "OUT-2"));
        entries.add(TlvFieldCodec.encodeInt32(TlvTag.TAG_X, 100));

        byte[] value = TlvFieldCodec.encodeFields(entries);
        List<Map<Byte, List<byte[]>>> items = TlvFieldCodec.decodeList(value);

        assertEquals(2, items.size());
        assertEquals("win-001", TlvFieldCodec.getString(items.get(0), TlvTag.TAG_WINDOW_ID));
        assertEquals("OUT-1", TlvFieldCodec.getString(items.get(0), TlvTag.TAG_CHANNEL_NAME));
        assertEquals("win-002", TlvFieldCodec.getString(items.get(1), TlvTag.TAG_WINDOW_ID));
        assertEquals("OUT-2", TlvFieldCodec.getString(items.get(1), TlvTag.TAG_CHANNEL_NAME));
    }

}
