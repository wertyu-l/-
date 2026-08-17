package com.example.demo.simulator.tlv.output.handler;

import com.example.demo.codec.TlvCommand;
import com.example.demo.codec.TlvFieldCodec;
import com.example.demo.codec.TlvFrame;
import com.example.demo.codec.TlvTag;
import com.example.demo.model.SimDeviceInfo;
import com.example.demo.simulator.tlv.output.core.SimDeviceManager;
import com.example.demo.simulator.tlv.output.server.TlvCommandHandler;
import com.example.demo.simulator.tlv.output.server.TlvResponses;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 查询设备信息（CMD_GET_INFO → RESP_INFO）。
 */
@Component
public class GetInfoHandler implements TlvCommandHandler {

    private final SimDeviceManager manager;

    public GetInfoHandler(SimDeviceManager manager) {
        this.manager = manager;
    }

    @Override
    public int commandType() {
        return TlvCommand.CMD_GET_INFO;
    }

    @Override
    public TlvFrame handle(TlvFrame request) {
        SimDeviceInfo info = manager.getDeviceInfo();
        List<byte[]> entries = new ArrayList<>();
        entries.add(TlvFieldCodec.encodeString(TlvTag.TAG_DEVICE_NAME, info.getDeviceName()));
        entries.add(TlvFieldCodec.encodeString(TlvTag.TAG_DEVICE_MODEL, info.getModel()));
        entries.add(TlvFieldCodec.encodeString(TlvTag.TAG_DEVICE_ID, info.getSerialNumber()));
        entries.add(TlvFieldCodec.encodeInt32(TlvTag.TAG_CHANNEL_COUNT, info.getChannelCount()));
        entries.add(TlvFieldCodec.encodeString(TlvTag.TAG_MAX_RESOLUTION, info.getMaxResolution()));
        entries.add(TlvFieldCodec.encodeString(TlvTag.TAG_DEVICE_CATEGORY, info.getDeviceCategory()));
        for (String channel : manager.getOutputChannels()) {
            entries.add(TlvFieldCodec.encodeString(TlvTag.TAG_CHANNEL_NAME, channel));
        }
        return TlvResponses.success(TlvCommand.RESP_INFO, entries);
    }

}
