package com.example.demo.service.impl;

import com.example.demo.common.*;
import com.example.demo.driver.DeviceDriver;
import com.example.demo.driver.DeviceEndpoint;
import com.example.demo.mapper.DeviceMapper;
import com.example.demo.model.SimDeviceCapability;
import com.example.demo.model.SimDeviceInfo;
import com.example.demo.model.SimDeviceStatus;
import com.example.demo.service.DeviceDiscoveryService;
import com.example.demo.service.DeviceService;
import com.example.demo.service.WindowService;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 设备管理 Service 实现
 * <p>
 * 设备类别（deviceCategory）由管控系统根据模拟设备返回的通道信息自动判定：
 * - 输入设备（INPUT）：inputChannel1 非空 → 拥有输入通道，提供信号源
 * - 输出设备（OUTPUT）：outputChannel1 或 outputChannel2 非空 → 拥有输出通道，用于大屏绑定
 */
@Service
public class DeviceServiceImpl implements DeviceService {

    @Autowired
    private DeviceMapper deviceMapper;

    @Autowired
    private DeviceDriver deviceDriver;

    @Autowired
    private DeviceDiscoveryService discoveryService;

    @Autowired
    private WindowService windowService;

    /**
     * 手动添加设备
     * <p>
     * 校验 baseUrl 合法性 → 查重 → 连接模拟设备拉取设备信息与能力 → 自动判定设备类别 → 写入数据库。
     *
     * @param baseUrl 设备 REST API 基地址
     * @return 添加后的设备完整信息（含自增 id）
     * @throws RuntimeException 参数为空、设备已存在、连接失败时抛出
     */
    @Override
    @Transactional
    public DevicePageVO addDevice(String baseUrl) {
        // 1. 校验 baseUrl
        if (!StringUtils.hasText(baseUrl)) {
            throw new RuntimeException("baseUrl不能为空");
        }
        // 只允许 IP+端口 格式（REST: http/https，TLV: udp）
        if (!baseUrl.matches("^(https?|udp)://\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}:\\d+$")) {
            throw new RuntimeException("请使用 IP+端口 格式");
        }

        // 2. 查重
        DevicePageVO existing = deviceMapper.findByBaseUrl(baseUrl);
        if (existing != null) {
            throw new RuntimeException("设备已存在: " + baseUrl);
        }

        // 3. 构建 DeviceEndpoint，连接模拟设备拉取设备信息与能力
        DeviceEndpoint endpoint = new DeviceEndpoint();
        endpoint.setDeviceType(baseUrl.startsWith("udp://") ? "TLV" : "REST");
        endpoint.setBaseUrl(baseUrl);

        SimDeviceInfo info;
        SimDeviceCapability capability;
        try {
            info = deviceDriver.getInfo(endpoint);
            capability = deviceDriver.getCapability(endpoint);
        } catch (Exception e) {
            throw new RuntimeException("无法连接模拟设备: " + baseUrl + "，请检查设备是否在线");
        }
        if (info == null || capability == null) {
            throw new RuntimeException("无法连接模拟设备: " + baseUrl + "，请检查设备是否在线");
        }

        // 4. 自动判定设备类别（若模拟设备未返回 deviceCategory）
        if (info.getDeviceCategory() == null || info.getDeviceCategory().isEmpty()) {
            info.setDeviceCategory(determineDeviceCategory(info));
        }

        // 5. 写入数据库
        deviceMapper.insert(info, capability, baseUrl);

        // 6. 查询返回（拿自增 id）
        return deviceMapper.findByBaseUrl(baseUrl);
    }

    /**
     * 自动判定设备类别
     * <p>
     * 规则：inputChannel1 非空 → INPUT（输入设备），否则 outputChannel1 或 outputChannel2 非空 → OUTPUT（输出设备），
     * 都不非空则报错。
     */
    private String determineDeviceCategory(SimDeviceInfo info) {
        boolean hasInput = StringUtils.hasText(info.getInputChannel1());
        boolean hasOutput = StringUtils.hasText(info.getOutputChannel1())
                || StringUtils.hasText(info.getOutputChannel2());
        if (hasInput) {
            return "INPUT";
        } else if (hasOutput) {
            return "OUTPUT";
        } else {
            throw new RuntimeException("设备无任何通道，无法添加");
        }
    }

