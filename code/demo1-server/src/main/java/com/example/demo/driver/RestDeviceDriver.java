package com.example.demo.driver;

import com.example.demo.common.Result;
import com.example.demo.model.SimDeviceCapability;
import com.example.demo.model.SimDeviceInfo;
import com.example.demo.model.SimDeviceStatus;
import com.example.demo.model.SimWindow;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;

/**
 * REST 设备驱动实现
 * <p>
 * 通过 HTTP 调用独立运行的 demo1-simulator 进程。
 * 一个模拟设备进程 = 一台设备，接口路径不再携带 deviceId。
 * baseUrl（含端口）即设备唯一标识。
 */
@Component
public class RestDeviceDriver implements DeviceDriver {

    private final RestTemplate restTemplate;

    public RestDeviceDriver() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(3000);
        this.restTemplate = new RestTemplate(factory);
    }

    /**
     * 获取设备基本信息
     * <p>
     * 请求 GET {baseUrl}/simulator/device/info
     */
    @Override
    public SimDeviceInfo getInfo(DeviceEndpoint endpoint) {
        String url = endpoint.getBaseUrl() + "/simulator/device/info";
        ResponseEntity<Result<SimDeviceInfo>> response = restTemplate.exchange(
                url, HttpMethod.GET, null,
                new ParameterizedTypeReference<Result<SimDeviceInfo>>() {});
        if (response.getBody() != null && response.getBody().getCode() == 1) {
            return response.getBody().getData();
        }
        return null;
    }

    /**
     * 获取设备能力
     * <p>
     * 请求 GET {baseUrl}/simulator/device/capability
     */
    @Override
    public SimDeviceCapability getCapability(DeviceEndpoint endpoint) {
        String url = endpoint.getBaseUrl() + "/simulator/device/capability";
        ResponseEntity<Result<SimDeviceCapability>> response = restTemplate.exchange(
                url, HttpMethod.GET, null,
                new ParameterizedTypeReference<Result<SimDeviceCapability>>() {});
        if (response.getBody() != null && response.getBody().getCode() == 1) {
            return response.getBody().getData();
        }
        return null;
    }

    /**
     * 获取设备运行状态
     * <p>
     * 请求 GET {baseUrl}/simulator/device/status
     */
    @Override
    public SimDeviceStatus getStatus(DeviceEndpoint endpoint) {
        String url = endpoint.getBaseUrl() + "/simulator/device/status";
        ResponseEntity<Result<SimDeviceStatus>> response = restTemplate.exchange(
                url, HttpMethod.GET, null,
                new ParameterizedTypeReference<Result<SimDeviceStatus>>() {});
        if (response.getBody() != null && response.getBody().getCode() == 1) {
            return response.getBody().getData();
        }
        return null;
    }

    /**
     * 创建窗口
     * <p>
     * 请求 POST {baseUrl}/simulator/device/window
     */
    @Override
    public Result<SimWindow> createWindow(DeviceEndpoint endpoint, SimWindow window) {
        String url = endpoint.getBaseUrl() + "/simulator/device/window";
        HttpEntity<SimWindow> request = new HttpEntity<>(window);
        ResponseEntity<Result<SimWindow>> response = restTemplate.exchange(
                url, HttpMethod.POST, request,
                new ParameterizedTypeReference<Result<SimWindow>>() {});
        if (response.getBody() != null) {
            return response.getBody();
        }
        return Result.error("请求失败");
    }

    /**
     * 更新窗口位置/大小
     * <p>
     * 请求 PUT {baseUrl}/simulator/device/window/{windowId}
     */
    @Override
    public Result<SimWindow> updateWindow(DeviceEndpoint endpoint, String windowId, SimWindow update) {
        String url = endpoint.getBaseUrl() + "/simulator/device/window/" + windowId;
        HttpEntity<SimWindow> request = new HttpEntity<>(update);
        ResponseEntity<Result<SimWindow>> response = restTemplate.exchange(
                url, HttpMethod.PUT, request,
                new ParameterizedTypeReference<Result<SimWindow>>() {});
        if (response.getBody() != null) {
            return response.getBody();
        }
        return Result.error("请求失败");
    }

    /**
     * 关闭窗口
     * <p>
     * 请求 DELETE {baseUrl}/simulator/device/window/{windowId}
     */
    @Override
    public Result<Void> closeWindow(DeviceEndpoint endpoint, String windowId) {
        String url = endpoint.getBaseUrl() + "/simulator/device/window/" + windowId;
        ResponseEntity<Result<Void>> response = restTemplate.exchange(
                url, HttpMethod.DELETE, null,
                new ParameterizedTypeReference<Result<Void>>() {});
        if (response.getBody() != null) {
            return response.getBody();
        }
        return Result.error("请求失败");
    }

    /**
     * 查询设备所有窗口
     * <p>
     * 请求 GET {baseUrl}/simulator/device/windows
     */
    @Override
    public List<SimWindow> getWindows(DeviceEndpoint endpoint) {
        String url = endpoint.getBaseUrl() + "/simulator/device/windows";
        ResponseEntity<Result<List<SimWindow>>> response = restTemplate.exchange(
                url, HttpMethod.GET, null,
                new ParameterizedTypeReference<Result<List<SimWindow>>>() {});
        if (response.getBody() != null && response.getBody().getCode() == 1) {
            return response.getBody().getData();
        }
        return null;
    }

    /**
     * 推送输入设备窗口快照
     * <p>
     * 请求 POST {baseUrl}/simulator/device/window/notify，body 为完整窗口列表。
     */
    @Override
    public Result<Void> notifyWindow(DeviceEndpoint endpoint, List<SimWindow> windows) {
        String url = endpoint.getBaseUrl() + "/simulator/device/window/notify";
        HttpEntity<List<SimWindow>> request = new HttpEntity<>(windows);
        ResponseEntity<Result<Void>> response = restTemplate.exchange(
                url, HttpMethod.POST, request,
                new ParameterizedTypeReference<Result<Void>>() {});
        if (response.getBody() != null) {
            return response.getBody();
        }
        return Result.error("请求失败");
    }

}