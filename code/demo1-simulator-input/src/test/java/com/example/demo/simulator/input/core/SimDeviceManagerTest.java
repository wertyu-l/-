package com.example.demo.simulator.input.core;

import com.example.demo.model.SimDeviceCapability;
import com.example.demo.model.SimDeviceInfo;
import com.example.demo.model.SimDeviceStatus;
import com.example.demo.model.SimWindow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * SimDeviceManager（输入模拟器）单元测试
 * <p>
 * 覆盖输入模拟设备的窗口快照反馈（notify）、状态查询、设备信息/能力查询的完整流程，
 * 重点验证输入通道校验（HDMI-1/HDMI-2）、快照整体替换、通道播放地址管理。
 * 输入设备作为被动信号源，不再提供窗口的增删改查。
 */
@ExtendWith(MockitoExtension.class)
class SimDeviceManagerTest {

    @Mock
    private DeviceRepository repo;

    private SimDeviceManager manager;

    @BeforeEach
    void setUp() {
        SimDeviceInfo info = new SimDeviceInfo();
        info.setDeviceName("REST-Input-01");
        info.setDeviceType("REST");
        info.setDeviceCategory("INPUT");
        info.setInputChannel1("HDMI-1");
        info.setInputChannel2("HDMI-2");

        SimDeviceCapability cap = new SimDeviceCapability();
        cap.setChannelCount(5);
        cap.setMaxWindows(4);

        when(repo.loadDeviceInfo()).thenReturn(info);
        when(repo.loadDeviceCapability()).thenReturn(cap);
        manager = new SimDeviceManager(repo);
    }

    // ========== 窗口快照反馈（notify） ==========

    /**
     * notify 应用完整快照整体替换本地窗口列表
     */
    @Test
    void notifyWindows_shouldReplaceAll() {
        SimWindow w1 = new SimWindow();
        w1.setWindowId("win-001");
        w1.setChannelName("HDMI-1");
        SimWindow w2 = new SimWindow();
        w2.setWindowId("win-002");
        w2.setChannelName("HDMI-2");
        List<SimWindow> snapshot = List.of(w1, w2);

        manager.notifyWindows(snapshot);

        verify(repo).replaceAllWindows(snapshot);
    }

    /**
     * notify 空列表表示全部窗口关闭
     */
    @Test
    void notifyWindows_empty_shouldReplaceWithEmpty() {
        manager.notifyWindows(List.of());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SimWindow>> captor = ArgumentCaptor.forClass(List.class);
        verify(repo).replaceAllWindows(captor.capture());
        assertTrue(captor.getValue().isEmpty());
    }

    /**
     * notify 为 null 时视为空列表
     */
    @Test
    void notifyWindows_null_shouldTreatAsEmpty() {
        manager.notifyWindows(null);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SimWindow>> captor = ArgumentCaptor.forClass(List.class);
        verify(repo).replaceAllWindows(captor.capture());
        assertTrue(captor.getValue().isEmpty());
    }

    /**
     * notify 时为空 sourceType 的窗口应根据通道名推断信号源类型
     * <p>sourceUrl 由用户通过 setChannelUrl 单独配置，notify 不会自动填充
     */
    @Test
    void notifyWindows_shouldFillSourceTypeFromChannelName() {
        SimWindow w = new SimWindow();
        w.setWindowId("win-001");
        w.setChannelName("HDMI-1");

        manager.notifyWindows(List.of(w));

        assertEquals("HDMI", w.getSourceType());
        assertNull(w.getSourceUrl());
    }

    // ========== 设备状态查询 ==========

    /**
     * 正常查询设备状态，应返回在线状态和窗口数量
     */
    @Test
    void getDeviceStatus_shouldReturnStatus() {
        when(repo.countWindows()).thenReturn(3);
        SimDeviceStatus status = manager.getDeviceStatus();
        assertTrue(status.isOnline());
        assertEquals(3, status.getWindowCount());
        assertNotNull(status.getUptime());
    }

    // ========== 输入通道校验 ==========

    /**
     * 有效的输入通道（HDMI-1, HDMI-2）应返回 true
     */
    @Test
    void isValidInputChannel_valid_shouldReturnTrue() {
        assertTrue(manager.isValidInputChannel("HDMI-1"));
        assertTrue(manager.isValidInputChannel("HDMI-2"));
    }

    /**
     * 无效的输入通道应返回 false
     */
    @Test
    void isValidInputChannel_invalid_shouldReturnFalse() {
        assertFalse(manager.isValidInputChannel("HDMI-3"));
        assertFalse(manager.isValidInputChannel("OUT-1"));
    }

    /**
     * null 通道名应返回 false（防 NPE）
     */
    @Test
    void isValidInputChannel_null_shouldReturnFalse() {
        assertFalse(manager.isValidInputChannel(null));
    }

    /**
     * 空字符串通道名应返回 false
     */
    @Test
    void isValidInputChannel_empty_shouldReturnFalse() {
        assertFalse(manager.isValidInputChannel(""));
    }

    // ========== 设备信息查询 ==========

    /**
     * 正常查询设备信息，验证名称和类别
     */
    @Test
    void getDeviceInfo_shouldReturnInfo() {
        SimDeviceInfo info = manager.getDeviceInfo();
        assertEquals("REST-Input-01", info.getDeviceName());
        assertEquals("INPUT", info.getDeviceCategory());
    }

    /**
     * 正常查询设备能力
     */
    @Test
    void getDeviceCapability_shouldReturnCapability() {
        SimDeviceCapability cap = manager.getDeviceCapability();
        assertNotNull(cap);
    }

    // ========== 设备能力更新 ==========

    /**
     * 更新设备能力应修改所有字段并持久化
     */
    @Test
    void updateDeviceCapability_shouldUpdateFields() {
        SimDeviceCapability newCap = new SimDeviceCapability();
        newCap.setMaxResolution("3840x2160");
        newCap.setChannelCount(2);
        SimDeviceCapability result = manager.updateDeviceCapability(newCap);
        assertEquals("3840x2160", result.getMaxResolution());
        assertEquals(2, result.getChannelCount());
        verify(repo).updateCapability(result);
    }

    // ========== 通道 URL 管理 ==========

    /**
     * 设置通道 URL 应委托给 Repository
     */
    @Test
    void setChannelUrl_shouldDelegate() {
        manager.setChannelUrl("HDMI-1", "rtsp://example.com/stream");
        verify(repo).setChannelUrl("HDMI-1", "rtsp://example.com/stream");
    }

    /**
     * 获取所有通道 URL 应委托给 Repository
     */
    @Test
    void getChannelUrls_shouldDelegate() {
        when(repo.getAllChannelUrls()).thenReturn(java.util.Map.of("HDMI-1", "url1"));
        assertEquals(1, manager.getChannelUrls().size());
    }

    // ========== 窗口列表查询 ==========

    /**
     * 查询所有窗口应返回窗口列表
     */
    @Test
    void getWindows_shouldReturnList() {
        when(repo.findAllWindows()).thenReturn(List.of(new SimWindow()));
        assertEquals(1, manager.getWindows().size());
    }
}