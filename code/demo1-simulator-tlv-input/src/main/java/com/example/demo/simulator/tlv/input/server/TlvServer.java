package com.example.demo.simulator.tlv.input.server;

import com.example.demo.codec.TlvDecoder;
import com.example.demo.codec.TlvEncoder;
import com.example.demo.codec.TlvFrame;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * UDP 服务端：监听端口，接收 TLV 帧并分发到对应命令处理器。
 * <p>
 * 收包 → {@link TlvDecoder#decode} 校验 Magic/CRC → 按 type 分发 → 编码响应（Seq 原样回填）→ 回源地址。
 * 解码失败（非协议包 / CRC 错误）返回 RESP_ERROR 而非静默丢弃，避免管控系统超时误判离线；未知命令返回 RESP_ERROR。
 */
@Component
public class TlvServer {

    private final int port;
    private final Map<Integer, TlvCommandHandler> handlers = new HashMap<>();

    private volatile boolean running = true;
    private Thread thread;

    public TlvServer(@Value("${port:8090}") int port, List<TlvCommandHandler> handlerList) {
        this.port = port;
        for (TlvCommandHandler h : handlerList) {
            handlers.put(h.commandType(), h);
        }
    }

    @PostConstruct
    public void start() {
        thread = new Thread(() -> {
            try (DatagramSocket socket = new DatagramSocket(port)) {
                socket.setReceiveBufferSize(256 * 1024);
                byte[] buf = new byte[65535];
                while (running) {
                    try {
                        DatagramPacket packet = new DatagramPacket(buf, buf.length);
                        socket.receive(packet);
                        byte[] data = new byte[packet.getLength()];
                        System.arraycopy(packet.getData(), packet.getOffset(), data, 0, packet.getLength());
                        byte[] response = handle(data);
                        if (response != null) {
                            DatagramPacket resp = new DatagramPacket(
                                    response, response.length, packet.getAddress(), packet.getPort());
                            socket.send(resp);
                        }
                    } catch (Exception e) {
                        // 忽略单次异常，继续服务
                    }
                }
            } catch (Exception e) {
                System.err.println("[TlvServer] 端口 " + port + " 绑定失败: " + e.getMessage());
            }
        }, "tlv-server-" + port);
        thread.setDaemon(true);
        thread.start();
    }

    private byte[] handle(byte[] data) {
        try {
            TlvFrame request = TlvDecoder.decode(data);
            TlvCommandHandler handler = handlers.get(request.getType());
            TlvFrame response = handler == null ? TlvResponses.error("未知命令") : handler.handle(request);
            return TlvEncoder.encode(request.getSeq(), response);
        } catch (Exception e) {
            return TlvEncoder.encode(0, TlvResponses.error("请求解码失败"));
        }
    }

    @PreDestroy
    public void stop() {
        running = false;
        if (thread != null) {
            thread.interrupt();
        }
    }

}