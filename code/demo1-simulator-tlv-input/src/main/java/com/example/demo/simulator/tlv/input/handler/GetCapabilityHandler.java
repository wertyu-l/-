package com.example.demo.simulator.tlv.input.handler;

import com.example.demo.codec.TlvCommand;
import com.example.demo.codec.TlvFieldCodec;
import com.example.demo.codec.TlvFrame;
import com.example.demo.codec.TlvTag;
import com.example.demo.model.SimDeviceCapability;
import com.example.demo.simulator.tlv.input.core.SimDeviceManager;
import com.example.demo.simulator.tlv.input.server.TlvCommandHandler;
import com.example.demo.simulator.tlv.input.server.TlvResponses;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 查询设备能力（CMD_GET_CAPABILITY → RESP_CAPABILITY）。
 * <p>
 * 输入设备 maxWindows=0，supportMove/supportResize/supportOverlay 均为 false。
 */
@Component
public class GetCapabilityHandler implements TlvCommandHandler {

    private final SimDeviceManager manager;

    public GetCapabilityHandler(SimDeviceManager manager) {
        this.manager = manager;
    }

    @Override
    public int commandType() {
        return TlvCommand.CMD_GET_CAPABILITY;
    }

    @Override
    public TlvFrame handle(TlvFrame request) {
        SimDeviceCapability cap = manager.getDeviceCapability();
        List<byte[]> entries = new ArrayList<>();
        entries.add(TlvFieldCodec.encodeInt32(TlvTag.TAG_MAX_WINDOWS, cap.getMaxWindows()));
        entries.add(TlvFieldCodec.encodeBool(TlvTag.TAG_SUPPORT_MOVE, cap.isSupportMove()));
        entries.add(TlvFieldCodec.encodeBool(TlvTag.TAG_SUPPORT_RESIZE, cap.isSupportResize()));
        entries.add(TlvFieldCodec.encodeBool(TlvTag.TAG_SUPPORT_OVERLAY, cap.isSupportOverlay()));
        entries.add(TlvFieldCodec.encodeString(TlvTag.TAG_MAX_RESOLUTION, cap.getMaxResolution()));
        entries.add(TlvFieldCodec.encodeInt32(TlvTag.TAG_CHANNEL_COUNT, cap.getChannelCount()));
        entries.add(TlvFieldCodec.encodeString(TlvTag.TAG_DEVICE_CATEGORY, manager.getDeviceInfo().getDeviceCategory()));
        for (String channel : manager.getInputChannels()) {
            entries.add(TlvFieldCodec.encodeString(TlvTag.TAG_CHANNEL_NAME, channel));
        }
        return TlvResponses.success(TlvCommand.RESP_CAPABILITY, entries);
    }

}
