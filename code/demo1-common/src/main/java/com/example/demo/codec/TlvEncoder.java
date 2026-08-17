package com.example.demo.codec;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * TLV 外层帧编码器：TlvFrame → byte[]。
 * <p>
 * 外层帧格式：Magic(0xEB 0x90) + Seq(2B) + Type(2B) + Length(2B) + Value(NB) + CRC16(2B)。
 * 除 CRC16 外所有字段均为大端序。
 */
public class TlvEncoder {

    public static byte[] encode(int seq, TlvFrame frame) {
        byte[] value = frame.getValue();
        if (value == null) {
            value = new byte[0];
        }
        int len = value.length;

        ByteArrayOutputStream bos = new ByteArrayOutputStream(8 + len + 2);
        // Magic
        bos.write(0xEB);
        bos.write(0x90);
        // Seq
        bos.write((seq >> 8) & 0xFF);
        bos.write(seq & 0xFF);
        // Type
        bos.write((frame.getType() >> 8) & 0xFF);
        bos.write(frame.getType() & 0xFF);
        // Length
        bos.write((len >> 8) & 0xFF);
        bos.write(len & 0xFF);
        // Value
        try {
            bos.write(value);
        } catch (IOException e) {
            throw new IllegalStateException("编码失败", e);
        }
        // CRC16（覆盖前面所有字节）
        byte[] data = bos.toByteArray();
        short crc = Crc16.compute(data);
        bos.write((crc >> 8) & 0xFF);
        bos.write(crc & 0xFF);
        return bos.toByteArray();
    }

    private TlvEncoder() {
    }

}
