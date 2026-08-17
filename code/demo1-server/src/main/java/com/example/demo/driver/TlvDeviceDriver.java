package com.example.demo.driver;

import com.example.demo.codec.TlvCommand;
import com.example.demo.codec.TlvDecoder;
import com.example.demo.codec.TlvEncoder;
import com.example.demo.codec.TlvFieldCodec;
import com.example.demo.codec.TlvFrame;
import com.example.demo.codec.TlvTag;
import com.example.demo.common.Result;
import com.example.demo.model.SimDeviceCapability;
import com.example.demo.model.SimDeviceInfo;
import com.example.demo.model.SimDeviceStatus;
import com.example.demo.model.SimWindow;
import org.springframework.stereotype.Component;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * TLV 设备驱动实现（UDP + TLV 二进制协议）。
 * <p>
 * 通过 UDP 向 TLV 模拟设备发送 TLV 帧并接收响应，实现 {@link DeviceDriver} 统一抽象接口。
 * baseUrl 形如 {@code udp://ip:port}，Seq 自增生成用于请求-响应匹配。
 * 单次请求超时 3 秒，超时后立即重试，共 3 次机会（§7.5）。
 */
@Component
public class TlvDeviceDriver implements DeviceDriver {

    private static final int TIMEOUT_MS = 3000;
    private static final int MAX_ATTEMPTS = 3;

    private final AtomicInteger seqGenerator = new AtomicInteger(0);

    // ==================== 地址解析 ====================

    private String hostOf(DeviceEndpoint endpoint) {
        String baseUrl = endpoint.getBaseUrl();
        if (baseUrl != null && baseUrl.startsWith("udp://")) {
            String s = baseUrl.substring("udp://".length());
            int colon = s.lastIndexOf(':');
            if (colon > 0) {
                return s.substring(0, colon);
            }
        }
        return endpoint.getIp();
    }

    private int portOf(DeviceEndpoint endpoint) {
        String baseUrl = endpoint.getBaseUrl();
        if (baseUrl != null && baseUrl.startsWith("udp://")) {
            String s = baseUrl.substring("udp://".length());
            int colon = s.lastIndexOf(':');
            if (colon > 0) {
                try {
                    return Integer.parseInt(s.substring(colon + 1));
                } catch (NumberFormatException ignored) {
                    // 回退到 endpoint.port
                }
            }
        }
        return endpoint.getPort();
    }

    // ==================== 底层请求 ====================

    /**
     * 发送请求帧并等待匹配 Seq 的响应帧。
     *
     * @param type  命令类型
     * @param value Value 字节序列（已编码的内层 TLV 字段）
     */
    private TlvFrame request(DeviceEndpoint endpoint, int type, byte[] value) {
        String host = hostOf(endpoint);
        int port = portOf(endpoint);
        if (host == null || host.isEmpty() || port <= 0) {
            throw new RuntimeException("TLV 设备地址无效: " + endpoint.getBaseUrl());
        }

        int seq = seqGenerator.incrementAndGet() & 0xFFFF;
        byte[] encoded = TlvEncoder.encode(seq, new TlvFrame(type, value == null ? 0 : value.length, value));

        Exception last = null;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            try (DatagramSocket socket = new DatagramSocket()) {
                socket.setSoTimeout(TIMEOUT_MS);
                InetAddress addr = InetAddress.getByName(host);
                socket.send(new DatagramPacket(encoded, encoded.length, addr, port));

                byte[] buf = new byte[65535];
                while (true) {
                    try {
                        DatagramPacket resp = new DatagramPacket(buf, buf.length);
                        socket.receive(resp);
                        byte[] data = new byte[resp.getLength()];
                        System.arraycopy(resp.getData(), resp.getOffset(), data, 0, resp.getLength());
                        TlvFrame frame = TlvDecoder.decode(data);
                        if (frame.getSeq() == seq) {
                            return frame;
                        }
                        // Seq 不匹配（如其它请求的迟到响应），继续等待
                    } catch (SocketTimeoutException e) {
                        break; // 本次尝试超时，进入下一次重试
                    }
                }
            } catch (Exception e) {
                last = e;
            }
        }
        throw new RuntimeException("TLV 设备请求失败: " + endpoint.getBaseUrl()
                + (last != null ? "（" + last.getMessage() + "）" : ""), last);
    }

    // ==================== 查询接口 ====================

    @Override
    public SimDeviceInfo getInfo(DeviceEndpoint endpoint) {
        TlvFrame resp = request(endpoint, TlvCommand.CMD_GET_INFO, new byte[0]);
        Map<Byte, List<byte[]>> f = resp.getFields();
        if (!isSuccess(f)) {
            return null;
        }
        SimDeviceInfo info = new SimDeviceInfo();
        info.setDeviceType("TLV");
        info.setDeviceCategory(str(f, TlvTag.TAG_DEVICE_CATEGORY));
        info.setDeviceName(str(f, TlvTag.TAG_DEVICE_NAME));
        info.setModel(str(f, TlvTag.TAG_DEVICE_MODEL));
        info.setSerialNumber(str(f, TlvTag.TAG_DEVICE_ID));
        info.setChannelCount(i(f, TlvTag.TAG_CHANNEL_COUNT, 0));
        info.setMaxResolution(str(f, TlvTag.TAG_MAX_RESOLUTION));
        assignChannels(info, strings(f, TlvTag.TAG_CHANNEL_NAME));
        return info;
    }

    @Override
    public SimDeviceCapability getCapability(DeviceEndpoint endpoint) {
        TlvFrame resp = request(endpoint, TlvCommand.CMD_GET_CAPABILITY, new byte[0]);
        Map<Byte, List<byte[]>> f = resp.getFields();
        if (!isSuccess(f)) {
            return null;
        }
        SimDeviceCapability cap = new SimDeviceCapability();
        cap.setMaxWindows(i(f, TlvTag.TAG_MAX_WINDOWS, 0));
        cap.setSupportMove(b(f, TlvTag.TAG_SUPPORT_MOVE, false));
        cap.setSupportResize(b(f, TlvTag.TAG_SUPPORT_RESIZE, false));
        cap.setSupportOverlay(b(f, TlvTag.TAG_SUPPORT_OVERLAY, false));
        cap.setMaxResolution(str(f, TlvTag.TAG_MAX_RESOLUTION));
        cap.setChannelCount(i(f, TlvTag.TAG_CHANNEL_COUNT, 0));
        assignCapabilityChannels(cap, str(f, TlvTag.TAG_DEVICE_CATEGORY), strings(f, TlvTag.TAG_CHANNEL_NAME));
        return cap;
    }

    @Override
    public SimDeviceStatus getStatus(DeviceEndpoint endpoint) {
        TlvFrame resp = request(endpoint, TlvCommand.CMD_GET_STATUS, new byte[0]);
        Map<Byte, List<byte[]>> f = resp.getFields();
        if (!isSuccess(f)) {
            return null;
        }
        SimDeviceStatus status = new SimDeviceStatus();
        status.setOnline(b(f, TlvTag.TAG_ONLINE, true));
        status.setWindowCount(i(f, TlvTag.TAG_WINDOW_COUNT, 0));
        status.setUptime(str(f, TlvTag.TAG_UPTIME));
        return status;
    }

    // ==================== 窗口操作 ====================

    @Override
    public Result<SimWindow> createWindow(DeviceEndpoint endpoint, SimWindow window) {
        byte[] value = TlvFieldCodec.encodeFields(List.of(
                TlvFieldCodec.encodeString(TlvTag.TAG_WINDOW_ID, window.getWindowId()),
                TlvFieldCodec.encodeString(TlvTag.TAG_CHANNEL_NAME, window.getChannelName()),
                TlvFieldCodec.encodeInt32(TlvTag.TAG_X, window.getX() == null ? 0 : window.getX()),
                TlvFieldCodec.encodeInt32(TlvTag.TAG_Y, window.getY() == null ? 0 : window.getY()),
                TlvFieldCodec.encodeInt32(TlvTag.TAG_WIDTH, window.getWidth() == null ? 1920 : window.getWidth()),
                TlvFieldCodec.encodeInt32(TlvTag.TAG_HEIGHT, window.getHeight() == null ? 1080 : window.getHeight())));
        TlvFrame resp = request(endpoint, TlvCommand.CMD_CREATE_WINDOW, value);
        return toWindowResult(resp);
    }

    @Override
    public Result<SimWindow> updateWindow(DeviceEndpoint endpoint, String windowId, SimWindow update) {
        List<byte[]> entries = new ArrayList<>();
        entries.add(TlvFieldCodec.encodeString(TlvTag.TAG_WINDOW_ID, windowId));
        if (update.getChannelName() != null) {
            entries.add(TlvFieldCodec.encodeString(TlvTag.TAG_CHANNEL_NAME, update.getChannelName()));
        }
        if (update.getX() != null) entries.add(TlvFieldCodec.encodeInt32(TlvTag.TAG_X, update.getX()));
        if (update.getY() != null) entries.add(TlvFieldCodec.encodeInt32(TlvTag.TAG_Y, update.getY()));
        if (update.getWidth() != null) entries.add(TlvFieldCodec.encodeInt32(TlvTag.TAG_WIDTH, update.getWidth()));
        if (update.getHeight() != null) entries.add(TlvFieldCodec.encodeInt32(TlvTag.TAG_HEIGHT, update.getHeight()));

        TlvFrame resp = request(endpoint, TlvCommand.CMD_UPDATE_WINDOW, TlvFieldCodec.encodeFields(entries));
        return toWindowResult(resp);
    }

    @Override
    public Result<Void> closeWindow(DeviceEndpoint endpoint, String windowId) {
        byte[] value = TlvFieldCodec.encodeFields(List.of(
                TlvFieldCodec.encodeString(TlvTag.TAG_WINDOW_ID, windowId)));
        TlvFrame resp = request(endpoint, TlvCommand.CMD_CLOSE_WINDOW, value);
        Map<Byte, List<byte[]>> f = resp.getFields();
        if (!isSuccess(f)) {
            return Result.error(str(f, TlvTag.TAG_ERROR_MSG));
        }
        return Result.success();
    }

    @Override
    public List<SimWindow> getWindows(DeviceEndpoint endpoint) {
        TlvFrame resp = request(endpoint, TlvCommand.CMD_GET_WINDOWS, new byte[0]);
        Map<Byte, List<byte[]>> f = resp.getFields();
        if (!isSuccess(f)) {
            return null;
        }
        List<SimWindow> result = new ArrayList<>();
        for (Map<Byte, List<byte[]>> item : TlvFieldCodec.decodeList(resp.getValue())) {
            SimWindow w = new SimWindow();
            w.setWindowId(str(item, TlvTag.TAG_WINDOW_ID));
            w.setChannelName(str(item, TlvTag.TAG_CHANNEL_NAME));
            w.setX(i(item, TlvTag.TAG_X, 0));
            w.setY(i(item, TlvTag.TAG_Y, 0));
            w.setWidth(i(item, TlvTag.TAG_WIDTH, 1920));
            w.setHeight(i(item, TlvTag.TAG_HEIGHT, 1080));
            w.setSourceType(str(item, TlvTag.TAG_SOURCE_TYPE));
            w.setSourceUrl(str(item, TlvTag.TAG_SOURCE_URL));
            w.setCreateTime(str(item, TlvTag.TAG_CREATE_TIME));
            result.add(w);
        }
        return result;
    }

    @Override
    public Result<Void> notifyWindow(DeviceEndpoint endpoint, List<SimWindow> windows) {
        if (windows == null) {
            windows = List.of();
        }
        byte[] value = TlvFieldCodec.encodeWindowList(windows);
        TlvFrame resp = request(endpoint, TlvCommand.CMD_NOTIFY_WINDOW, value);
        if (!isSuccess(resp.getFields())) {
            return Result.error(str(resp.getFields(), TlvTag.TAG_ERROR_MSG));
        }
        return Result.success();
    }

    // ==================== 解析工具 ====================

    private Result<SimWindow> toWindowResult(TlvFrame resp) {
        Map<Byte, List<byte[]>> f = resp.getFields();
        if (!isSuccess(f)) {
            return Result.error(str(f, TlvTag.TAG_ERROR_MSG));
        }
        SimWindow w = new SimWindow();
        w.setWindowId(str(f, TlvTag.TAG_WINDOW_ID));
        w.setChannelName(str(f, TlvTag.TAG_CHANNEL_NAME));
        w.setX(i(f, TlvTag.TAG_X, 0));
        w.setY(i(f, TlvTag.TAG_Y, 0));
        w.setWidth(i(f, TlvTag.TAG_WIDTH, 1920));
        w.setHeight(i(f, TlvTag.TAG_HEIGHT, 1080));
        w.setSourceType(str(f, TlvTag.TAG_SOURCE_TYPE));
        w.setSourceUrl(str(f, TlvTag.TAG_SOURCE_URL));
        w.setCreateTime(str(f, TlvTag.TAG_CREATE_TIME));
        return Result.success(w);
    }

    /** 依类别把通道名填入 SimDeviceInfo 的 input/outputChannel1..5 */
    private void assignChannels(SimDeviceInfo info, List<String> channels) {
        boolean input = "INPUT".equalsIgnoreCase(info.getDeviceCategory());
        String[] arr = toArray5(channels);
        if (input) {
            info.setInputChannel1(arr[0]);
            info.setInputChannel2(arr[1]);
            info.setInputChannel3(arr[2]);
            info.setInputChannel4(arr[3]);
            info.setInputChannel5(arr[4]);
        } else {
            info.setOutputChannel1(arr[0]);
            info.setOutputChannel2(arr[1]);
            info.setOutputChannel3(arr[2]);
            info.setOutputChannel4(arr[3]);
            info.setOutputChannel5(arr[4]);
        }
    }

    private void assignCapabilityChannels(SimDeviceCapability cap, String category, List<String> channels) {
        boolean input = "INPUT".equalsIgnoreCase(category);
        String[] arr = toArray5(channels);
        if (input) {
            cap.setInputChannel1(arr[0]);
            cap.setInputChannel2(arr[1]);
            cap.setInputChannel3(arr[2]);
            cap.setInputChannel4(arr[3]);
            cap.setInputChannel5(arr[4]);
        } else {
            cap.setOutputChannel1(arr[0]);
            cap.setOutputChannel2(arr[1]);
            cap.setOutputChannel3(arr[2]);
            cap.setOutputChannel4(arr[3]);
            cap.setOutputChannel5(arr[4]);
        }
    }

    private String[] toArray5(List<String> channels) {
        String[] arr = new String[5];
        for (int i = 0; i < arr.length; i++) {
            arr[i] = i < channels.size() ? channels.get(i) : "";
        }
        return arr;
    }

    private static boolean isSuccess(Map<Byte, List<byte[]>> f) {
        Integer code = TlvFieldCodec.getInt32(f, TlvTag.TAG_RESULT_CODE);
        return code != null && code == 1;
    }

    private static String str(Map<Byte, List<byte[]>> f, byte tag) {
        return TlvFieldCodec.getString(f, tag);
    }

    private static int i(Map<Byte, List<byte[]>> f, byte tag, int def) {
        Integer v = TlvFieldCodec.getInt32(f, tag);
        return v == null ? def : v;
    }

    private static boolean b(Map<Byte, List<byte[]>> f, byte tag, boolean def) {
        Boolean v = TlvFieldCodec.getBool(f, tag);
        return v == null ? def : v;
    }

    private static List<String> strings(Map<Byte, List<byte[]>> f, byte tag) {
        return TlvFieldCodec.getStrings(f, tag);
    }

}