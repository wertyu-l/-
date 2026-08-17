package com.example.demo.simulator.tlv.input.handler;

import com.example.demo.codec.TlvCommand;
import com.example.demo.codec.TlvFieldCodec;
import com.example.demo.codec.TlvFrame;
import com.example.demo.codec.TlvTag;
import com.example.demo.simulator.tlv.input.core.SimDeviceManager;
import com.example.demo.simulator.tlv.input.server.TlvCommandHandler;
import com.example.demo.simulator.tlv.input.server.TlvResponses;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 设置通道播放地址（CMD_SET_CHANNEL_URL → RESP_WINDOW）。
 */
@Component
public class SetChannelUrlHandler implements TlvCommandHandler {

    private final SimDeviceManager manager;

    public SetChannelUrlHandler(SimDeviceManager manager) {
        this.manager = manager;
    }

    @Override
    public int commandType() {
        return TlvCommand.CMD_SET_CHANNEL_URL;
    }

    @Override
    public TlvFrame handle(TlvFrame request) {
        Map<Byte, List<byte[]>> fields = request.getFields();
        String channelName = TlvFieldCodec.getString(fields, TlvTag.TAG_CHANNEL_NAME);
        String sourceUrl = TlvFieldCodec.getString(fields, TlvTag.TAG_CHANNEL_URL);

        if (!manager.isValidInputChannel(channelName)) {
            return TlvResponses.error("通道名无效: " + channelName);
        }
        manager.setChannelUrl(channelName, sourceUrl);
        return TlvResponses.success(TlvCommand.RESP_WINDOW, List.of());
    }

}
