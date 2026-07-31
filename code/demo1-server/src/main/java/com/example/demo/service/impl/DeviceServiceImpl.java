package com.example.demo.service.impl;

import com.example.demo.common.*;
import com.example.demo.driver.DeviceEndpoint;
import com.example.demo.driver.RestDeviceDriver;
import com.example.demo.mapper.DeviceMapper;
import com.example.demo.model.SimDeviceInfo;
import com.example.demo.model.SimDeviceStatus;
import com.example.demo.service.DeviceDiscoveryService;
import com.example.demo.service.DeviceService;
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
 */
@Service
public class DeviceServiceImpl implements DeviceService {

    @Autowired
    private DeviceMapper deviceMapper;

    @Autowired
    private RestDeviceDriver restDeviceDriver;

    @Autowired
    private DeviceDiscoveryService discoveryService;

    /**
     * 手动添加设备
     * <p>
     * 校验 baseUrl 合法性 → 查重 → 连接模拟设备拉取设备描述信息 → 写入数据库。
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
        // 只允许 IP+端口 格式
        if (!baseUrl.matches("^https?://\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}:\\d+$")) {
            throw new RuntimeException("请使用 IP+端口 格式");
        }

        // 2. 查重
        DevicePageVO existing = deviceMapper.findByBaseUrl(baseUrl);
        if (existing != null) {
            throw new RuntimeException("设备已存在: " + baseUrl);
        }

        // 3. 构建 DeviceEndpoint，连接模拟设备拉取设备信息
        DeviceEndpoint endpoint = new DeviceEndpoint();
        endpoint.setDeviceType("REST");
        endpoint.setBaseUrl(baseUrl);

        SimDeviceInfo info;
        try {
            info = restDeviceDriver.getInfo(endpoint);
        } catch (Exception e) {
            throw new RuntimeException("无法连接模拟设备: " + baseUrl + "，请检查设备是否在线");
        }
        if (info == null) {
            throw new RuntimeException("无法连接模拟设备: " + baseUrl + "，请检查设备是否在线");
        }

        // 4. 写入数据库
        deviceMapper.insert(info, baseUrl);

        // 5. 查询返回（拿自增 id）
        return deviceMapper.findByBaseUrl(baseUrl);
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
     * 刷新设备信息（从模拟设备重新拉取并更新数据库）
     *
     * @param id 设备主键
     * @return 最新的设备描述信息
     * @throws RuntimeException 设备不存在或连接失败时抛出
     */
    @Override
    @Transactional
    public SimDeviceInfo refreshDevice(Long id) {
        DevicePageVO device = deviceMapper.findById(id);
        if (device == null) {
            throw new RuntimeException("设备不存在: id=" + id);
        }

        DeviceEndpoint endpoint = buildEndpoint(device);
        SimDeviceInfo info;
        try {
            info = restDeviceDriver.getInfo(endpoint);
        } catch (Exception e) {
            throw new RuntimeException("无法连接模拟设备: " + device.getBaseUrl() + "，请检查设备是否在线");
        }
        if (info == null) {
            throw new RuntimeException("无法连接模拟设备: " + device.getBaseUrl() + "，请检查设备是否在线");
        }

        deviceMapper.updateDeviceInfo(id, info);
        return info;
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
                SimDeviceStatus status = restDeviceDriver.getStatus(endpoint);
                if (status != null && status.isOnline()) {
                    deviceMapper.updateOnline(device.getId(), 1, now);
                } else {
                    deviceMapper.updateOnline(device.getId(), 0, now);
                }
            } catch (Exception e) {
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
     * 获取设备基本信息（查数据库）
     *
     * @param id 设备主键
     * @return 设备描述信息
     * @throws RuntimeException 设备不存在时抛出
     */
    @Override
    public SimDeviceInfo getDeviceInfo(Long id) {
        DevicePageVO device = deviceMapper.findById(id);
        if (device == null) {
            throw new RuntimeException("设备不存在: id=" + id);
        }
        return device;
    }

    /**
     * 获取设备运行状态（查数据库）
     *
     * @param id 设备主键
     * @return 设备运行状态（在线状态、窗口数、启动时间）
     * @throws RuntimeException 设备不存在时抛出
     */
    @Override
    public SimDeviceStatus getDeviceStatus(Long id) {
        DevicePageVO device = deviceMapper.findById(id);
        if (device == null) {
            throw new RuntimeException("设备不存在: id=" + id);
        }

        SimDeviceStatus status = new SimDeviceStatus();
        status.setOnline(device.getOnline() != null && device.getOnline() == 1);
        status.setWindowCount(0);
        status.setUptime("");
        return status;
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
     * 遍历所有设备，通过 {@link RestDeviceDriver#getStatus(DeviceEndpoint)}
     * 请求模拟设备状态接口（connect/read 超时各 3 秒），根据结果更新数据库 online 字段。
     */
    @Scheduled(fixedRate = 30000)
    public void heartbeat() {
        updateOnlineStatus();
    }

}