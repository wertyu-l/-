package com.example.demo.simulator.tlv.input.server;

import com.example.demo.codec.TlvFrame;

/**
 * TLV 命令处理器接口。
 * <p>
 * 每个实现声明其处理的命令类型，TlvServer 收包后按命令类型分发。
 */
public interface TlvCommandHandler {

    /** 处理的命令类型（见 {@link com.example.demo.codec.TlvCommand}） */
    int commandType();

    /** 处理请求帧，返回响应帧（不含 Seq，Seq 由 TlvServer 回填） */
    TlvFrame handle(TlvFrame request);

}
