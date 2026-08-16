package com.example.demo.simulator.output.core;

import com.example.demo.model.SimDeviceCapability;
import com.example.demo.model.SimDeviceInfo;
import com.example.demo.model.SimDeviceStatus;
import com.example.demo.model.SimWindow;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 输出设备核心管理器
 * <p>
 * 管理设备信息、能力、子窗口的内存缓存，并与 H2 数据库同步。
 * 输出设备通过 supportMove/supportResize/supportOverlay 声明设备能力，
 * 管控系统据此做布局校验，设备端不执行移动/缩放/叠加逻辑。
 * 管控系统将大屏窗口按单元拆分后，推送子窗口到各输出设备。
 */
@Component
public class SimDeviceManager {

    private final SimDeviceInfo deviceInfo;
    private final SimDeviceCapability deviceCapability;
    private final DeviceRepository repo;
    private final LocalDateTime startTime = LocalDateTime.now();

    /**
     * 构造时从数据库加载设备信息和能力到内存，
     * 同步 maxWindows 到 deviceInfo
     */
    public SimDeviceManager(DeviceRepository repo) {
        this.repo = repo;
        this.deviceInfo = repo.loadDeviceInfo();
        this.deviceCapability = repo.loadDeviceCapability();
        this.deviceInfo.setMaxWindows(this.deviceCapability.getMaxWindows());
    }

    /**
     * 获取设备基本信息（内存缓存）
     */
    public SimDeviceInfo getDeviceInfo() {
        return deviceInfo;
    }

    /**
     * 从数据库重新加载设备信息
     */
    public SimDeviceInfo getDeviceInfoFromDb() {
        return repo.loadDeviceInfo();
    }

    /**
     * 获取设备运行状态
     * <p>
     * 返回在线状态、当前子窗口数量、设备启动时间。
     */
    public SimDeviceStatus getDeviceStatus() {
        SimDeviceStatus status = new SimDeviceStatus();
        status.setOnline(true);
        status.setWindowCount(repo.countWindows());
        status.setUptime(startTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        return status;
    }

    /**
     * 获取设备能力（内存缓存）
     * <p>
     * 包含 maxWindows、supportMove、supportResize、supportOverlay 等能力声明。
     */
    public SimDeviceCapability getDeviceCapability() {
        return deviceCapability;
    }

    /**
     * 从数据库重新加载设备能力
     */
    public SimDeviceCapability getDeviceCapabilityFromDb() {
        return repo.loadDeviceCapability();
    }

    /**
     * 更新设备能力（运行时动态变更）
     * <p>
     * 更新内存缓存和数据库，同时同步 deviceInfo 中的通道信息。
     * 支持修改能力开关（supportMove/supportResize/supportOverlay）、
     * 最大窗口数、通道配置等。
     *
     * @param newCapability 新的能力配置
     * @return 更新后的能力对象
     */
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

    /**
     * 校验输出通道名是否有效
     *
     * @param channelName 通道名称
     * @return true 表示该通道已定义
     */
    public boolean isValidOutputChannel(String channelName) {
        if (channelName == null || channelName.isEmpty()) return false;
        return channelName.equals(deviceInfo.getOutputChannel1())
                || channelName.equals(deviceInfo.getOutputChannel2())
                || channelName.equals(deviceInfo.getOutputChannel3())
                || channelName.equals(deviceInfo.getOutputChannel4())
                || channelName.equals(deviceInfo.getOutputChannel5());
    }

    /**
     * 创建子窗口（管控系统下发）
     * <p>
     * 管控系统将大屏窗口按单元拆分后，推送到各输出设备。
     * 校验通道有效性、窗口 ID 唯一性，自动补全默认值，存入内存并返回。
     *
     * @param window 子窗口信息
     * @return 创建成功返回窗口对象，ID 重复或通道无效返回 null
     */
    public SimWindow createWindow(SimWindow window) {
        if (repo.findByWindowIdAndChannel(window.getWindowId(), window.getChannelName()) != null) {
            return null;
        }
        if (!isValidOutputChannel(window.getChannelName())) {
            return null;
        }
        if (repo.countWindowsByChannel(window.getChannelName()) >= deviceCapability.getMaxWindows()) {
            return null;
        }
        if (window.getX() == null) window.setX(0);
        if (window.getY() == null) window.setY(0);
        if (window.getWidth() == null) window.setWidth(1920);
        if (window.getHeight() == null) window.setHeight(1080);
        if (window.getSourceType() == null || window.getSourceType().isEmpty()) {
            window.setSourceType("");
        }
        window.setCreateTime(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        repo.insertWindow(window);
        return window;
    }

    /**
     * 查询单个子窗口
     *
     * @param windowId 窗口唯一标识
     * @return 子窗口对象，不存在返回 null
     */
    public SimWindow getWindow(String windowId) {
        return repo.findWindowById(windowId);
    }

    /**
     * 查询所有子窗口
     *
     * @return 当前设备内存中的全部子窗口列表
     */
    public List<SimWindow> getWindows() {
        return repo.findAllWindows();
    }

    /**
     * 统计指定输出通道上的子窗口数
     *
     * @param channelName 输出通道名
     * @return 该通道上的子窗口数量
     */
    public int countWindowsByChannel(String channelName) {
        return repo.countWindowsByChannel(channelName);
    }

    /**
     * 更新子窗口位置/大小（管控系统下发）
     * <p>
     * 校验设备能力：坐标变化需 supportMove，尺寸变化需 supportResize，
     * 不支持对应能力时返回 null。
     *
     * @param windowId 窗口唯一标识
     * @param update   更新参数（x、y、width、height），null 表示不修改
     * @return 更新后的窗口对象，窗口不存在或能力不支持返回 null
     */
    public SimWindow updateWindow(String windowId, SimWindow update) {
        SimWindow existing = repo.findWindowById(windowId);
        if (existing == null) {
            return null;
        }
        boolean moveRequested = (update.getX() != null && !update.getX().equals(existing.getX()))
                || (update.getY() != null && !update.getY().equals(existing.getY()));
        boolean resizeRequested = (update.getWidth() != null && !update.getWidth().equals(existing.getWidth()))
                || (update.getHeight() != null && !update.getHeight().equals(existing.getHeight()));
        if (moveRequested && !deviceCapability.isSupportMove()) {
            return null;
        }
        if (resizeRequested && !deviceCapability.isSupportResize()) {
            return null;
        }
        if (update.getX() != null) existing.setX(update.getX());
        if (update.getY() != null) existing.setY(update.getY());
        if (update.getWidth() != null) existing.setWidth(update.getWidth());
        if (update.getHeight() != null) existing.setHeight(update.getHeight());
        repo.updateWindow(existing);
        return existing;
    }

    /**
     * 关闭子窗口（从内存中移除）
     *
     * @param windowId 窗口唯一标识
     * @return true 表示删除成功，false 表示窗口不存在
     */
    public boolean closeWindow(String windowId) {
        return repo.deleteWindow(windowId);
    }

}