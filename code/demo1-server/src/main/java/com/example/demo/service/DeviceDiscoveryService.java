package com.example.demo.service;

import com.example.demo.common.DiscoveredNode;
import com.example.demo.mapper.DeviceMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 设备发现服务
 * <p>
 * 通过 UDP 广播向局域网内所有模拟设备发送 {@code {"action":"discovery"}} 搜索请求，
 * 向多个发现端口广播，等待 3 秒收集回复，返回发现的设备列表（含是否已添加标记）。
 * <p>
 * 广播地址：255.255.255.255，纯 JDK 实现，无额外依赖。
 */
@Service
public class DeviceDiscoveryService {

    @Autowired
    private DeviceMapper deviceMapper;

    @Value("${discovery.ports:9997,9999}")
    private String discoveryPorts;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 解析端口配置，支持单端口和范围格式
     * 例如: "20000-20010,30000-30010" 或 "20000,20001,30000-30005"
     */
    private List<Integer> parsePorts(String config) {
        List<Integer> result = new ArrayList<>();
        String[] parts = config.split(",");
        for (String part : parts) {
            part = part.trim();
            if (part.contains("-")) {
                String[] range = part.split("-");
                int start = Integer.parseInt(range[0].trim());
                int end = Integer.parseInt(range[1].trim());
                for (int p = start; p <= end; p++) {
                    result.add(p);
                }
            } else {
                result.add(Integer.parseInt(part));
            }
        }
        return result;
    }

    /**
     * 发送 UDP 广播搜索设备
     *
     * @return 发现的设备列表
     */
    public List<DiscoveredNode> discover() {
        List<DiscoveredNode> result = new ArrayList<>();

        List<Integer> ports = parsePorts(discoveryPorts);

        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setBroadcast(true);
            socket.setSoTimeout(3000); // 3 秒超时

            byte[] requestData = objectMapper.writeValueAsBytes(Map.of("action", "discovery"));

            // 向每个发现端口发送广播
            for (int port : ports) {
                try {
                    DatagramPacket requestPacket = new DatagramPacket(
                            requestData, requestData.length,
                            InetAddress.getByName("255.255.255.255"), port);
                    socket.send(requestPacket);
                } catch (Exception e) {
                    System.err.println("[DeviceDiscovery] 向端口 " + port + " 广播失败: " + e.getMessage());
                }
            }

            // 收集回复
            byte[] buf = new byte[1024];
            long deadline = System.currentTimeMillis() + 3000;
            while (System.currentTimeMillis() < deadline) {
                try {
                    // 动态计算剩余超时时间
                    int remaining = (int) (deadline - System.currentTimeMillis());
                    if (remaining <= 0) break;
                    socket.setSoTimeout(Math.max(remaining, 100));

                    DatagramPacket responsePacket = new DatagramPacket(buf, buf.length);
                    socket.receive(responsePacket);

                    String responseJson = new String(responsePacket.getData(), 0, responsePacket.getLength());
                    Map<String, String> responseMap = objectMapper.readValue(responseJson, Map.class);

                    String baseUrl = responseMap.get("baseUrl");
                    String deviceType = responseMap.get("deviceType");
                    if (baseUrl != null && !baseUrl.isEmpty()) {
                        // 检查是否已添加到数据库
                        boolean added = deviceMapper.findByBaseUrl(baseUrl) != null;

                        // 防重复：同一个 baseUrl 只保留一条
                        boolean alreadyInResult = result.stream()
                                .anyMatch(n -> n.getBaseUrl().equals(baseUrl));
                        if (!alreadyInResult) {
                            result.add(new DiscoveredNode(baseUrl, deviceType, added));
                        }
                    }
                } catch (java.net.SocketTimeoutException e) {
                    // 超时，退出循环
                    break;
                } catch (Exception e) {
                    // 忽略单次解析异常，继续等待下一个回复
                }
            }
        } catch (Exception e) {
            // 广播发送失败（如网络不可达），返回空列表
        }

        return result;
    }

}