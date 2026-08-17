package com.example.demo.simulator.tlv.input.core;

import com.example.demo.model.SimDeviceCapability;
import com.example.demo.model.SimDeviceInfo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;

/**
 * 设备配置加载器。
 * <p>
 * 从 classpath 下的 {@code device-{port}.json} 读取设备信息与能力到内存，
 * 一个进程对应一台设备。端口通过 {@code ${port}} 注入。
 */
@Component
public class DeviceConfig {

    private final SimDeviceInfo deviceInfo;
    private final SimDeviceCapability deviceCapability;

    public DeviceConfig(@Value("${port:8090}") int port) {
        String file = "device-" + port + ".json";
        ObjectMapper mapper = new ObjectMapper();
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(file)) {
            if (in == null) {
                throw new IllegalStateException("设备配置文件不存在: " + file);
            }
            JsonNode root = mapper.readTree(in);
            this.deviceInfo = mapper.treeToValue(root.get("deviceInfo"), SimDeviceInfo.class);
            this.deviceCapability = mapper.treeToValue(root.get("deviceCapability"), SimDeviceCapability.class);
        } catch (IOException e) {
            throw new IllegalStateException("设备配置加载失败: " + file, e);
        }
    }

    public SimDeviceInfo getDeviceInfo() {
        return deviceInfo;
    }

    public SimDeviceCapability getDeviceCapability() {
        return deviceCapability;
    }

}
