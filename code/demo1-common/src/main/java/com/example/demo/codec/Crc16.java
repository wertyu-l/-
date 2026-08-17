package com.example.demo.codec;

/**
 * CRC-16/XMODEM 校验（多项式 0x1021）。
 * <p>
 * 用于 TLV 外层帧的完整性校验，覆盖范围：Magic + Seq + Type + Length + Value。
 */
public class Crc16 {

    /** CRC-16/XMODEM 多项式 */
    private static final int POLY = 0x1021;

    /**
     * 计算整段数据的 CRC16。
     *
     * @param data 数据
     * @return CRC16 校验值（大端序写入帧尾）
     */
    public static short compute(byte[] data) {
        return compute(data, 0, data.length);
    }

    /**
     * 计算指定区间的 CRC16。
     *
     * @param data   数据
     * @param offset 起始偏移
     * @param length 长度
     * @return CRC16 校验值
     */
    public static short compute(byte[] data, int offset, int length) {
        int crc = 0x0000;
        for (int i = offset; i < offset + length; i++) {
            crc ^= (data[i] & 0xFF) << 8;
            for (int j = 0; j < 8; j++) {
                if ((crc & 0x8000) != 0) {
                    crc = (crc << 1) ^ POLY;
                } else {
                    crc <<= 1;
                }
            }
            crc &= 0xFFFF;
        }
        return (short) crc;
    }

    /**
     * 校验数据末尾 2 字节是否为该数据（不含尾 2 字节）的 CRC16。
     *
     * @param data 完整数据（末尾 2 字节为 CRC16，大端序）
     * @return true 表示校验通过
     */
    public static boolean verify(byte[] data) {
        if (data == null || data.length < 2) {
            return false;
        }
        int bodyLen = data.length - 2;
        short expected = (short) (((data[bodyLen] & 0xFF) << 8) | (data[bodyLen + 1] & 0xFF));
        short actual = compute(data, 0, bodyLen);
        return expected == actual;
    }

}
