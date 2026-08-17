package com.example.demo.service.impl;

import com.example.demo.common.*;
import com.example.demo.driver.DeviceDriver;
import com.example.demo.driver.DeviceEndpoint;
import com.example.demo.mapper.DeviceMapper;
import com.example.demo.model.SimDeviceCapability;
import com.example.demo.model.SimDeviceInfo;
import com.example.demo.model.SimDeviceStatus;
import com.example.demo.service.DeviceDiscoveryService;
import com.example.demo.service.WindowService;
import com.github.pagehelper.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * DeviceServiceImpl 单元测试
 * <p>
 * 覆盖设备添加、删除、启用/禁用、信息查询、能力查询、状态刷新、心跳检测的完整流程，
 * 重点验证设备类别判定（INPUT/OUTPUT）、通道校验、驱动异常处理、在线状态更新。
 */
@ExtendWith(MockitoExtension.class)
class DeviceServiceImplTest {

    @Mock
    private DeviceMapper deviceMapper;

    @Mock
    private DeviceDriver deviceDriver;

    @Mock
    private DeviceDiscoveryService discoveryService;

    @Mock
    private WindowService windowService;

    @InjectMocks
    private DeviceServiceImpl deviceService;

    private static final String BASE_URL = "http://192.168.1.100:8086";

    private SimDeviceInfo inputDeviceInfo;
    private SimDeviceInfo outputDeviceInfo;
    private SimDeviceCapability capability;
    private DevicePageVO devicePageVO;

    @BeforeEach
    void setUp() {
        inputDeviceInfo = new SimDeviceInfo();
        inputDeviceInfo.setDeviceName("REST-Input-01");
        inputDeviceInfo.setDeviceType("REST");
        inputDeviceInfo.setInputChannel1("HDMI-1");
        inputDeviceInfo.setInputChannel2("HDMI-2");
        inputDeviceInfo.setMaxResolution("1920x1080");

        outputDeviceInfo = new SimDeviceInfo();
        outputDeviceInfo.setDeviceName("REST-Output-01");
        outputDeviceInfo.setDeviceType("REST");
        outputDeviceInfo.setOutputChannel1("OUT-1");
        outputDeviceInfo.setOutputChannel2("OUT-2");
        outputDeviceInfo.setMaxResolution("1920x1080");

        capability = new SimDeviceCapability();
        capability.setMaxWindows(4);
        capability.setSupportMove(true);
        capability.setSupportResize(true);
        capability.setSupportOverlay(true);
        capability.setMaxResolution("1920x1080");

        devicePageVO = new DevicePageVO();
        devicePageVO.setId(1L);
        devicePageVO.setBaseUrl(BASE_URL);
        devicePageVO.setDeviceType("REST");
        devicePageVO.setDeviceName("REST-Output-01");
        devicePageVO.setDeviceCategory("OUTPUT");
        devicePageVO.setEnabled(0);
        devicePageVO.setOnline(1);
        devicePageVO.setMaxWindows(4);
        devicePageVO.setSupportMove(1);
        devicePageVO.setSupportResize(1);
        devicePageVO.setSupportOverlay(1);
        devicePageVO.setMaxResolution("1920x1080");
        devicePageVO.setOutputChannel1("OUT-1");
        devicePageVO.setOutputChannel2("OUT-2");
        devicePageVO.setOutputChannel3("OUT-3");
    }

    // ========== 设备添加：参数校验 ==========

    /**
     * 空字符串 baseUrl 应抛出异常
     */
    @Test
    void addDevice_emptyBaseUrl_shouldThrowException() {
        RuntimeException ex = assertThrows(RuntimeException.class, () -> deviceService.addDevice(""));
        assertEquals("baseUrl不能为空", ex.getMessage());
    }

    /**
     * null baseUrl 应抛出异常（防 NPE）
     */
    @Test
    void addDevice_nullBaseUrl_shouldThrowException() {
        RuntimeException ex = assertThrows(RuntimeException.class, () -> deviceService.addDevice(null));
        assertEquals("baseUrl不能为空", ex.getMessage());
    }

