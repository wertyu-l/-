package com.example.demo.service;

import com.example.demo.common.DiscoveredNode;
import com.example.demo.mapper.DeviceMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
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
 * 等待 3 秒收集回复，返回发现的设备列表（含是否已添加标记）。
 * <p>
 * 广播地址：255.255.255.255，端口：9999，纯 JDK 实现，无额外依赖。
 */
@Service
public class DeviceDiscoveryService {

    @Autowired
    private DeviceMapper deviceMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 发送 UDP 广播搜索设备
     *
     * @return 发现的设备列表
     */
    public List<DiscoveredNode> discover() {
        List<DiscoveredNode> result = new ArrayList<>();

        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setBroadcast(true);
            socket.setSoTimeout(3000); // 3 秒超时

            // 发送广播
            byte[] requestData = objectMapper.writeValueAsBytes(Map.of("action", "discovery"));
            DatagramPacket requestPacket = new DatagramPacket(
                    requestData, requestData.length,
                    InetAddress.getByName("255.255.255.255"), 9999);
            socket.send(requestPacket);

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
                    if (baseUrl != null && !baseUrl.isEmpty()) {
                        // 检查是否已添加到数据库
                        boolean added = deviceMapper.findByBaseUrl(baseUrl) != null;

                        // 防重复：同一个 baseUrl 只保留一条
                        boolean alreadyInResult = result.stream()
                                .anyMatch(n -> n.getBaseUrl().equals(baseUrl));
                        if (!alreadyInResult) {
                            result.add(new DiscoveredNode(baseUrl, added));
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
