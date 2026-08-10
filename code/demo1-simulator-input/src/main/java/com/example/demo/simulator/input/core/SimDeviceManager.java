package com.example.demo.simulator.input.core;

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
    }

    public SimDeviceInfo getDeviceInfo() {
        return deviceInfo;
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

    public SimDeviceCapability updateDeviceCapability(SimDeviceCapability newCapability) {
        deviceCapability.setSupportMove(newCapability.isSupportMove());
        deviceCapability.setSupportResize(newCapability.isSupportResize());
        deviceCapability.setSupportOverlay(newCapability.isSupportOverlay());
        deviceCapability.setMaxResolution(newCapability.getMaxResolution());
        deviceCapability.setChannelCount(newCapability.getChannelCount());
        deviceCapability.setInputChannel1(newCapability.getInputChannel1());
        deviceCapability.setInputChannel2(newCapability.getInputChannel2());
        deviceCapability.setInputChannel3(newCapability.getInputChannel3());
        deviceCapability.setInputChannel4(newCapability.getInputChannel4());
        deviceCapability.setInputChannel5(newCapability.getInputChannel5());
        repo.updateCapability(deviceCapability);
        repo.updateDeviceInfoFromCapability(deviceCapability);
        deviceInfo.setChannelCount(newCapability.getChannelCount());
        deviceInfo.setInputChannel1(newCapability.getInputChannel1());
        deviceInfo.setInputChannel2(newCapability.getInputChannel2());
        deviceInfo.setInputChannel3(newCapability.getInputChannel3());
        deviceInfo.setInputChannel4(newCapability.getInputChannel4());
        deviceInfo.setInputChannel5(newCapability.getInputChannel5());
        deviceInfo.setMaxResolution(newCapability.getMaxResolution());
        return deviceCapability;
    }

    public boolean isValidInputChannel(String channelName) {
        if (channelName == null || channelName.isEmpty()) return false;
        return channelName.equals(deviceInfo.getInputChannel1())
                || channelName.equals(deviceInfo.getInputChannel2())
                || channelName.equals(deviceInfo.getInputChannel3())
                || channelName.equals(deviceInfo.getInputChannel4())
                || channelName.equals(deviceInfo.getInputChannel5());
    }

    public SimWindow createWindow(SimWindow window) {
        if (repo.findByWindowIdAndChannel(window.getWindowId(), window.getChannelName()) != null) {
            return null;
        }
        if (!isValidInputChannel(window.getChannelName())) {
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
            window.setSourceType(inferSourceType(window.getChannelName()));
        }
        window.setCreateTime(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        repo.insertWindow(window);
        return window;
    }

    private String inferSourceType(String channelName) {
        if (channelName == null) return "";
        String upper = channelName.toUpperCase();
        if (upper.startsWith("HDMI")) return "HDMI";
        if (upper.startsWith("VGA")) return "VGA";
        if (upper.startsWith("DP")) return "DP";
        if (upper.startsWith("SDI")) return "SDI";
        return "Stream";
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