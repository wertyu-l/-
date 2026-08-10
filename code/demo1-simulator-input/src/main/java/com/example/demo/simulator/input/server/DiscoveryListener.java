package com.example.demo.simulator.input.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;

@Component
public class DiscoveryListener {

    @Value("${server.port:8086}")
    private int serverPort;

    @Value("${discovery.port:9999}")
    private int discoveryPort;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private Thread listenerThread;

    private volatile boolean running = true;

    @PostConstruct
    public void start() {
        listenerThread = new Thread(() -> {
            try (DatagramSocket socket = new DatagramSocket(discoveryPort)) {
                socket.setBroadcast(true);
                byte[] buf = new byte[1024];
                while (running) {
                    try {
                        DatagramPacket packet = new DatagramPacket(buf, buf.length);
                        socket.receive(packet);

                        String request = new String(packet.getData(), 0, packet.getLength());
                        Map<String, String> requestMap = objectMapper.readValue(request, Map.class);

                        if ("discovery".equals(requestMap.get("action"))) {
                            String hostAddress = InetAddress.getLocalHost().getHostAddress();
                            String baseUrl = "http://" + hostAddress + ":" + serverPort;

                            Map<String, String> response = new HashMap<>();
                            response.put("baseUrl", baseUrl);

                            byte[] responseData = objectMapper.writeValueAsBytes(response);
                            DatagramPacket responsePacket = new DatagramPacket(
                                    responseData, responseData.length,
                                    packet.getAddress(), packet.getPort());
                            socket.send(responsePacket);
                        }
                    } catch (Exception e) {
                        // 忽略单次异常
                    }
                }
            } catch (Exception e) {
                System.err.println("[DiscoveryListener] 端口 " + discoveryPort + " 绑定失败: " + e.getMessage());
            }
        }, "discovery-listener");
        listenerThread.setDaemon(true);
        listenerThread.start();
    }

    @PreDestroy
    public void stop() {
        running = false;
        if (listenerThread != null) {
            listenerThread.interrupt();
        }
    }

}