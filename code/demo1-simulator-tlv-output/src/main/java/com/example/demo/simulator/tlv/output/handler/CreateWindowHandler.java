package com.example.demo.simulator.tlv.output.handler;

import com.example.demo.codec.TlvCommand;
import com.example.demo.codec.TlvFieldCodec;
import com.example.demo.codec.TlvFrame;
import com.example.demo.codec.TlvTag;
import com.example.demo.model.SimWindow;
import com.example.demo.simulator.tlv.output.core.SimDeviceManager;
import com.example.demo.simulator.tlv.output.server.TlvCommandHandler;
import com.example.demo.simulator.tlv.output.server.TlvResponses;
import com.example.demo.simulator.tlv.output.server.TlvWindowCodec;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 创建窗口（CMD_CREATE_WINDOW → RESP_WINDOW）。
 */
@Component
public class CreateWindowHandler implements TlvCommandHandler {

    private final SimDeviceManager manager;

    public CreateWindowHandler(SimDeviceManager manager) {
        this.manager = manager;
    }

    @Override
    public int commandType() {
        return TlvCommand.CMD_CREATE_WINDOW;
    }

    @Override
    public TlvFrame handle(TlvFrame request) {
        Map<Byte, java.util.List<byte[]>> fields = request.getFields();
        String windowId = TlvFieldCodec.getString(fields, TlvTag.TAG_WINDOW_ID);
        String channelName = TlvFieldCodec.getString(fields, TlvTag.TAG_CHANNEL_NAME);
        Integer x = TlvFieldCodec.getInt32(fields, TlvTag.TAG_X);
        Integer y = TlvFieldCodec.getInt32(fields, TlvTag.TAG_Y);
        Integer width = TlvFieldCodec.getInt32(fields, TlvTag.TAG_WIDTH);
        Integer height = TlvFieldCodec.getInt32(fields, TlvTag.TAG_HEIGHT);

        try {
            SimWindow w = manager.createWindow(windowId, channelName, x, y, width, height);
            return TlvResponses.success(TlvCommand.RESP_WINDOW, TlvWindowCodec.encodeWindow(w));
        } catch (IllegalArgumentException e) {
            return TlvResponses.error(e.getMessage());
        }
    }

}