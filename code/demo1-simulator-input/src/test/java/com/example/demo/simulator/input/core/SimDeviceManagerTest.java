package com.example.demo.simulator.input.core;

import com.example.demo.model.SimDeviceCapability;
import com.example.demo.model.SimDeviceInfo;
import com.example.demo.model.SimDeviceStatus;
import com.example.demo.model.SimWindow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * SimDeviceManager（输入模拟器）单元测试
 * <p>
 * 覆盖输入模拟设备的窗口创建、更新、删除、状态查询、设备信息/能力查询的完整流程，
 * 重点验证输入通道校验（HDMI-1/HDMI-2）、能力开关（移动/缩放）、重复窗口检测。
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
        cap.setSupportMove(true);
        cap.setSupportResize(true);
        cap.setSupportOverlay(true);
        cap.setChannelCount(5);
        cap.setMaxWindows(4);

        when(repo.loadDeviceInfo()).thenReturn(info);
        when(repo.loadDeviceCapability()).thenReturn(cap);
        manager = new SimDeviceManager(repo);
    }

    // ========== 窗口创建 ==========

    /**
     * 正常创建窗口应返回包含 windowId 和创建时间的结果
     */
    @Test
    void createWindow_valid_shouldSucceed() {
        SimWindow window = new SimWindow();
        window.setWindowId("win-001");
        window.setChannelName("HDMI-1");
        when(repo.findByWindowIdAndChannel("win-001", "HDMI-1")).thenReturn(null);
        SimWindow result = manager.createWindow(window);
        assertNotNull(result);
        assertEquals("win-001", result.getWindowId());
        assertEquals("HDMI-1", result.getChannelName());
        assertNotNull(result.getCreateTime());
        verify(repo).insertWindow(window);
    }

    /**
     * 重复创建同窗口应返回 null
     */
    @Test
    void createWindow_duplicate_shouldReturnNull() {
        SimWindow window = new SimWindow();
        window.setWindowId("win-001");
        window.setChannelName("HDMI-1");
        when(repo.findByWindowIdAndChannel("win-001", "HDMI-1")).thenReturn(new SimWindow());
        assertNull(manager.createWindow(window));
    }

    /**
     * 使用无效通道创建窗口应返回 null
     */
    @Test
    void createWindow_invalidChannel_shouldReturnNull() {
        SimWindow window = new SimWindow();
        window.setWindowId("win-001");
        window.setChannelName("INVALID");
        when(repo.findByWindowIdAndChannel("win-001", "INVALID")).thenReturn(null);
        assertNull(manager.createWindow(window));
    }

    /**
     * 未指定尺寸/位置时应使用默认值（1920x1080, 原点）
     */
    @Test
    void createWindow_nullDefaults_shouldSetDefaults() {
        SimWindow window = new SimWindow();
        window.setWindowId("win-001");
        window.setChannelName("HDMI-1");
        when(repo.findByWindowIdAndChannel("win-001", "HDMI-1")).thenReturn(null);
        SimWindow result = manager.createWindow(window);
        assertEquals(0, result.getX());
        assertEquals(0, result.getY());
        assertEquals(1920, result.getWidth());
        assertEquals(1080, result.getHeight());
    }

    /**
     * 创建窗口时应从通道获取信号源 URL
     */
    @Test
    void createWindow_sourceUrlFromChannel_shouldUseChannelUrl() {
        SimWindow window = new SimWindow();
        window.setWindowId("win-001");
        window.setChannelName("HDMI-1");
        when(repo.findByWindowIdAndChannel("win-001", "HDMI-1")).thenReturn(null);
        when(repo.getChannelUrl("HDMI-1")).thenReturn("rtsp://camera1/stream");
        SimWindow result = manager.createWindow(window);
        assertEquals("rtsp://camera1/stream", result.getSourceUrl());
    }

    /**
     * HDMI 通道的窗口应自动推断 sourceType 为 HDMI
     */
    @Test
    void createWindow_sourceTypeHDMI_shouldInferHDMI() {
        SimWindow window = new SimWindow();
        window.setWindowId("win-001");
        window.setChannelName("HDMI-1");
        when(repo.findByWindowIdAndChannel("win-001", "HDMI-1")).thenReturn(null);
        SimWindow result = manager.createWindow(window);
        assertEquals("HDMI", result.getSourceType());
    }

    /**
     * HDMI-2 通道的窗口也应推断为 HDMI 类型
     */
    @Test
    void createWindow_sourceTypeHDMI2_shouldInferHDMI() {
        SimWindow window = new SimWindow();
        window.setWindowId("win-001");
        window.setChannelName("HDMI-2");
        when(repo.findByWindowIdAndChannel("win-001", "HDMI-2")).thenReturn(null);
        SimWindow result = manager.createWindow(window);
        assertEquals("HDMI", result.getSourceType());
    }

    // ========== 窗口更新 ==========

    /**
     * 更新不存在的窗口应返回 null
     */
    @Test
    void updateWindow_notFound_shouldReturnNull() {
        when(repo.findWindowById("win-999")).thenReturn(null);
        SimWindow update = new SimWindow();
        update.setX(100);
        assertNull(manager.updateWindow("win-999", update));
    }

    /**
     * 设备不支持移动时，移动操作应返回 null
     */
    @Test
    void updateWindow_moveNotSupported_shouldReturnNull() {
        SimDeviceCapability cap = manager.getDeviceCapability();
        cap.setSupportMove(false);
        SimWindow existing = new SimWindow();
        existing.setWindowId("win-001");
        existing.setChannelName("HDMI-1");
        when(repo.findWindowById("win-001")).thenReturn(existing);
        SimWindow update = new SimWindow();
        update.setX(100);
        assertNull(manager.updateWindow("win-001", update));
    }

    /**
     * 设备不支持缩放时，缩放操作应返回 null
     */
    @Test
    void updateWindow_resizeNotSupported_shouldReturnNull() {
        SimDeviceCapability cap = manager.getDeviceCapability();
        cap.setSupportResize(false);
        SimWindow existing = new SimWindow();
        existing.setWindowId("win-001");
        existing.setChannelName("HDMI-1");
        when(repo.findWindowById("win-001")).thenReturn(existing);
        SimWindow update = new SimWindow();
        update.setWidth(800);
        assertNull(manager.updateWindow("win-001", update));
    }

    /**
     * 正常更新窗口应成功修改坐标
     */
    @Test
    void updateWindow_success_shouldUpdate() {
        SimWindow existing = new SimWindow();
        existing.setWindowId("win-001");
        existing.setChannelName("HDMI-1");
        existing.setX(0);
        existing.setY(0);
        when(repo.findWindowById("win-001")).thenReturn(existing);
        SimWindow update = new SimWindow();
        update.setX(100);
        update.setY(200);
        SimWindow result = manager.updateWindow("win-001", update);
        assertNotNull(result);
        assertEquals(100, result.getX());
        assertEquals(200, result.getY());
        verify(repo).updateWindow(existing);
    }

    // ========== 窗口关闭/删除 ==========

    /**
     * 关闭存在的窗口应返回 true
     */
    @Test
    void closeWindow_success_shouldReturnTrue() {
        when(repo.deleteWindow("win-001")).thenReturn(true);
        assertTrue(manager.closeWindow("win-001"));
    }

    /**
     * 关闭不存在的窗口应返回 false
     */
    @Test
    void closeWindow_notFound_shouldReturnFalse() {
        when(repo.deleteWindow("win-999")).thenReturn(false);
        assertFalse(manager.closeWindow("win-999"));
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
        assertTrue(cap.isSupportMove());
        assertTrue(cap.isSupportResize());
    }

    // ========== 设备能力更新 ==========

    /**
     * 更新设备能力应修改所有字段并持久化
     */
    @Test
    void updateDeviceCapability_shouldUpdateFields() {
        SimDeviceCapability newCap = new SimDeviceCapability();
        newCap.setSupportMove(false);
        newCap.setSupportResize(false);
        newCap.setSupportOverlay(false);
        SimDeviceCapability result = manager.updateDeviceCapability(newCap);
        assertFalse(result.isSupportMove());
        assertFalse(result.isSupportResize());
        assertFalse(result.isSupportOverlay());
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

    /**
     * 按 ID 查询窗口应返回对应窗口
     */
    @Test
    void getWindow_shouldReturnWindow() {
        SimWindow window = new SimWindow();
        window.setWindowId("win-001");
        when(repo.findWindowById("win-001")).thenReturn(window);
        assertEquals(window, manager.getWindow("win-001"));
    }
}