    /**
     * 删除设备（需先禁用）
     *
     * @param id 设备主键
     * @throws RuntimeException 设备不存在或未禁用时抛出
     */
    @Override
    public void deleteDevice(Long id) {
        DevicePageVO device = deviceMapper.findById(id);
        if (device == null) {
            throw new RuntimeException("设备不存在: id=" + id);
        }
        if (device.getEnabled() != null && device.getEnabled() == 1) {
            throw new RuntimeException("设备已启用，请先禁用后再删除");
        }
        deviceMapper.deleteById(id);
    }

    /**
     * 启用/禁用设备
     *
     * @param id      设备主键
     * @param enabled 1=启用，0=禁用
     * @throws RuntimeException 设备不存在或已是目标状态时抛出
     */
    @Override
    public void setEnabled(Long id, int enabled) {
        DevicePageVO device = deviceMapper.findById(id);
        if (device == null) {
            throw new RuntimeException("设备不存在: id=" + id);
        }
        if (device.getEnabled() != null && device.getEnabled() == enabled) {
            throw new RuntimeException(enabled == 1 ? "设备已是启用状态" : "设备已是禁用状态");
        }
        deviceMapper.updateEnabled(id, enabled);
    }

    /**
     * 刷新设备信息（从模拟设备重新拉取信息与能力并更新数据库）
     *
     * @param id 设备主键
     * @return 更新后的设备完整信息（含自增 id、baseUrl 等）
     * @throws RuntimeException 设备不存在或连接失败时抛出
     */
    @Override
    @Transactional
    public DevicePageVO refreshDevice(Long id) {
        DevicePageVO device = deviceMapper.findById(id);
        if (device == null) {
            throw new RuntimeException("设备不存在: id=" + id);
        }

        DeviceEndpoint endpoint = buildEndpoint(device);
        SimDeviceInfo info;
        SimDeviceCapability capability;
        try {
            info = deviceDriver.getInfo(endpoint);
            capability = deviceDriver.getCapability(endpoint);
        } catch (Exception e) {
            throw new RuntimeException("无法连接模拟设备: " + device.getBaseUrl() + "，请检查设备是否在线");
        }
        if (info == null || capability == null) {
            throw new RuntimeException("无法连接模拟设备: " + device.getBaseUrl() + "，请检查设备是否在线");
        }

        // 自动判定设备类别（若模拟设备未返回）
        if (info.getDeviceCategory() == null || info.getDeviceCategory().isEmpty()) {
            info.setDeviceCategory(determineDeviceCategory(info));
        }

        deviceMapper.updateDeviceInfo(id, info, capability);
        return deviceMapper.findById(id);
    }