    /**
     * 非 IP+端口 格式的 baseUrl 应抛出异常
     */
    @Test
    void addDevice_invalidFormat_shouldThrowException() {
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> deviceService.addDevice("invalid-url"));
        assertEquals("请使用 IP+端口 格式", ex.getMessage());
    }

    /**
     * 重复添加同一设备应抛出异常
     */
    @Test
    void addDevice_duplicateDevice_shouldThrowException() {
        when(deviceMapper.findByBaseUrl(BASE_URL)).thenReturn(devicePageVO);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> deviceService.addDevice(BASE_URL));
        assertEquals("设备已存在: " + BASE_URL, ex.getMessage());
    }

    // ========== 设备添加：驱动通信 ==========

    /**
     * 驱动连接失败应抛出异常
     */
    @Test
    void addDevice_driverThrowsException_shouldThrowException() {
        when(deviceMapper.findByBaseUrl(BASE_URL)).thenReturn(null);
        when(deviceDriver.getInfo(any(DeviceEndpoint.class)))
                .thenThrow(new RuntimeException("Connection refused"));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> deviceService.addDevice(BASE_URL));
        assertTrue(ex.getMessage().contains("无法连接模拟设备"));
    }

    /**
     * 驱动返回 null 设备信息应抛出异常
     */
    @Test
    void addDevice_infoNull_shouldThrowException() {
        when(deviceMapper.findByBaseUrl(BASE_URL)).thenReturn(null);
        when(deviceDriver.getInfo(any(DeviceEndpoint.class))).thenReturn(null);
        when(deviceDriver.getCapability(any(DeviceEndpoint.class))).thenReturn(capability);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> deviceService.addDevice(BASE_URL));
        assertTrue(ex.getMessage().contains("无法连接模拟设备"));
    }

    // ========== 设备添加：类别判定 ==========

    /**
     * 有输入通道的设备应判定为 INPUT 类别
     */
    @Test
    void addDevice_inputDevice_shouldDetermineCategoryAsINPUT() {
        when(deviceMapper.findByBaseUrl(BASE_URL)).thenReturn(null).thenReturn(devicePageVO);
        when(deviceDriver.getInfo(any(DeviceEndpoint.class))).thenReturn(inputDeviceInfo);
        when(deviceDriver.getCapability(any(DeviceEndpoint.class))).thenReturn(capability);

        deviceService.addDevice(BASE_URL);
        assertEquals("INPUT", inputDeviceInfo.getDeviceCategory());
    }

    /**
     * 有输出通道的设备应判定为 OUTPUT 类别
     */
    @Test
    void addDevice_outputDevice_shouldDetermineCategoryAsOUTPUT() {
        when(deviceMapper.findByBaseUrl(BASE_URL)).thenReturn(null).thenReturn(devicePageVO);
        when(deviceDriver.getInfo(any(DeviceEndpoint.class))).thenReturn(outputDeviceInfo);
        when(deviceDriver.getCapability(any(DeviceEndpoint.class))).thenReturn(capability);

        deviceService.addDevice(BASE_URL);
        assertEquals("OUTPUT", outputDeviceInfo.getDeviceCategory());
    }

    /**
     * 无任何通道的设备应抛出异常
     */
    @Test
    void addDevice_noChannels_shouldThrowException() {
        SimDeviceInfo noChannel = new SimDeviceInfo();
        noChannel.setDeviceName("Empty");
        noChannel.setDeviceType("REST");

        when(deviceMapper.findByBaseUrl(BASE_URL)).thenReturn(null);
        when(deviceDriver.getInfo(any(DeviceEndpoint.class))).thenReturn(noChannel);
        when(deviceDriver.getCapability(any(DeviceEndpoint.class))).thenReturn(capability);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> deviceService.addDevice(BASE_URL));
        assertEquals("设备无任何通道，无法添加", ex.getMessage());
    }

    // ========== 设备删除 ==========

    /**
     * 删除不存在的设备应抛出异常
     */
    @Test
    void deleteDevice_notFound_shouldThrowException() {
        when(deviceMapper.findById(999L)).thenReturn(null);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> deviceService.deleteDevice(999L));
        assertEquals("设备不存在: id=999", ex.getMessage());
    }

    /**
     * 禁止删除已启用的设备（需先禁用）
     */
    @Test
    void deleteDevice_enabledDevice_shouldThrowException() {
        devicePageVO.setEnabled(1);
        when(deviceMapper.findById(1L)).thenReturn(devicePageVO);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> deviceService.deleteDevice(1L));
        assertEquals("设备已启用，请先禁用后再删除", ex.getMessage());
    }

    /**
     * 已禁用的设备可正常删除
     */
    @Test
    void deleteDevice_disabledDevice_shouldSucceed() {
        devicePageVO.setEnabled(0);
        when(deviceMapper.findById(1L)).thenReturn(devicePageVO);

        deviceService.deleteDevice(1L);
        verify(deviceMapper).deleteById(1L);
    }

    // ========== 设备启用/禁用 ==========

    /**
     * 启用/禁用不存在的设备应抛出异常
     */
    @Test
    void setEnabled_deviceNotFound_shouldThrowException() {
        when(deviceMapper.findById(999L)).thenReturn(null);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> deviceService.setEnabled(999L, 1));
        assertEquals("设备不存在: id=999", ex.getMessage());
    }

    /**
     * 重复启用已启用的设备应抛出异常
     */
    @Test
    void setEnabled_alreadyEnabled_shouldThrowException() {
        devicePageVO.setEnabled(1);
        when(deviceMapper.findById(1L)).thenReturn(devicePageVO);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> deviceService.setEnabled(1L, 1));
        assertEquals("设备已是启用状态", ex.getMessage());
    }

    /**
     * 重复禁用已禁用的设备应抛出异常
     */
    @Test
    void setEnabled_alreadyDisabled_shouldThrowException() {
        devicePageVO.setEnabled(0);
        when(deviceMapper.findById(1L)).thenReturn(devicePageVO);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> deviceService.setEnabled(1L, 0));
        assertEquals("设备已是禁用状态", ex.getMessage());
    }

    /**
     * 正常切换启用/禁用状态
     */
    @Test
    void setEnabled_changeState_shouldSucceed() {
        devicePageVO.setEnabled(0);
        when(deviceMapper.findById(1L)).thenReturn(devicePageVO);

        deviceService.setEnabled(1L, 1);
        verify(deviceMapper).updateEnabled(1L, 1);
    }

    // ========== 设备能力查询 ==========

    /**
     * 查询不存在设备的能力应抛出异常
     */
    @Test
    void getDeviceCapability_notFound_shouldThrowException() {
        when(deviceMapper.findById(999L)).thenReturn(null);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> deviceService.getDeviceCapability(999L));
        assertEquals("设备不存在: id=999", ex.getMessage());
    }

    /**
     * 正常查询设备能力，验证各字段正确映射
     */
    @Test
    void getDeviceCapability_shouldReturnCorrectCapability() {
        when(deviceMapper.findById(1L)).thenReturn(devicePageVO);

        SimDeviceCapability result = deviceService.getDeviceCapability(1L);

        assertEquals(4, result.getMaxWindows());
        assertTrue(result.isSupportMove());
        assertTrue(result.isSupportResize());
        assertTrue(result.isSupportOverlay());
        assertEquals("1920x1080", result.getMaxResolution());
        assertEquals("OUT-1", result.getOutputChannel1());
        assertEquals("OUT-2", result.getOutputChannel2());
        assertEquals("OUT-3", result.getOutputChannel3());
    }

    /**
     * 能力字段为 null 时应返回 false（安全默认值）
     */
    @Test
    void getDeviceCapability_nullSupportFields_shouldReturnFalse() {
        devicePageVO.setSupportMove(null);
        devicePageVO.setSupportResize(null);
        devicePageVO.setSupportOverlay(null);
        when(deviceMapper.findById(1L)).thenReturn(devicePageVO);

        SimDeviceCapability result = deviceService.getDeviceCapability(1L);

        assertFalse(result.isSupportMove());
        assertFalse(result.isSupportResize());
        assertFalse(result.isSupportOverlay());
    }

    // ========== 设备信息实时查询 ==========

    /**
     * 查询不存在设备的信息应抛出异常
     */
    @Test
    void getDeviceInfo_notFound_shouldThrowException() {
        when(deviceMapper.findById(999L)).thenReturn(null);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> deviceService.getDeviceInfo(999L));
        assertEquals("设备不存在: id=999", ex.getMessage());
    }

    /**
     * 驱动不可达时应抛出异常
     */
    @Test
    void getDeviceInfo_driverThrowsException_shouldThrowException() {
        when(deviceMapper.findById(1L)).thenReturn(devicePageVO);
        when(deviceDriver.getInfo(any(DeviceEndpoint.class)))
                .thenThrow(new RuntimeException("Connection refused"));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> deviceService.getDeviceInfo(1L));
        assertTrue(ex.getMessage().contains("无法连接模拟设备"));
    }

    // ========== 设备状态查询 ==========

    /**
     * 查询不存在设备的状态应抛出异常
     */
    @Test
    void getDeviceStatus_notFound_shouldThrowException() {
        when(deviceMapper.findById(999L)).thenReturn(null);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> deviceService.getDeviceStatus(999L));
        assertEquals("设备不存在: id=999", ex.getMessage());
    }

    /**
     * 状态查询时驱动不可达应抛出异常
     */
    @Test
    void getDeviceStatus_driverThrowsException_shouldThrowException() {
        when(deviceMapper.findById(1L)).thenReturn(devicePageVO);
        when(deviceDriver.getStatus(any(DeviceEndpoint.class)))
                .thenThrow(new RuntimeException("Connection refused"));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> deviceService.getDeviceStatus(1L));
        assertTrue(ex.getMessage().contains("无法连接模拟设备"));
    }

    // ========== 设备刷新 ==========

    /**
     * 刷新不存在设备应抛出异常
     */
    @Test
    void refreshDevice_notFound_shouldThrowException() {
        when(deviceMapper.findById(999L)).thenReturn(null);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> deviceService.refreshDevice(999L));
        assertEquals("设备不存在: id=999", ex.getMessage());
    }

    /**
     * 刷新时驱动不可达应抛出异常
     */
    @Test
    void refreshDevice_driverThrowsException_shouldThrowException() {
        when(deviceMapper.findById(1L)).thenReturn(devicePageVO);
        when(deviceDriver.getInfo(any(DeviceEndpoint.class)))
                .thenThrow(new RuntimeException("Connection refused"));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> deviceService.refreshDevice(1L));
        assertTrue(ex.getMessage().contains("无法连接模拟设备"));
    }

    // ========== 设备发现 ==========

    /**
     * 设备发现应委托给 DiscoveryService
     */
    @Test
    void discover_shouldDelegateToDiscoveryService() {
        List<DiscoveredNode> nodes = new ArrayList<>();
        nodes.add(new DiscoveredNode(BASE_URL, "INPUT", false));
        when(discoveryService.discover()).thenReturn(nodes);

        List<DiscoveredNode> result = deviceService.discover();
        assertEquals(1, result.size());
        assertEquals(BASE_URL, result.get(0).getBaseUrl());
    }

    // ========== 心跳检测：在线状态更新 ==========

    /**
     * 无设备时不应执行任何更新操作
     */
    @Test
    void updateOnlineStatus_noDevices_shouldReturn() {
        when(deviceMapper.findAll()).thenReturn(new ArrayList<>());

        deviceService.updateOnlineStatus();
        verify(deviceMapper, never()).updateOnline(anyLong(), anyInt(), any());
    }

    /**
     * 设备恢复在线时应更新状态并触发窗口补推
     */
    @Test
    void updateOnlineStatus_deviceOnline_shouldUpdateStatus() {
        devicePageVO.setOnline(0);
        List<DevicePageVO> devices = List.of(devicePageVO);
        when(deviceMapper.findAll()).thenReturn(devices);

        SimDeviceStatus status = new SimDeviceStatus();
        status.setOnline(true);
        when(deviceDriver.getStatus(any(DeviceEndpoint.class))).thenReturn(status);

        deviceService.updateOnlineStatus();

        verify(deviceMapper).updateOnline(eq(1L), eq(1), any(LocalDateTime.class));
        verify(windowService).markPendingForDevice(devicePageVO);
    }

    /**
     * 设备离线时应更新状态为离线
     */
    @Test
    void updateOnlineStatus_deviceOffline_shouldUpdateStatus() {
        devicePageVO.setOnline(1);
        List<DevicePageVO> devices = List.of(devicePageVO);
        when(deviceMapper.findAll()).thenReturn(devices);

        SimDeviceStatus status = new SimDeviceStatus();
        status.setOnline(false);
        when(deviceDriver.getStatus(any(DeviceEndpoint.class))).thenReturn(status);

        deviceService.updateOnlineStatus();

        verify(deviceMapper).updateOnline(eq(1L), eq(0), any(LocalDateTime.class));
        verify(windowService).markFailedForDevice(devicePageVO);
    }

    /**
     * 心跳检测异常时应将设备标记为离线（容错处理）
     */
    @Test
    void updateOnlineStatus_driverException_shouldMarkOffline() {
        List<DevicePageVO> devices = List.of(devicePageVO);
        when(deviceMapper.findAll()).thenReturn(devices);
        when(deviceDriver.getStatus(any(DeviceEndpoint.class)))
                .thenThrow(new RuntimeException("Connection refused"));

        deviceService.updateOnlineStatus();

        verify(deviceMapper).updateOnline(eq(1L), eq(0), any(LocalDateTime.class));
        verify(windowService).markFailedForDevice(devicePageVO);
    }

    // ========== 设备分页查询 ==========

    /**
     * 分页查询应返回正确的分页结果
     */
    @SuppressWarnings("unchecked")
    @Test
    void getPage_shouldReturnPageResult() {
        DevicePageDTO pageDTO = new DevicePageDTO();
        pageDTO.setPage(1);
        pageDTO.setPageSize(10);

        Page<DevicePageVO> mockPage = mock(Page.class);
        when(mockPage.getTotal()).thenReturn(1L);
        when(mockPage.getResult()).thenReturn(List.of(devicePageVO));
        when(deviceMapper.pageQuery(pageDTO)).thenReturn(mockPage);

        PageResult<DevicePageVO> result = deviceService.getPage(pageDTO);
        assertEquals(1L, result.getTotal());
        assertEquals(1, result.getRecords().size());
    }
}