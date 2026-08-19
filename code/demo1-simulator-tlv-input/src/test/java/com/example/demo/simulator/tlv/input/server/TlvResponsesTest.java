package com.example.demo.simulator.tlv.input.server;

import com.example.demo.codec.TlvCommand;
import com.example.demo.codec.TlvFieldCodec;
import com.example.demo.codec.TlvFrame;
import com.example.demo.codec.TlvTag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TlvResponses 单元测试（输入模拟器）
 * <p>
 * 验证成功/错误响应帧构建的正确性。
 */
class TlvResponsesTest {

    @Test
    void success_noExtraEntries_shouldContainOnlyCode() {
        TlvFrame frame = TlvResponses.success(TlvCommand.RESP_WINDOW, List.of());

        assertNotNull(frame);
        assertEquals(TlvCommand.RESP_WINDOW, frame.getType());
        Map<Byte, List<byte[]>> fields = TlvFieldCodec.decodeFields(frame.getValue());
        assertEquals(Integer.valueOf(1), TlvFieldCodec.getInt32(fields, TlvTag.TAG_RESULT_CODE));
    }

    @Test
    void success_withExtraEntries_shouldContainCodeAndEntries() {
        List<byte[]> entries = List.of(
                TlvFieldCodec.encodeString(TlvTag.TAG_DEVICE_NAME, "TestDevice"));
        TlvFrame frame = TlvResponses.success(TlvCommand.RESP_INFO, entries);

        Map<Byte, List<byte[]>> fields = TlvFieldCodec.decodeFields(frame.getValue());
        assertEquals(Integer.valueOf(1), TlvFieldCodec.getInt32(fields, TlvTag.TAG_RESULT_CODE));
        assertEquals("TestDevice", TlvFieldCodec.getString(fields, TlvTag.TAG_DEVICE_NAME));
    }

    @Test
    void error_shouldContainCodeZeroAndErrorMessage() {
        TlvFrame frame = TlvResponses.error("通道名无效");

        assertEquals(TlvCommand.RESP_ERROR, frame.getType());
        Map<Byte, List<byte[]>> fields = TlvFieldCodec.decodeFields(frame.getValue());
        assertEquals(Integer.valueOf(0), TlvFieldCodec.getInt32(fields, TlvTag.TAG_RESULT_CODE));
        assertEquals("通道名无效", TlvFieldCodec.getString(fields, TlvTag.TAG_ERROR_MSG));
    }
}