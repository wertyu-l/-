package com.example.demo.simulator.tlv.output.core;

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

    private final int port;
    private final ObjectMapper mapper = new ObjectMapper();

    public DeviceConfig(@Value("${port:8092}") int port) {
        this.port = port;
    }

    private JsonNode loadRoot() {
        String file = "device-" + port + ".json";
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(file)) {
            if (in == null) {
                throw new IllegalStateException("设备配置文件不存在: " + file);
            }
            return mapper.readTree(in);
        } catch (IOException e) {
            throw new IllegalStateException("设备配置加载失败: " + file, e);
        }
    }

    public SimDeviceInfo getDeviceInfo() {
        try {
            return mapper.treeToValue(loadRoot().get("deviceInfo"), SimDeviceInfo.class);
        } catch (IOException e) {
            throw new RuntimeException("设备信息解析失败", e);
        }
    }

    public SimDeviceCapability getDeviceCapability() {
        try {
            return mapper.treeToValue(loadRoot().get("deviceCapability"), SimDeviceCapability.class);
        } catch (IOException e) {
            throw new RuntimeException("设备能力解析失败", e);
        }
    }

}