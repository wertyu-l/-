package com.example.demo.simulator.tlv.input.handler;

import com.example.demo.codec.TlvCommand;
import com.example.demo.codec.TlvFieldCodec;
import com.example.demo.codec.TlvFrame;
import com.example.demo.codec.TlvTag;
import com.example.demo.model.SimDeviceStatus;
import com.example.demo.simulator.tlv.input.core.SimDeviceManager;
import com.example.demo.simulator.tlv.input.server.TlvCommandHandler;
import com.example.demo.simulator.tlv.input.server.TlvResponses;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 查询设备状态（CMD_GET_STATUS → RESP_STATUS）。
 */
@Component
public class GetStatusHandler implements TlvCommandHandler {

    private final SimDeviceManager manager;

    public GetStatusHandler(SimDeviceManager manager) {
        this.manager = manager;
    }

    @Override
    public int commandType() {
        return TlvCommand.CMD_GET_STATUS;
    }

    @Override
    public TlvFrame handle(TlvFrame request) {
        SimDeviceStatus status = manager.getDeviceStatus();
        List<byte[]> entries = List.of(
                TlvFieldCodec.encodeBool(TlvTag.TAG_ONLINE, status.isOnline()),
                TlvFieldCodec.encodeInt32(TlvTag.TAG_WINDOW_COUNT, status.getWindowCount()),
                TlvFieldCodec.encodeString(TlvTag.TAG_UPTIME, status.getUptime()));
        return TlvResponses.success(TlvCommand.RESP_STATUS, entries);
    }

}
