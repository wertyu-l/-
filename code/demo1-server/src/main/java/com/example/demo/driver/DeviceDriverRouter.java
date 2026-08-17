package com.example.demo.driver;

import com.example.demo.common.Result;
import com.example.demo.model.SimDeviceCapability;
import com.example.demo.model.SimDeviceInfo;
import com.example.demo.model.SimDeviceStatus;
import com.example.demo.model.SimWindow;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 设备驱动路由器。
 * <p>
 * 作为 {@link DeviceDriver} 的 {@link Primary} 实现，按 {@link DeviceEndpoint#deviceType}
 * 分发到具体驱动：{@code "TLV"} → {@link TlvDeviceDriver}，否则 → {@link RestDeviceDriver}。
 * 业务代码注入 {@code DeviceDriver} 时即拿到本路由，无需感知具体设备协议。
 */
@Component
@Primary
public class DeviceDriverRouter implements DeviceDriver {

    private final RestDeviceDriver restDeviceDriver;
    private final TlvDeviceDriver tlvDeviceDriver;

    public DeviceDriverRouter(RestDeviceDriver restDeviceDriver, TlvDeviceDriver tlvDeviceDriver) {
        this.restDeviceDriver = restDeviceDriver;
        this.tlvDeviceDriver = tlvDeviceDriver;
    }

    private DeviceDriver driverFor(DeviceEndpoint endpoint) {
        return "TLV".equalsIgnoreCase(endpoint.getDeviceType()) ? tlvDeviceDriver : restDeviceDriver;
    }

    @Override
    public SimDeviceInfo getInfo(DeviceEndpoint endpoint) {
        return driverFor(endpoint).getInfo(endpoint);
    }

    @Override
    public SimDeviceCapability getCapability(DeviceEndpoint endpoint) {
        return driverFor(endpoint).getCapability(endpoint);
    }

    @Override
    public SimDeviceStatus getStatus(DeviceEndpoint endpoint) {
        return driverFor(endpoint).getStatus(endpoint);
    }

    @Override
    public Result<SimWindow> createWindow(DeviceEndpoint endpoint, SimWindow window) {
        return driverFor(endpoint).createWindow(endpoint, window);
    }

    @Override
    public Result<SimWindow> updateWindow(DeviceEndpoint endpoint, String windowId, SimWindow update) {
        return driverFor(endpoint).updateWindow(endpoint, windowId, update);
    }

    @Override
    public Result<Void> closeWindow(DeviceEndpoint endpoint, String windowId) {
        return driverFor(endpoint).closeWindow(endpoint, windowId);
    }

    @Override
    public List<SimWindow> getWindows(DeviceEndpoint endpoint) {
        return driverFor(endpoint).getWindows(endpoint);
    }

    @Override
    public Result<Void> notifyWindow(DeviceEndpoint endpoint, List<SimWindow> windows) {
        return driverFor(endpoint).notifyWindow(endpoint, windows);
    }

}
