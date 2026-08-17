package com.example.demo.codec;

/**
 * TLV 外层帧解码器：byte[] → TlvFrame。
 * <p>
 * 解码时校验 Magic 与 CRC16，校验失败抛出 {@link IllegalArgumentException}。
 */
public class TlvDecoder {

    private static final int HEADER_LEN = 8;

    public static TlvFrame decode(byte[] data) {
        if (data == null || data.length < HEADER_LEN + 2) {
            throw new IllegalArgumentException("数据长度不足");
        }
        // 1. 校验 Magic
        if ((data[0] & 0xFF) != 0xEB || (data[1] & 0xFF) != 0x90) {
            throw new IllegalArgumentException("Invalid magic number");
        }
        // 2. 校验 CRC16
        if (!Crc16.verify(data)) {
            throw new IllegalArgumentException("CRC16 mismatch");
        }
        // 3. 提取字段
        int seq = ((data[2] & 0xFF) << 8) | (data[3] & 0xFF);
        int type = ((data[4] & 0xFF) << 8) | (data[5] & 0xFF);
        int length = ((data[6] & 0xFF) << 8) | (data[7] & 0xFF);
        if (data.length < HEADER_LEN + length + 2) {
            throw new IllegalArgumentException("Value 长度不足");
        }
        byte[] value = new byte[length];
        System.arraycopy(data, HEADER_LEN, value, 0, length);
        TlvFrame frame = new TlvFrame(type, length, value);
        frame.setSeq(seq);
        frame.setFields(TlvFieldCodec.decodeFields(value));
        return frame;
    }

    private TlvDecoder() {
    }

}
