package com.example.demo.simulator.tlv.output.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * UDP 设备发现监听。
 * <p>
 * 监听发现端口，收到 {@code {"action":"discovery"}} 广播后，
 * 单播回复 {@code {"deviceType":"TLV","baseUrl":"udp://ip:port"}}。
 */
@Component
public class DiscoveryListener {

    @Value("${port:8092}")
    private int port;

    @Value("${discoveryPort:9993}")
    private int discoveryPort;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private volatile boolean running = true;
    private Thread thread;

    @PostConstruct
    public void start() {
        thread = new Thread(() -> {
            try (DatagramSocket socket = new DatagramSocket(discoveryPort)) {
                socket.setBroadcast(true);
                byte[] buf = new byte[1024];
                while (running) {
                    try {
                        DatagramPacket packet = new DatagramPacket(buf, buf.length);
                        socket.receive(packet);

                        String request = new String(packet.getData(), packet.getOffset(), packet.getLength(), StandardCharsets.UTF_8);
                        Map<String, String> requestMap = objectMapper.readValue(request, Map.class);

                        if ("discovery".equals(requestMap.get("action"))) {
                            String host = InetAddress.getLocalHost().getHostAddress();
                            String baseUrl = "udp://" + host + ":" + port;

                            Map<String, String> response = new HashMap<>();
                            response.put("deviceType", "TLV");
                            response.put("baseUrl", baseUrl);

                            byte[] data = objectMapper.writeValueAsBytes(response);
                            socket.send(new DatagramPacket(data, data.length, packet.getAddress(), packet.getPort()));
                        }
                    } catch (Exception e) {
                        // 忽略单次异常
                    }
                }
            } catch (Exception e) {
                System.err.println("[DiscoveryListener] 发现端口 " + discoveryPort + " 绑定失败: " + e.getMessage());
            }
        }, "discovery-listener-" + discoveryPort);
        thread.setDaemon(true);
        thread.start();
    }

    @PreDestroy
    public void stop() {
        running = false;
        if (thread != null) {
            thread.interrupt();
        }
    }

}
