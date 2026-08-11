package com.example.demo.simulator.output.core;

import com.example.demo.model.SimDeviceCapability;
import com.example.demo.model.SimDeviceInfo;
import com.example.demo.model.SimDeviceStatus;
import com.example.demo.model.SimWindow;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
public class SimDeviceManager {

    private final SimDeviceInfo deviceInfo;
    private final SimDeviceCapability deviceCapability;
    private final DeviceRepository repo;
    private final LocalDateTime startTime = LocalDateTime.now();

    public SimDeviceManager(DeviceRepository repo) {
        this.repo = repo;
        this.deviceInfo = repo.loadDeviceInfo();
        this.deviceCapability = repo.loadDeviceCapability();
        this.deviceInfo.setMaxWindows(this.deviceCapability.getMaxWindows());
    }

    public SimDeviceInfo getDeviceInfo() {
        return deviceInfo;
    }

    public SimDeviceInfo getDeviceInfoFromDb() {
        return repo.loadDeviceInfo();
    }

    public SimDeviceStatus getDeviceStatus() {
        SimDeviceStatus status = new SimDeviceStatus();
        status.setOnline(true);
        status.setWindowCount(repo.countWindows());
        status.setUptime(startTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        return status;
    }

    public SimDeviceCapability getDeviceCapability() {
        return deviceCapability;
    }

    public SimDeviceCapability getDeviceCapabilityFromDb() {
        return repo.loadDeviceCapability();
    }

    public SimDeviceCapability updateDeviceCapability(SimDeviceCapability newCapability) {
        deviceCapability.setSupportMove(newCapability.isSupportMove());
        deviceCapability.setSupportResize(newCapability.isSupportResize());
        deviceCapability.setSupportOverlay(newCapability.isSupportOverlay());
        deviceCapability.setMaxWindows(newCapability.getMaxWindows());
        deviceCapability.setMaxResolution(newCapability.getMaxResolution());
        deviceCapability.setChannelCount(newCapability.getChannelCount());
        deviceCapability.setOutputChannel1(newCapability.getOutputChannel1());
        deviceCapability.setOutputChannel2(newCapability.getOutputChannel2());
        deviceCapability.setOutputChannel3(newCapability.getOutputChannel3());
        deviceCapability.setOutputChannel4(newCapability.getOutputChannel4());
        deviceCapability.setOutputChannel5(newCapability.getOutputChannel5());
        repo.updateCapability(deviceCapability);
        repo.updateDeviceInfoFromCapability(deviceCapability);
        deviceInfo.setChannelCount(newCapability.getChannelCount());
        deviceInfo.setOutputChannel1(newCapability.getOutputChannel1());
        deviceInfo.setOutputChannel2(newCapability.getOutputChannel2());
        deviceInfo.setOutputChannel3(newCapability.getOutputChannel3());
        deviceInfo.setOutputChannel4(newCapability.getOutputChannel4());
        deviceInfo.setOutputChannel5(newCapability.getOutputChannel5());
        deviceInfo.setMaxResolution(newCapability.getMaxResolution());
        deviceInfo.setMaxWindows(newCapability.getMaxWindows());
        return deviceCapability;
    }

    public boolean isValidOutputChannel(String channelName) {
        if (channelName == null || channelName.isEmpty()) return false;
        return channelName.equals(deviceInfo.getOutputChannel1())
                || channelName.equals(deviceInfo.getOutputChannel2())
                || channelName.equals(deviceInfo.getOutputChannel3())
                || channelName.equals(deviceInfo.getOutputChannel4())
                || channelName.equals(deviceInfo.getOutputChannel5());
    }

    public SimWindow createWindow(SimWindow window) {
        if (repo.findByWindowIdAndChannel(window.getWindowId(), window.getChannelName()) != null) {
            return null;
        }
        if (!isValidOutputChannel(window.getChannelName())) {
            return null;
        }
        if (window.getX() == null) window.setX(0);
        if (window.getY() == null) window.setY(0);
        if (window.getWidth() == null) window.setWidth(1920);
        if (window.getHeight() == null) window.setHeight(1080);
        if (window.getSourceUrl() == null || window.getSourceUrl().isEmpty()) {
            String channelUrl = repo.getChannelUrl(window.getChannelName());
            window.setSourceUrl(channelUrl != null ? channelUrl : "");
        }
        if (window.getSourceType() == null || window.getSourceType().isEmpty()) {
            window.setSourceType("");
        }
        window.setCreateTime(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        repo.insertWindow(window);
        return window;
    }

    public SimWindow getWindow(String windowId) {
        return repo.findWindowById(windowId);
    }

    public List<SimWindow> getWindows() {
        return repo.findAllWindows();
    }

    public boolean closeWindow(String windowId) {
        return repo.deleteWindow(windowId);
    }

    public SimWindow updateWindow(String windowId, SimWindow update) {
        SimWindow existing = repo.findWindowById(windowId);
        if (existing == null) {
            return null;
        }
        boolean moveRequested = update.getX() != null || update.getY() != null;
        if (moveRequested) {
            if (!deviceCapability.isSupportMove()) {
                return null;
            }
            if (update.getX() != null) existing.setX(update.getX());
            if (update.getY() != null) existing.setY(update.getY());
        }
        boolean resizeRequested = update.getWidth() != null || update.getHeight() != null;
        if (resizeRequested) {
            if (!deviceCapability.isSupportResize()) {
                return null;
            }
            if (update.getWidth() != null) existing.setWidth(update.getWidth());
            if (update.getHeight() != null) existing.setHeight(update.getHeight());
        }
        repo.updateWindow(existing);
        return existing;
    }

    public void setChannelUrl(String channelName, String sourceUrl) {
        repo.setChannelUrl(channelName, sourceUrl);
    }

    public java.util.Map<String, String> getChannelUrls() {
        return repo.getAllChannelUrls();
    }

}