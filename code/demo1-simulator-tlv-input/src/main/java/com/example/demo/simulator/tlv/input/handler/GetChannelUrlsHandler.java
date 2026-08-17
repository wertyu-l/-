package com.example.demo.simulator.tlv.input.handler;

import com.example.demo.codec.TlvCommand;
import com.example.demo.codec.TlvFieldCodec;
import com.example.demo.codec.TlvFrame;
import com.example.demo.codec.TlvTag;
import com.example.demo.simulator.tlv.input.core.SimDeviceManager;
import com.example.demo.simulator.tlv.input.server.TlvCommandHandler;
import com.example.demo.simulator.tlv.input.server.TlvResponses;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 获取所有通道播放地址（CMD_GET_CHANNEL_URLS → RESP_CHANNEL_URLS）。
 */
@Component
public class GetChannelUrlsHandler implements TlvCommandHandler {

    private final SimDeviceManager manager;

    public GetChannelUrlsHandler(SimDeviceManager manager) {
        this.manager = manager;
    }

    @Override
    public int commandType() {
        return TlvCommand.CMD_GET_CHANNEL_URLS;
    }

    @Override
    public TlvFrame handle(TlvFrame request) {
        List<byte[]> entries = new ArrayList<>();
        for (Map.Entry<String, String> e : manager.getChannelUrls().entrySet()) {
            entries.add(TlvFieldCodec.encodeString(TlvTag.TAG_CHANNEL_NAME, e.getKey()));
            entries.add(TlvFieldCodec.encodeString(TlvTag.TAG_CHANNEL_URL, e.getValue()));
        }
        return TlvResponses.success(TlvCommand.RESP_CHANNEL_URLS, entries);
    }

}