    /**
     * 更新所有设备在线状态（心跳检测）
     * <p>
     * 遍历数据库中所有设备，逐个请求模拟设备状态接口，
     * 请求成功且返回在线 → 更新 online=1，否则 → 更新 online=0。
     */
    @Override
    public void updateOnlineStatus() {
        List<DevicePageVO> allDevices = deviceMapper.findAll();
        if (allDevices == null || allDevices.isEmpty()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        for (DevicePageVO device : allDevices) {
            DeviceEndpoint endpoint = buildEndpoint(device);
            try {
                SimDeviceStatus status = deviceDriver.getStatus(endpoint);
                if (status != null && status.isOnline()) {
                    if (device.getOnline() == null || device.getOnline() == 0) {
                        windowService.markPendingForDevice(device);
                    }
                    deviceMapper.updateOnline(device.getId(), 1, now);
                } else {
                    // 在线 → 离线：标记窗口为 failed + 降级
                    if (device.getOnline() != null && device.getOnline() == 1) {
                        windowService.markFailedForDevice(device);
                    }
                    deviceMapper.updateOnline(device.getId(), 0, now);
                }
            } catch (Exception e) {
                // 在线 → 离线（连接异常）：标记窗口为 failed + 降级
                if (device.getOnline() != null && device.getOnline() == 1) {
                    windowService.markFailedForDevice(device);
                }
                deviceMapper.updateOnline(device.getId(), 0, now);
            }
        }
    }

    /**
     * 分页查询设备列表
     *
     * @param pageDTO 分页查询条件（页码、每页数量、设备名称模糊搜索、设备类型精确匹配）
     * @return 分页结果（含 id、baseUrl、online 等字段）
     */
    @Override
    public PageResult<DevicePageVO> getPage(DevicePageDTO pageDTO) {
        PageHelper.startPage(pageDTO.getPage(), pageDTO.getPageSize());
        Page<DevicePageVO> page = deviceMapper.pageQuery(pageDTO);
        return new PageResult<>(page.getTotal(), page.getResult());
    }

    /**
     * 获取设备基本信息（实时查询模拟设备）
     *
     * @param id 设备主键
     * @return 设备描述信息
     * @throws RuntimeException 设备不存在或连接失败时抛出
     */
    @Override
    public SimDeviceInfo getDeviceInfo(Long id) {
        DevicePageVO device = deviceMapper.findById(id);
        if (device == null) {
            throw new RuntimeException("设备不存在: id=" + id);
        }

        DeviceEndpoint endpoint = buildEndpoint(device);
        SimDeviceInfo info;
        try {
            info = deviceDriver.getInfo(endpoint);
        } catch (Exception e) {
            throw new RuntimeException("无法连接模拟设备: " + device.getBaseUrl() + "，请检查设备是否在线");
        }
        if (info == null) {
            throw new RuntimeException("无法连接模拟设备: " + device.getBaseUrl() + "，请检查设备是否在线");
        }
        return info;
    }

    /**
     * 获取设备运行状态（实时查询模拟设备）
     *
     * @param id 设备主键
     * @return 设备运行状态（在线状态、窗口数、启动时间）
     * @throws RuntimeException 设备不存在或连接失败时抛出
     */
    @Override
    public SimDeviceStatus getDeviceStatus(Long id) {
        DevicePageVO device = deviceMapper.findById(id);
        if (device == null) {
            throw new RuntimeException("设备不存在: id=" + id);
        }

        DeviceEndpoint endpoint = buildEndpoint(device);
        SimDeviceStatus status;
        try {
            status = deviceDriver.getStatus(endpoint);
        } catch (Exception e) {
            throw new RuntimeException("无法连接模拟设备: " + device.getBaseUrl() + "，请检查设备是否在线");
        }
        if (status == null) {
            throw new RuntimeException("无法连接模拟设备: " + device.getBaseUrl() + "，请检查设备是否在线");
        }
        return status;
    }

    /**
     * 获取设备能力（从数据库 DEVICE 表返回能力字段）
     *
     * @param id 设备主键
     * @return 设备能力
     * @throws RuntimeException 设备不存在时抛出
     */
    @Override
    public SimDeviceCapability getDeviceCapability(Long id) {
        DevicePageVO device = deviceMapper.findById(id);
        if (device == null) {
            throw new RuntimeException("设备不存在: id=" + id);
        }

        SimDeviceCapability capability = new SimDeviceCapability();
        capability.setMaxWindows(device.getMaxWindows());
        capability.setSupportMove(device.getSupportMove() != null && device.getSupportMove() == 1);
        capability.setSupportResize(device.getSupportResize() != null && device.getSupportResize() == 1);
        capability.setSupportOverlay(device.getSupportOverlay() != null && device.getSupportOverlay() == 1);
        capability.setMaxResolution(device.getMaxResolution());
        capability.setInputChannel1(device.getInputChannel1());
        capability.setInputChannel2(device.getInputChannel2());
        capability.setOutputChannel1(device.getOutputChannel1());
        capability.setOutputChannel2(device.getOutputChannel2());
        capability.setOutputChannel3(device.getOutputChannel3());
        return capability;
    }

    /**
     * UDP 广播搜索设备
     *
     * @return 发现的设备列表，含是否已添加标记
     */
    @Override
    public List<DiscoveredNode> discover() {
        return discoveryService.discover();
    }

    /**
     * 根据数据库查询结果构建 DeviceEndpoint
     */
    private DeviceEndpoint buildEndpoint(DevicePageVO device) {
        DeviceEndpoint endpoint = new DeviceEndpoint();
        endpoint.setDeviceType(device.getDeviceType());
        endpoint.setBaseUrl(device.getBaseUrl());
        return endpoint;
    }

    /**
     * 心跳检测定时任务（每 30 秒）
     * <p>
     * 遍历所有设备，通过 {@link DeviceDriver#getStatus(DeviceEndpoint)}
     * 请求模拟设备状态接口（REST 超时 3 秒 / TLV 超时 3 秒），根据结果更新数据库 online 字段。
     */
    @Scheduled(fixedRate = 30000)
    public void heartbeat() {
        updateOnlineStatus();
    }

}