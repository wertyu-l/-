package com.example.demo.simulator.tlv.output.handler;

import com.example.demo.codec.TlvCommand;
import com.example.demo.codec.TlvFieldCodec;
import com.example.demo.codec.TlvFrame;
import com.example.demo.codec.TlvTag;
import com.example.demo.simulator.tlv.output.core.SimDeviceManager;
import com.example.demo.simulator.tlv.output.server.TlvCommandHandler;
import com.example.demo.simulator.tlv.output.server.TlvResponses;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 关闭窗口（CMD_CLOSE_WINDOW → RESP_WINDOW）。
 */
@Component
public class CloseWindowHandler implements TlvCommandHandler {

    private final SimDeviceManager manager;

    public CloseWindowHandler(SimDeviceManager manager) {
        this.manager = manager;
    }

    @Override
    public int commandType() {
        return TlvCommand.CMD_CLOSE_WINDOW;
    }

    @Override
    public TlvFrame handle(TlvFrame request) {
        Map<Byte, List<byte[]>> fields = request.getFields();
        String windowId = TlvFieldCodec.getString(fields, TlvTag.TAG_WINDOW_ID);

        try {
            manager.closeWindow(windowId);
            return TlvResponses.success(TlvCommand.RESP_WINDOW, List.of());
        } catch (IllegalArgumentException e) {
            return TlvResponses.error(e.getMessage());
        }
    }

}
