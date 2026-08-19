package com.example.demo.simulator.tlv.output.server;

import com.example.demo.codec.TlvCommand;
import com.example.demo.codec.TlvFieldCodec;
import com.example.demo.codec.TlvFrame;
import com.example.demo.codec.TlvTag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TlvResponses 单元测试
 * <p>
 * 验证成功/错误响应帧构建的正确性，包括 code 字段、错误信息、响应类型。
 */
class TlvResponsesTest {

    // ========== success ==========

    @Test
    void success_noExtraEntries_shouldContainOnlyCode() {
        TlvFrame frame = TlvResponses.success(TlvCommand.RESP_WINDOW, List.of());

        assertNotNull(frame);
        assertEquals(TlvCommand.RESP_WINDOW, frame.getType());
        assertNotNull(frame.getValue());
        assertTrue(frame.getValue().length > 0);

        Map<Byte, List<byte[]>> fields = TlvFieldCodec.decodeFields(frame.getValue());
        assertEquals(Integer.valueOf(1), TlvFieldCodec.getInt32(fields, TlvTag.TAG_RESULT_CODE));
    }

    @Test
    void success_withExtraEntries_shouldContainCodeAndEntries() {
        List<byte[]> entries = List.of(
                TlvFieldCodec.encodeString(TlvTag.TAG_WINDOW_ID, "win-001"),
                TlvFieldCodec.encodeString(TlvTag.TAG_CHANNEL_NAME, "OUT-1"));
        TlvFrame frame = TlvResponses.success(TlvCommand.RESP_WINDOW, entries);

        Map<Byte, List<byte[]>> fields = TlvFieldCodec.decodeFields(frame.getValue());
        assertEquals(Integer.valueOf(1), TlvFieldCodec.getInt32(fields, TlvTag.TAG_RESULT_CODE));
        assertEquals("win-001", TlvFieldCodec.getString(fields, TlvTag.TAG_WINDOW_ID));
        assertEquals("OUT-1", TlvFieldCodec.getString(fields, TlvTag.TAG_CHANNEL_NAME));
    }

    @Test
    void success_shouldPreserveResponseType() {
        TlvFrame frame = TlvResponses.success(TlvCommand.RESP_INFO, List.of());
        assertEquals(TlvCommand.RESP_INFO, frame.getType());
    }

    // ========== error ==========

    @Test
    void error_shouldContainCodeZeroAndErrorMessage() {
        TlvFrame frame = TlvResponses.error("窗口不存在");

        assertNotNull(frame);
        assertEquals(TlvCommand.RESP_ERROR, frame.getType());

        Map<Byte, List<byte[]>> fields = TlvFieldCodec.decodeFields(frame.getValue());
        assertEquals(Integer.valueOf(0), TlvFieldCodec.getInt32(fields, TlvTag.TAG_RESULT_CODE));
        assertEquals("窗口不存在", TlvFieldCodec.getString(fields, TlvTag.TAG_ERROR_MSG));
    }

    @Test
    void error_emptyMessage_shouldStillHaveCodeZero() {
        TlvFrame frame = TlvResponses.error("");

        Map<Byte, List<byte[]>> fields = TlvFieldCodec.decodeFields(frame.getValue());
        assertEquals(Integer.valueOf(0), TlvFieldCodec.getInt32(fields, TlvTag.TAG_RESULT_CODE));
        assertEquals("", TlvFieldCodec.getString(fields, TlvTag.TAG_ERROR_MSG));
    }

    @Test
    void error_nullMessage_shouldStillHaveCodeZero() {
        TlvFrame frame = TlvResponses.error(null);

        Map<Byte, List<byte[]>> fields = TlvFieldCodec.decodeFields(frame.getValue());
        assertEquals(Integer.valueOf(0), TlvFieldCodec.getInt32(fields, TlvTag.TAG_RESULT_CODE));
        // encodeString(null) 会编码为空字符串，所以解码后也是空字符串
        assertEquals("", TlvFieldCodec.getString(fields, TlvTag.TAG_ERROR_MSG));
    }
}