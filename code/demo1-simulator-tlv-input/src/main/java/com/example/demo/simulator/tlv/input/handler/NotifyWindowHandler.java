package com.example.demo.simulator.tlv.input.handler;

import com.example.demo.codec.TlvCommand;
import com.example.demo.codec.TlvFieldCodec;
import com.example.demo.codec.TlvFrame;
import com.example.demo.codec.TlvTag;
import com.example.demo.model.SimWindow;
import com.example.demo.simulator.tlv.input.core.SimDeviceManager;
import com.example.demo.simulator.tlv.input.server.TlvCommandHandler;
import com.example.demo.simulator.tlv.input.server.TlvResponses;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 窗口信息反馈（CMD_NOTIFY_WINDOW → RESP_WINDOW）。
 * <p>
 * 管控系统在输出设备上创建/更新/关闭窗口后，将窗口占用关系推送给对应输入设备，
 * 输入设备据此维护「通道-窗口占用映射」。
 */
@Component
public class NotifyWindowHandler implements TlvCommandHandler {

    private final SimDeviceManager manager;

    public NotifyWindowHandler(SimDeviceManager manager) {
        this.manager = manager;
    }

    @Override
    public int commandType() {
        return TlvCommand.CMD_NOTIFY_WINDOW;
    }

    @Override
    public TlvFrame handle(TlvFrame request) {
        List<Map<Byte, List<byte[]>>> windowFieldsList = TlvFieldCodec.decodeList(request.getValue());
        List<SimWindow> windows = new ArrayList<>();
        for (Map<Byte, List<byte[]>> fields : windowFieldsList) {
            String windowId = TlvFieldCodec.getString(fields, TlvTag.TAG_WINDOW_ID);
            String channelName = TlvFieldCodec.getString(fields, TlvTag.TAG_CHANNEL_NAME);

            if (windowId == null || windowId.isEmpty()) {
                continue;
            }
            if (!manager.isValidInputChannel(channelName)) {
                continue;
            }

            SimWindow w = new SimWindow();
            w.setWindowId(windowId);
            w.setChannelName(channelName);
            Integer x = TlvFieldCodec.getInt32(fields, TlvTag.TAG_X);
            Integer y = TlvFieldCodec.getInt32(fields, TlvTag.TAG_Y);
            Integer width = TlvFieldCodec.getInt32(fields, TlvTag.TAG_WIDTH);
            Integer height = TlvFieldCodec.getInt32(fields, TlvTag.TAG_HEIGHT);
            w.setX(x == null ? 0 : x);
            w.setY(y == null ? 0 : y);
            w.setWidth(width == null ? 1920 : width);
            w.setHeight(height == null ? 1080 : height);
            windows.add(w);
        }

        manager.replaceWindows(windows);
        return TlvResponses.success(TlvCommand.RESP_WINDOW, List.of());
    }

}