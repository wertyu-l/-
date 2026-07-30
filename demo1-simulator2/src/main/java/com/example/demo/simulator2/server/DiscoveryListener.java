package com.example.demo.simulator2.server;

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

/**
 * UDP 设备发现监听器（设备2）
 * <p>
 * 启动时开启守护线程监听端口 9999，接收管控系统广播的 {@code {"action":"discovery"}} 请求，
 * 回复本机 IP + 端口（baseUrl）。
 * <p>
 * 协议：纯 JDK DatagramSocket，JSON 格式，广播地址 255.255.255.255。
 * 一个进程 = 一台设备，回复仅含 baseUrl，不含设备列表。
 */
@Component
public class DiscoveryListener {

    @Value("${server.port:8087}")
    private int serverPort;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private Thread listenerThread;

    private volatile boolean running = true;

    @PostConstruct
    public void start() {
        listenerThread = new Thread(() -> {
            try (DatagramSocket socket = new DatagramSocket(9999)) {
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
                        // 忽略单次解析/发送异常，继续监听下一个包
                    }
                }
            } catch (Exception e) {
                // 端口占用等致命错误，线程退出
            }
        }, "discovery-listener2");
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
