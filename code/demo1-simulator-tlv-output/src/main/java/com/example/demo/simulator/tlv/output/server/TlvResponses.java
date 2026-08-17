package com.example.demo.simulator.tlv.output.server;

import com.example.demo.codec.TlvCommand;
import com.example.demo.codec.TlvFieldCodec;
import com.example.demo.codec.TlvFrame;
import com.example.demo.codec.TlvTag;

import java.util.ArrayList;
import java.util.List;

/**
 * 响应帧构建工具。
 * <p>
 * 所有响应统一在 Value 头部编码 {@code TAG_RESULT_CODE}（1=成功，0=失败），
 * 失败时附带 {@code TAG_ERROR_MSG}。
 */
public final class TlvResponses {

    private TlvResponses() {
    }

    /** 成功响应：自动在条目前插入 code=1 */
    public static TlvFrame success(int respType, List<byte[]> entries) {
        List<byte[]> all = new ArrayList<>(entries.size() + 1);
        all.add(TlvFieldCodec.encodeInt32(TlvTag.TAG_RESULT_CODE, 1));
        all.addAll(entries);
        byte[] value = TlvFieldCodec.encodeFields(all);
        return new TlvFrame(respType, value.length, value);
    }

    /** 错误响应：code=0 + 错误信息 */
    public static TlvFrame error(String msg) {
        byte[] value = TlvFieldCodec.encodeFields(List.of(
                TlvFieldCodec.encodeInt32(TlvTag.TAG_RESULT_CODE, 0),
                TlvFieldCodec.encodeString(TlvTag.TAG_ERROR_MSG, msg)));
        return new TlvFrame(TlvCommand.RESP_ERROR, value.length, value);
    }

}
