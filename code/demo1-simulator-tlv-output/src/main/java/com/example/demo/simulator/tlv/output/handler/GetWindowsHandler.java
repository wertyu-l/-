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

import java.util.ArrayList;
import java.util.List;

/**
 * 查询窗口列表（CMD_GET_WINDOWS → RESP_WINDOWS）。
 * <p>
 * 响应 Value = code + windowCount + N 组窗口字段（见 §5.2.8），
 * 管控侧用 {@link com.example.demo.codec.TlvFieldCodec#decodeList} 解析。
 */
@Component
public class GetWindowsHandler implements TlvCommandHandler {

    private final SimDeviceManager manager;

    public GetWindowsHandler(SimDeviceManager manager) {
        this.manager = manager;
    }

    @Override
    public int commandType() {
        return TlvCommand.CMD_GET_WINDOWS;
    }

    @Override
    public TlvFrame handle(TlvFrame request) {
        List<SimWindow> windows = manager.getWindows();
        List<byte[]> entries = new ArrayList<>();
        entries.add(TlvFieldCodec.encodeInt32(TlvTag.TAG_WINDOW_COUNT, windows.size()));
        for (SimWindow w : windows) {
            entries.addAll(TlvWindowCodec.encodeWindow(w));
        }
        return TlvResponses.success(TlvCommand.RESP_WINDOWS, entries);
    }

}
